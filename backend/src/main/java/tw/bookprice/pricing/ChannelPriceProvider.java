package tw.bookprice.pricing;

import java.util.Optional;

/**
 * How one 通路 is asked for a price.
 *
 * The whole point of this interface is that the six 通路 differ wildly in how
 * they can be read — 示意資料 today, a real page fetch for one or two of them
 * next — and none of that may reach 取價 商業邏輯 or anything above it. Swapping
 * an implementation is meant to be the entire change.
 *
 * An implementation returns an empty Optional when the 通路 simply does not
 * carry that ISBN, and throws when the attempt itself failed. The two are not
 * the same thing: 沒有這本書 is an answer, 抓取失敗 is not, and the 取價 report
 * distinguishes them so a silent outage cannot read as an empty shelf.
 */
public interface ChannelPriceProvider {

    /** The 通路 this provider speaks for, matching Channel.code. */
    String channelCode();

    /**
     * @throws RuntimeException when the 通路 could not be reached or read; the
     *                          caller tolerates it per 通路 and carries on
     */
    Optional<FetchedPrice> fetch(String isbn);
}
