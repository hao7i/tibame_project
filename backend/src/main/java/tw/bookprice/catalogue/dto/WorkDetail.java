package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * One 作品 as the 單書比價 screen renders it: every 通路 報價 under the active
 * 載體, and which of them is 最低價.
 *
 * @param isbn      the 紙本 ISBN, whichever 版本 ISBN was asked for
 * @param bestPrice absent when no 報價 survives the 載體 filter
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkDetail(
        String isbn,
        String title,
        String author,
        String publisher,
        int publicationYear,
        String category,
        String blurb,
        int listPrice,
        int channelCount,
        String coverImageUrl,
        Instant fetchedAt,
        BestPrice bestPrice,
        List<OfferView> offers) {
}
