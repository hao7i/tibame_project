package tw.bookprice.pricing.provider;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * Reading a 金石堂 商品頁.
 *
 * Separate from the provider so the fragile half — what the markup means — can
 * be tested against a captured page, while the provider keeps only the part
 * that talks to the network.
 */
public final class KingstoneParsing {

    private KingstoneParsing() {
    }

    /**
     * The 售價 for one ISBN, or empty when this page does not state one.
     *
     * A block that declares a different ISBN is skipped: a 金石堂 search can land
     * on a neighbouring edition, and answering with its price would report a
     * number for a book the reader did not ask about.
     */
    /** 金石堂 marks an 電子書 listing in its own breadcrumb, and only there. */
    private static final String EBOOK_BREADCRUMB = "kingstone.com.tw/ebook/";

    public static Optional<FetchedPrice> parse(String isbn, String productUrl, String html) {
        // 金石堂 publishes the same gtin13 for the 紙本 and the 電子書 listing, so
        // the ISBN cannot tell them apart — and it lists the 電子書 first. Every
        // 金石堂 報價 in this 書目 is 紙本, so an 電子書 page is the wrong answer
        // rather than a second one. If 電子書 報價 are ever modelled for this
        // 通路, the provider will need to be told which 載體 it is pricing.
        if (html.contains(EBOOK_BREADCRUMB)) {
            return Optional.empty();
        }

        for (JsonNode block : JsonLdOffers.blocks(html)) {
            Integer price = JsonLdOffers.priceOf(block);
            if (price == null) {
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
