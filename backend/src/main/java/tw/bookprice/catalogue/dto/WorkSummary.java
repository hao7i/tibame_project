package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One 作品 as the results screen renders it.
 *
 * @param isbn           the 紙本 ISBN, which is how a 作品 is addressed in URLs
 * @param listPrice      定價 of the 紙本 版本, shown as the 定價 tag
 * @param channelCount   how many 通路 hold a 報價, shown as n 個通路有貨
 * @param bestPrice      cheapest 報價 across every 版本, absent if there are none
 * @param channelPrices  every 通路 報價, in fixed 通路 order; the caller decides how
 *                       many to show, and the table view needs all of them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkSummary(
        String isbn,
        String title,
        String author,
        String publisher,
        int publicationYear,
        String category,
        int listPrice,
        int channelCount,
        BestPrice bestPrice,
        List<ChannelPrice> channelPrices) {
}
