package tw.bookprice.catalogue.dto;

/**
 * The 進階搜尋 欄位條件 — two fixed rows, the second joined to the first by a 布林
 * operator, exactly as the design draws them.
 *
 * A row with a blank 關鍵字 is not a condition at all, so it drops out rather
 * than matching everything or nothing. Two blank rows therefore mean 全部收錄書籍,
 * which is what the 查詢式預覽 writes as `*`.
 *
 * @param field1 搜尋欄位 of the first row (title / author / publisher /
 *               translator / isbn / series); null or unknown reads as 書名
 * @param term1  關鍵字 of the first row; blank drops the row
 * @param op     how the second row joins the first: AND / OR / NOT, where NOT
 *               means 「第一列成立且第二列不成立」. Ignored unless both rows carry
 *               a 關鍵字, since joining one condition to nothing is meaningless.
 * @param field2 搜尋欄位 of the second row
 * @param term2  關鍵字 of the second row; blank drops the row
 */
public record FieldQuery(
        String field1,
        String term1,
        String op,
        String field2,
        String term2) {

    /** Nothing to narrow by — the caller asked for everything. */
    public boolean isEmpty() {
        return blank(term1) && blank(term2);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
