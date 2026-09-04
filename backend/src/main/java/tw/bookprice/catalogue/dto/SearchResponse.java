package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * One page of 搜尋 results.
 *
 * @param query      the 搜尋 term, absent when the caller asked for everything
 *                   (the results screen then reads 全部收錄書籍)
 * @param total      how many 作品 matched after 篩選 and before 分頁
 * @param page       1-based page actually returned, clamped into range
 * @param pageSize   how many 作品 a page holds; fixed by the server
 * @param totalPages 0 when nothing matched
 * @param fetchedAt  newest 取價 time across the result set, for the 取價時間 stamp
 * @param works      the 作品 on this page, empty when nothing matched
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        String query,
        int total,
        int page,
        int pageSize,
        int totalPages,
        Instant fetchedAt,
        List<WorkSummary> works) {
}
