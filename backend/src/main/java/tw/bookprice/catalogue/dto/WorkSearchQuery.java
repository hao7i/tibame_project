package tw.bookprice.catalogue.dto;

import java.util.List;

/**
 * Everything the 搜尋結果 screen can ask for, in one object rather than six
 * positional arguments.
 *
 * @param query      搜尋 term, or null/blank for 全部收錄書籍
 * @param format     載體 to narrow to, or null/blank for 全部版本
 * @param channels   通路 codes; OR within this group, AND against the others.
 *                   Narrows the 報價 that 最低價 is computed from, so it changes
 *                   prices rather than only hiding rows.
 * @param categories 分類 names; OR within this group
 * @param maxPrice   價格上限, applied to the computed 最低價; null for no ceiling
 * @param page       1-based page number; null for the first page
 */
public record WorkSearchQuery(
        String query,
        String format,
        List<String> channels,
        List<String> categories,
        Integer maxPrice,
        Integer page) {
}
