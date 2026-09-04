package tw.bookprice.watchlist.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tw.bookprice.catalogue.dto.BestPrice;

/**
 * One row of 追蹤清單.
 *
 * @param isbn        the 作品 address, for the 比價 link
 * @param bestPrice   目前最低價 across every 通路 and 版本, recomputed on every read
 *                    rather than stored — the whole point of 追蹤 is that it moves
 * @param targetPrice 目標價, absent when 未設目標價
 * @param status      how bestPrice stands against targetPrice
 * @param gap         尚差 how much; absent unless status is ABOVE_TARGET
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchItemView(
        String isbn,
        String title,
        String author,
        String publisher,
        int publicationYear,
        BestPrice bestPrice,
        Integer targetPrice,
        WatchStatus status,
        Integer gap) {
}
