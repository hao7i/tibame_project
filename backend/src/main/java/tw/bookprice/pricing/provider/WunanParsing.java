package tw.bookprice.pricing.provider;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tw.bookprice.pricing.FetchedPrice;

/**
 * Reading a 五南文化廣場 商品頁.
 *
 * This 通路 publishes no structured data at all — no JSON-LD, no og:price — so
 * unlike the other three live 通路 the figures have to come out of the markup.
 * Two consequences shape everything below.
 *
 * First, the page cannot be trusted to be the book that was asked for. The
 * product URL is keyed by ISBN, which looks like a guarantee, but the page
 * simply echoes whatever ISBN the URL carried: a deliberately invented ISBN
 * comes back printed on the page as though it were the product. Worse, some
 * ISBNs return a real but unrelated title rather than a not-found page. The
 * only thing that actually distinguishes them is the 書名, so the caller has to
 * supply the title it expects and this class refuses anything else.
 *
 * That is not the same as identifying a book by its title, which this project
 * avoids elsewhere. The ISBN still selects the page; the title only confirms
 * it. And the failure is one-directional: an unrecognised title is reported as
 * 查無, never as a price. Missing a price is recoverable, printing the wrong
 * one is not.
 *
 * Second, the page carries the prices of a dozen recommended books further
 * down, so extraction is anchored to the first 優惠價 block, which is the
 * product itself.
 */
public final class WunanParsing {

    /** The product block: 優惠價 ... NN 折, NNN 元 — the discount then the price. */
    private static final Pattern SELLING_PRICE = Pattern.compile(
            "優惠價.{0,400}?([0-9]{2,5})\\s*</span>\\s*<span[^>]*>\\s*元",
            Pattern.DOTALL);

    /** Fallback for products listed without a discount. */
    private static final Pattern PLAIN_PRICE = Pattern.compile(
            "優惠價[^0-9]{0,80}([0-9]{2,5})\\s*元", Pattern.DOTALL);

    /** A page that found nothing keeps the bare store name as its title. */
    private static final String NOT_FOUND_TITLE = "五南文化廣場網路書店";

    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private WunanParsing() {
    }

    /**
     * @param expectedTitle the 書名 from our own 書目; the page must carry it
     * @return the 售價, or empty when the page is not this book
     */
    public static Optional<FetchedPrice> parse(String expectedTitle, String productUrl, String html) {
        String title = titleOf(html);
        if (title == null || title.isBlank() || title.startsWith(NOT_FOUND_TITLE)) {
            return Optional.empty();
        }

        // The 通路 shortens and decorates 書名, so containment rather than
        // equality — but only in this direction, so a shorter unrelated title
        // cannot swallow ours.
        if (!title.contains(expectedTitle)) {
            return Optional.empty();
        }

        Integer price = firstMatch(SELLING_PRICE, html);
        if (price == null) {
            price = firstMatch(PLAIN_PRICE, html);
        }
        if (price == null) {
            return Optional.empty();
        }

        return Optional.of(new FetchedPrice(price, "有貨", productUrl));
    }

    private static String titleOf(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static Integer firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        try {
            int value = Integer.parseInt(matcher.group(1));
            return value > 0 ? value : null;
        } catch (NumberFormatException cause) {
            return null;
        }
    }
}
