package tw.bookprice.pricing.provider;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * Reading a 墊腳石 商品頁.
 *
 * The page publishes schema.org JSON-LD whose sku is the ISBN, so unlike 五南 —
 * where the only ISBN on the page is the one we put in the URL — this 通路 can
 * be held to what it claims. The ISBN must be declared and must match.
 *
 * Its 搜尋 page is a client-rendered shell with no product data in the HTML, and
 * its sitemap does not list every book it stocks. Neither is used: the ISBN is
 * the product URL, and that is the only route taken.
 */
public final class TcsbParsing {

    private TcsbParsing() {
    }

    public static Optional<FetchedPrice> parse(String isbn, String productUrl, String html) {
        for (JsonNode block : JsonLdOffers.blocks(html)) {
            Integer price = JsonLdOffers.priceOf(block);
            if (price == null) {
                continue;
            }

            String declared = JsonLdOffers.isbnOf(block);
            if (declared == null || !declared.equals(isbn)) {
                continue;
            }

            return Optional.of(new FetchedPrice(
                    price, JsonLdOffers.availabilityOf(block), productUrl));
        }
        return Optional.empty();
    }
}
