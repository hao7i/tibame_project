package tw.bookprice.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 價格每 6 小時更新一次 — the design's promise, kept by a clock rather than by
 * whoever happens to click something.
 *
 * Before this, the only things that refreshed a stale 報價 were the 管理後台
 * button and 找書. Hanging it off 找書 meant a reader asking for one book paid
 * for the whole 書目, and it also meant that if nobody searched, nothing was ever
 * updated. Neither is the right owner for a promise about elapsed time.
 *
 * It runs more often than the 6 小時 window and re-checks rather than assuming:
 * PriceRefreshService skips anything still inside the window, so a frequent tick
 * costs nothing except when something has actually expired. That keeps the
 * staleness bounded near 6 小時 instead of near 12, which is what a tick exactly
 * as long as the window would give on the wrong phase.
 *
 * Off by default in tests, where a background thread reaching for the network is
 * never what a test meant to exercise.
 */
@Component
@ConditionalOnProperty(name = "bookprice.pricing.schedule.enabled",
        havingValue = "true", matchIfMissing = true)
public class PriceRefreshSchedule {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshSchedule.class);

    private final PriceRefreshService priceRefreshService;
    private final long freshnessHours;

    public PriceRefreshSchedule(PriceRefreshService priceRefreshService,
            @Value("${bookprice.pricing.freshness-hours:6}") long freshnessHours) {
        this.priceRefreshService = priceRefreshService;
        this.freshnessHours = freshnessHours;
    }

    /**
     * Fixed delay, not a fixed rate: the next tick is counted from the end of
     * the last one, so a long run can never have the next one starting on top of
     * it and doubling the traffic at somebody else's site.
     *
     * The first tick waits too, so a restart does not fire a burst of requests
     * while the application is still settling.
     */
    @Scheduled(fixedDelayString = "${bookprice.pricing.schedule.interval-ms:3600000}",
            initialDelayString = "${bookprice.pricing.schedule.initial-delay-ms:120000}")
    public void refreshStale() {
        try {
            RefreshReport report = priceRefreshService.refreshAll(false);

            int updated = report.channels().stream()
                    .mapToInt(RefreshReport.ChannelResult::updated).sum();
            int failed = report.channels().stream()
                    .mapToInt(RefreshReport.ChannelResult::failed).sum();

            if (updated > 0 || failed > 0) {
                log.info("排程取價：更新 {} 筆、失敗 {} 筆（新鮮窗 {} 小時）",
                        updated, failed, freshnessHours);
            }
        } catch (RuntimeException cause) {
            // Swallowed on purpose: an exception out of a scheduled method
            // cancels the schedule in Spring, so one bad night would silently
            // end all future refreshes.
            log.error("排程取價失敗，下一輪會再試", cause);
        }
    }
}
