package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * One 搜尋 result set.
 *
 * @param query      the 搜尋 term, absent when the caller asked for everything
 *                   (the results screen then reads 全部收錄書籍)
 * @param total      how many 作品 matched
 * @param fetchedAt  newest 取價 time across the result set, for the 取價時間 stamp
 * @param works      the matched 作品, empty when nothing matched
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        String query,
        int total,
        Instant fetchedAt,
        List<WorkSummary> works) {
}
