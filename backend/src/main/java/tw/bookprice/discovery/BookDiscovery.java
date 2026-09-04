package tw.bookprice.discovery;

import java.util.List;

/**
 * How one 通路 is asked "which books match this 書名".
 *
 * Deliberately separate from ChannelPriceProvider. Pricing asks a 通路 about an
 * ISBN we already hold; discovery asks it to tell us an ISBN we do not. Only
 * some 通路 can answer the second question — 墊腳石 renders its 搜尋 in the
 * browser, so its HTML carries no results at all — and that is fine: a 通路 that
 * cannot be searched is still priced, because pricing goes by ISBN.
 */
public interface BookDiscovery {

    /** The 通路 this speaks for, matching Channel.code. */
    String channelCode();

    /**
     * @param title 書名 the reader typed
     * @param limit at most this many books, so one broad 書名 cannot turn into
     *              dozens of page fetches
     * @throws RuntimeException when the 通路 could not be reached or read
     */
    List<DiscoveredBook> byTitle(String title, int limit);
}
