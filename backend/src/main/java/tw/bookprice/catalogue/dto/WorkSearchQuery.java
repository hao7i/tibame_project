package tw.bookprice.catalogue.dto;

import java.util.List;

/**
 * Everything the 搜尋結果 screen can ask for, in one object rather than a row of
 * positional arguments.
 *
 * 簡易搜尋 fills in {@code query}; 進階搜尋 fills in {@code fields}, {@code minPrice}
 * and {@code year} instead. Nothing stops both arriving together — they simply
 * narrow in series, since every condition here is ANDed against the others.
 *
 * @param query      搜尋 term, or null/blank for 全部收錄書籍
 * @param format     載體 to narrow to, or null/blank for 全部版本
 * @param channels   通路 codes; OR within this group, AND against the others.
 *                   Narrows the 報價 that 最低價 is computed from, so it changes
 *                   prices rather than only hiding rows.
 * @param categories 分類 names; OR within this group
 * @param minPrice   價格下限, applied to the computed 最低價; null for no floor
 * @param maxPrice   價格上限, applied to the computed 最低價; null for no ceiling
 * @param year       出版年: "2025" for that year exactly, "2024-" for 2024 或更早,
 *                   null for 不限
 * @param fields     進階搜尋 欄位條件; null when the caller is not using them
 * @param page       1-based page number; null for the first page
 */
public record WorkSearchQuery(
        String query,
        String format,
        List<String> channels,
        List<String> categories,
        Integer minPrice,
        Integer maxPrice,
        String year,
        FieldQuery fields,
        Integer page) {
}
