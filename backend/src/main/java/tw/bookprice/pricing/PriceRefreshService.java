package tw.bookprice.pricing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.Channel;
import tw.bookprice.catalogue.ChannelRepository;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Offer;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;

/**
 * 取價.
 *
 * One 通路 failing must never cost the others their results, so every provider
 * call is caught individually and recorded. That is the rule that matters on a
 * bad day: a shop that changed its markup, rate-limited us or simply went down
 * has to degrade into one line of the report, not into an empty 書目.
 *
 * Two entry points, because the two callers want different things. 找書 asks for
 * one book and should pay for one book ({@link #refreshIsbns}); the 管理後台 and
 * the scheduler ask for everything that has gone stale ({@link #refreshAll}).
 * Mixing them made a 找書 slower every time the 書目 grew — the pause between
 * requests to one host meant the reader waited in proportion to how many other
 * 報價 happened to be stale, which has nothing to do with what they asked for.
 */
@Service
public class PriceRefreshService {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshService.class);

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;
    private final List<ChannelPriceProvider> providers;
    private final Duration freshness;

    public PriceRefreshService(WorkRepository workRepository,
            ChannelRepository channelRepository,
            List<ChannelPriceProvider> providers,
            @Value("${bookprice.pricing.freshness-hours:6}") long freshnessHours) {
        this.freshness = Duration.ofHours(freshnessHours);
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
        this.providers = List.copyOf(providers);
    }

    /**
     * Re-asks every 通路 for the 報價 that are due, and writes what comes back.
     *
     * 報價 fetched within the freshness window are skipped. The design promises
     * 「價格每 6 小時更新一次」, and honouring that is also what keeps this from
     * asking a shop for the same price on every click. The window lives in the
     * database rather than in a cache, so it survives a restart — a cache that
     * empties on deploy would turn every deploy into a burst of traffic at
     * somebody else's site.
     *
     * @param force ignore the window, for an operator who needs an answer now
     */
    @Transactional
    public RefreshReport refreshAll(boolean force) {
        return refresh(workRepository.findAllWithOffers(), force);
    }

    /**
     * Re-asks the 通路 about specific books only.
     *
     * This is what 找書 calls once it has imported something: the reader is
     * waiting for those books and nothing else, and the rest of the 書目 has its
     * own schedule. Ignores the freshness window, since a book that was just
     * imported has no price yet and one that was asked for by name is being
     * asked about now.
     *
     * @param isbns 版本 ISBNs; anything not in the 書目 is silently ignored
     */
    @Transactional
    public RefreshReport refreshIsbns(Collection<String> isbns) {
        if (isbns == null || isbns.isEmpty()) {
            return refresh(List.of(), true);
        }
        Set<String> wanted = Set.copyOf(isbns);
        List<Work> works = workRepository.findAllWithOffers().stream()
                .filter(work -> work.getEditions().stream()
                        .anyMatch(edition -> wanted.contains(edition.getIsbn())))
                .toList();
        return refresh(works, true);
    }

    /**
     * Everything that has to be asked, asked, then written.
     *
     * The three steps are separate on purpose. Deciding and writing both touch
     * JPA entities and so must stay on this one transactional thread; only the
     * middle step goes over the network, and that is the step worth running in
     * parallel. Doing it the obvious way — a parallel stream over the whole loop
     * — would hand the persistence context to several threads at once, which it
     * is not built for.
     */
    private RefreshReport refresh(List<Work> works, boolean force) {
        Instant startedAt = Instant.now();

        Map<String, Channel> channels = new java.util.LinkedHashMap<>();
        for (Channel channel : channelRepository.findAllByOrderByDisplayOrderAsc()) {
            channels.put(channel.getCode(), channel);
        }

        Map<String, Tally> tallies = new HashMap<>();
        for (String code : channels.keySet()) {
            tallies.put(code, new Tally());
        }

        List<Pending> pending = new ArrayList<>();

        for (Work work : works) {
            for (Edition edition : work.getEditions()) {
                for (Offer offer : edition.getOffers()) {
                    String code = offer.getChannel().getCode();
                    Tally tally = tallies.get(code);
                    if (tally == null) {
                        continue;
                    }
                    if (!force && isFresh(offer, startedAt)) {
                        tally.skipped++;
                        continue;
                    }
                    // The ISBN is read here, on this thread: the fetch itself
                    // must not touch a lazily-loaded entity from another one.
                    pending.add(new Pending(code, edition, offer, edition.getIsbn()));
                }
            }
        }

        for (Fetched fetched : fetchInParallel(pending)) {
            apply(fetched, tallies.get(fetched.pending().channelCode()), startedAt);
        }

        List<RefreshReport.ChannelResult> results = new ArrayList<>();
        for (Channel channel : channels.values()) {
            Tally tally = tallies.get(channel.getCode());
            String note = providerFor(channel.getCode()) == null
                    ? "沒有對應的取價實作"
                    : tally.note;
            results.add(new RefreshReport.ChannelResult(
                    channel.getCode(), channel.getName(),
                    tally.updated, tally.notFound, tally.failed, tally.skipped, note));
        }

        return new RefreshReport(startedAt, results);
    }

    /**
     * The network step, run across 通路 at once.
     *
     * Safe to parallelise because the throttle in HtmlFetcher is per host: two
     * requests to the same shop still queue behind each other exactly as before,
     * while two different shops no longer wait for one another. Virtual threads
     * because every task here is a blocked socket, not work for a core.
     */
    private List<Fetched> fetchInParallel(List<Pending> pending) {
        if (pending.isEmpty()) {
            return List.of();
        }

        List<Fetched> results = new ArrayList<>(pending.size());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Fetched>> futures = pending.stream()
                    .map(item -> pool.submit(() -> fetchOne(item)))
                    .toList();

            for (Future<Fetched> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("取價被中斷", cause);
                } catch (java.util.concurrent.ExecutionException cause) {
                    // fetchOne catches its own failures, so reaching here means
                    // something outside the provider broke; treat it as one
                    // failed 報價 rather than losing the whole run.
                    throw new IllegalStateException("取價任務失敗", cause.getCause());
                }
            }
        }
        return results;
    }

    private Fetched fetchOne(Pending item) {
        ChannelPriceProvider provider = providerFor(item.channelCode());
        if (provider == null) {
            return new Fetched(item, Optional.empty(), null, true);
        }
        try {
            return new Fetched(item, provider.fetch(item.isbn()), null, false);
        } catch (RuntimeException cause) {
            return new Fetched(item, Optional.empty(), cause, false);
        }
    }

    /** Writing back, on the transactional thread and nowhere else. */
    private void apply(Fetched fetched, Tally tally, Instant startedAt) {
        if (tally == null || fetched.noProvider()) {
            return;
        }

        Offer offer = fetched.pending().offer();

        if (fetched.failure() != null) {
            // Marked on the 報價 itself so 單書比價 can show this one 通路 as
            // 取價失敗 while the others stay current.
            offer.recordFetchFailure(startedAt);
            tally.failed++;
            if (tally.note == null) {
                tally.note = fetched.failure().getMessage();
            }
            log.warn("取價失敗: {} / {}", fetched.pending().channelCode(),
                    fetched.pending().isbn(), fetched.failure());
            return;
        }

        if (fetched.price().isEmpty()) {
            // A clear answer, not a failure: the 通路 does not carry this book.
            // Marking it stops the row being shown with whatever price it was
            // seeded with, which after a 通路 replacement belongs to the shop
            // that used to occupy that column.
            offer.markUnavailable();
            tally.notFound++;
            return;
        }

        FetchedPrice price = fetched.price().get();
        offer.recordFetch(price.price(), price.stockStatus(), startedAt, price.productUrl());
        // 書封 belongs to the 版本, not to this one 通路 row: the screens show one
        // cover per book, whichever shop it came from.
        fetched.pending().edition().recordCoverImage(price.coverImageUrl());
        tally.updated++;
    }

    /**
     * Resolved per run rather than cached into a map at construction: that made
     * the service depend on every provider being fully built before this one
     * was, which is a coupling with nothing to gain.
     */
    private ChannelPriceProvider providerFor(String channelCode) {
        return providers.stream()
                .filter(candidate -> channelCode.equals(candidate.channelCode()))
                .findFirst()
                .orElse(null);
    }

    /** Within the freshness window, so there is nothing to ask about yet. */
    private boolean isFresh(Offer offer, Instant now) {
        return !offer.isFetchFailed()
                && offer.getFetchedAt() != null
                && offer.getFetchedAt().isAfter(now.minus(freshness));
    }

    /** One 報價 that has to be asked about, with its ISBN already read. */
    private record Pending(String channelCode, Edition edition, Offer offer, String isbn) {
    }

    /** What came back, or why nothing did. */
    private record Fetched(Pending pending, Optional<FetchedPrice> price,
            RuntimeException failure, boolean noProvider) {
    }

    /** Running counts for one 通路. */
    private static final class Tally {
        private int updated;
        private int notFound;
        private int failed;
        private int skipped;
        private String note;
    }
}
