package tw.bookprice.pricing;

/**
 * What one 通路 reports for one ISBN at one moment.
 *
 * @param price       售價 in NT$
 * @param stockStatus 庫存／到貨, as the 通路 words it
 */
public record FetchedPrice(int price, String stockStatus) {
}
