package tw.bookprice.pricing.provider;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * Reading a 讀冊生活 商品頁.
 *
 * TAAZE is Taiwan main used-book channel and lists a used copy for the same
 * ISBN as the new one, often at a third of the price. Only the 全新 listing is
 * taken: a comparison column that quietly mixed in a used price would advertise
 * a bargain the reader cannot buy new.
 *
 * 絕版 titles publish 定價 and no 優惠價, and are answered as no offer rather than
 * by passing the list price off as a selling price.
 */
public final class TaazeParsing {

    private TaazeParsing() {
    }

    public static Optional<FetchedPrice> readNewCopy(String isbn, String productUrl, String html) {
        for (JsonNode block : JsonLdOffers.blocks(html)) {
            Integer price = JsonLdOffers.priceOf(block);
            if (price == null || JsonLdOffers.isUsed(block)) {
                continue;
            }

            // The ISBN must be declared and must match. A page that states no
            // ISBN is not evidence about the book we asked for: 金石堂 returns
            // its 電子書 listing first for a 紙本 ISBN, and that page omits the
            // field entirely — accepting it once put an ebook price on a
            // paperback row.
            String declared = JsonLdOffers.isbnOf(block);
            if (declared == null || !declared.equals(isbn)) {
                continue;
            }

            return Optional.of(new FetchedPrice(
                    price, JsonLdOffers.availabilityOf(block), productUrl,
                    JsonLdOffers.coverOf(block, html)));
        }
        return Optional.empty();
    }
}
