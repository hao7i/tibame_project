package tw.bookprice.pricing;

/**
 * What one 通路 reports for one ISBN at one moment.
 *
 * @param price       售價 in NT$
 * @param stockStatus 庫存／到貨, as the 通路 words it
 * @param productUrl  the 商品頁 this price was read from, so 前往購買 goes to the
 *                    page that actually states it rather than to a search that
 *                    may since have moved. Null for a 示意 provider, which has
 *                    no page behind it to point at.
 */
public record FetchedPrice(int price, String stockStatus, String productUrl) {

    /** A 示意 answer: a price with no page behind it. */
    public static FetchedPrice seeded(int price, String stockStatus) {
        return new FetchedPrice(price, stockStatus, null);
    }
}
