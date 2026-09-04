package tw.bookprice.pricing;

import java.time.Instant;
import java.util.List;

/**
 * What one 手動取價 actually did, per 通路.
 *
 * The 管理後台 shows this verbatim. A run that quietly did nothing and a run that
 * failed everywhere must not look alike from the outside, which is why 更新,
 * 查無 and 失敗 are three separate counts rather than one success total.
 *
 * @param startedAt when the run began
 * @param channels  one line per 通路, in the fixed presentation order
 */
public record RefreshReport(Instant startedAt, List<ChannelResult> channels) {

    public int totalUpdated() {
        return channels.stream().mapToInt(ChannelResult::updated).sum();
    }

    public int totalFailed() {
        return channels.stream().mapToInt(ChannelResult::failed).sum();
    }

    /**
     * @param updated  報價 whose 售價 and 取價時間 were written
     * @param notFound ISBNs the 通路 does not carry — an answer, not a fault
     * @param failed   attempts that threw; the 通路 could not be read
     * @param note     the first failure message, for the operator to act on
     */
    public record ChannelResult(
            String channelCode,
            String channelName,
            int updated,
            int notFound,
            int failed,
            String note) {
    }
}
