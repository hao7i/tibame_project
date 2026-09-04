package tw.bookprice.discovery;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * Reading 書目 out of a 金石堂 商品頁.
 *
 * The JSON-LD Product block gives 書名, ISBN (as gtin13) and 出版社 (as brand)
 * outright. 作者 and 出版年 are not their own fields, but the description is
 * written to a fixed template that carries both:
 *
 *   {書名} | 作者: {作者} | {出版社} {yyyy/MM/dd}出版 | 類別: … | ISBN: … | 語言: …
 *
 * Parsing that is more fragile than reading a field, so both are optional: a
 * book whose 作者 or 出版年 cannot be read is still worth importing, because the
 * ISBN — the only part everything downstream depends on — came from a real
 * field, not from this template.
 */
public final class KingstoneDiscoveryParsing {

    /** 作者: up to the next pipe. */
    private static final Pattern AUTHOR = Pattern.compile("作者:\s*([^|]+)");

    /** The four digits before a yyyy/MM/dd 出版 stamp. */
    private static final Pattern YEAR = Pattern.compile("([0-9]{4})/[0-9]{1,2}/[0-9]{1,2}\s*出版");

    private KingstoneDiscoveryParsing() {
    }

    public static Optional<DiscoveredBook> parse(String html) {
        for (JsonNode block : JsonLdOffers.blocks(html)) {
            String isbn = JsonLdOffers.isbnOf(block);
            String title = textOf(block, "name");

            // A real ISBN or nothing. 金石堂 puts its own product number in sku
            // and it is thirteen digits too, so checking the shape alone lets it
            // through — which is how a 作品 would end up keyed on a number the
            // other four 通路 have never heard of.
            if (!isRealIsbn13(isbn) || title == null) {
                continue;
            }

            String description = textOf(block, "description");
            return Optional.of(new DiscoveredBook(
                    isbn,
                    title.trim(),
                    firstGroup(AUTHOR, description),
                    brandOf(block),
                    yearOf(description)));
        }
        return Optional.empty();
    }

    private static String brandOf(JsonNode block) {
        JsonNode brand = block.get("brand");
        return brand == null ? null : textOf(brand, "name");
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }

    private static String firstGroup(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * A Bookland ISBN-13 with a correct check digit.
     *
     * Both halves earn their place. The 978/979 prefix rejects 金石堂 own product
     * numbers, which are thirteen digits as well but start 20; the check digit
     * rejects one that was truncated or mistyped. Neither proves the ISBN names
     * the book this page is about — only that it is an ISBN at all.
     */
    private static boolean isRealIsbn13(String isbn) {
        if (isbn == null || !isbn.matches("97[89][0-9]{10}")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10 == isbn.charAt(12) - '0';
    }

    /** 0 rather than an exception: an unknown 出版年 is not a reason to skip a book. */
    private static int yearOf(String description) {
        String year = firstGroup(YEAR, description);
        return year == null ? 0 : Integer.parseInt(year);
    }
}
