package tw.bookprice.pricing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.Channel;
import tw.bookprice.catalogue.ChannelRepository;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Offer;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;

/**
 * 手動觸發一次取價.
 *
 * One 通路 failing must never cost the others their results, so every provider
 * call is caught individually and recorded. This is the rule that matters once
 * 票 10 puts a real website behind one of these providers: a shop that changed
 * its markup, rate-limited us, or simply went down has to degrade into one line
 * of the report, not into an empty 書目.
 *
 * There is no scheduler here. The design says 價格每 6 小時更新一次 and 票 10 owns
 * the caching that delivers it; this class is the manual trigger the 管理後台
 * needs, and nothing calls it on a timer yet.
 */
@Service
public class PriceRefreshService {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshService.class);

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;
    private final List<ChannelPriceProvider> providers;
    private final java.time.Duration freshness;

    public PriceRefreshService(WorkRepository workRepository,
            ChannelRepository channelRepository,
            List<ChannelPriceProvider> providers,
            @org.springframework.beans.factory.annotation.Value(
                    "${bookprice.pricing.freshness-hours:6}") long freshnessHours) {
        this.freshness = java.time.Duration.ofHours(freshnessHours);
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
     * somebody else site.
     *
     * @param force ignore the window, for an operator who needs an answer now
     */
    @Transactional
    public RefreshReport refreshAll(boolean force) {
        Instant startedAt = Instant.now();
        List<Work> works = workRepository.findAllWithOffers();

        List<RefreshReport.ChannelResult> results = new ArrayList<>();

        for (Channel channel : channelRepository.findAllByOrderByDisplayOrderAsc()) {
            // Resolved per run rather than cached into a map at construction:
            // that made the service depend on every provider being fully built
            // before this one was, which is a coupling with nothing to gain.
            ChannelPriceProvider provider = providers.stream()
                    .filter(candidate -> channel.getCode().equals(candidate.channelCode()))
                    .findFirst()
                    .orElse(null);

            if (provider == null) {
                // A 通路 in the 書目 with nobody to ask is a configuration gap, and
                // saying so beats reporting a clean run that touched nothing.
                results.add(new RefreshReport.ChannelResult(
                        channel.getCode(), channel.getName(), 0, 0, 0, 0,
                        "沒有對應的取價實作"));
                continue;
            }

            results.add(refreshChannel(channel, provider, works, startedAt, force));
        }

        return new RefreshReport(startedAt, results);
    }

    /** Within the freshness window, so there is nothing to ask about yet. */
    private boolean isFresh(Offer offer, Instant now) {
        return !offer.isFetchFailed()
                && offer.getFetchedAt() != null
                && offer.getFetchedAt().isAfter(now.minus(freshness));
    }

    private RefreshReport.ChannelResult refreshChannel(Channel channel,
            ChannelPriceProvider provider, List<Work> works, Instant startedAt,
            boolean force) {

        int updated = 0;
        int notFound = 0;
        int failed = 0;
        int skipped = 0;
        String note = null;

        for (Work work : works) {
            for (Edition edition : work.getEditions()) {
                for (Offer offer : edition.getOffers()) {
                    if (!offer.getChannel().getCode().equals(channel.getCode())) {
                        continue;
                    }

                    if (!force && isFresh(offer, startedAt)) {
                        skipped++;
                        continue;
                    }

                    try {
                        Optional<FetchedPrice> fetched = provider.fetch(edition.getIsbn());
                        if (fetched.isEmpty()) {
                            // A clear answer, not a failure: the 通路 does not
                            // carry this book. Marking it stops the row being
                            // shown with whatever price it was seeded with,
                            // which after a 通路 replacement belongs to the shop
                            // that used to occupy that column.
                            offer.markUnavailable();
                            notFound++;
                            continue;
                        }
                        offer.recordFetch(fetched.get().price(), fetched.get().stockStatus(),
                                startedAt, fetched.get().productUrl());
                        updated++;
                    } catch (RuntimeException cause) {
                        // Marked on the 報價 itself so 單書比價 can show this one
                        // 通路 as 取價失敗 while the other five stay current.
                        offer.recordFetchFailure(startedAt);
                        failed++;
                        if (note == null) {
                            note = cause.getMessage();
                        }
                        // Logged, not rethrown: the remaining 通路 still have to run.
                        log.warn("取價失敗: {} / {}", channel.getCode(), edition.getIsbn(), cause);
                    }
                }
            }
        }

        return new RefreshReport.ChannelResult(
                channel.getCode(), channel.getName(), updated, notFound, failed, skipped, note);
    }
}
