package tw.bookprice.pricing.provider;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * Reading a 三民網路書店 商品頁.
 *
 * The page carries schema.org JSON-LD with isbn, price and availability, the
 * same machine-intended block the other two live 通路 publish. As everywhere
 * else, the ISBN must be declared and must match: a price that cannot be tied
 * to the book we asked about is not an answer about that book.
 */
public final class SanminParsing {

    private SanminParsing() {
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
