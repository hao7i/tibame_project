package tw.bookprice.pricing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final Map<String, ChannelPriceProvider> providersByCode;

    public PriceRefreshService(WorkRepository workRepository,
            ChannelRepository channelRepository,
            List<ChannelPriceProvider> providers) {
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
        this.providersByCode = providers.stream()
                .collect(Collectors.toMap(ChannelPriceProvider::channelCode,
                        Function.identity()));
    }

    /** Re-asks every 通路 for every 報價 it holds, and writes what comes back. */
    @Transactional
    public RefreshReport refreshAll() {
        Instant startedAt = Instant.now();
        List<Work> works = workRepository.findAllWithOffers();

        List<RefreshReport.ChannelResult> results = new ArrayList<>();

        for (Channel channel : channelRepository.findAllByOrderByDisplayOrderAsc()) {
            ChannelPriceProvider provider = providersByCode.get(channel.getCode());

            if (provider == null) {
                // A 通路 in the 書目 with nobody to ask is a configuration gap, and
                // saying so beats reporting a clean run that touched nothing.
                results.add(new RefreshReport.ChannelResult(
                        channel.getCode(), channel.getName(), 0, 0, 0,
                        "沒有對應的取價實作"));
                continue;
            }

            results.add(refreshChannel(channel, provider, works, startedAt));
        }

        return new RefreshReport(startedAt, results);
    }

    private RefreshReport.ChannelResult refreshChannel(Channel channel,
            ChannelPriceProvider provider, List<Work> works, Instant startedAt) {

        int updated = 0;
        int notFound = 0;
        int failed = 0;
        String note = null;

        for (Work work : works) {
            for (Edition edition : work.getEditions()) {
                for (Offer offer : edition.getOffers()) {
                    if (!offer.getChannel().getCode().equals(channel.getCode())) {
                        continue;
                    }

                    try {
                        Optional<FetchedPrice> fetched = provider.fetch(edition.getIsbn());
                        if (fetched.isEmpty()) {
                            notFound++;
                            continue;
                        }
                        offer.recordFetch(
                                fetched.get().price(), fetched.get().stockStatus(), startedAt);
                        updated++;
                    } catch (RuntimeException cause) {
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
                channel.getCode(), channel.getName(), updated, notFound, failed, note);
    }
}
