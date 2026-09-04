package tw.bookprice.pricing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads schema.org JSON-LD out of a 通路 product page.
 *
 * Both fetched 通路 publish the same machine-readable block that search engines
 * read, carrying ISBN, price, currency and availability. Parsing that rather
 * than the visible markup is the whole reason these two were chosen: it is
 * intended to be read by machines, and it does not move when the site restyles.
 *
 * A naive price regex would be actively wrong on both sites — each product page
 * also carries 定價 and a grid of other books with their own prices.
 */
public final class JsonLdOffers {

    private static final Pattern BLOCK = Pattern.compile(
            "<script[^>]*application/ld[+]json[^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** property before content, which is what all five 通路 emit today. */
    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /** The same tag written the other way round, which is equally valid HTML. */
    private static final Pattern OG_IMAGE_REVERSED = Pattern.compile(
            "<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonLdOffers() {
    }

    /**
     * Every JSON-LD object on the page, flattened.
     *
     * A page may carry several blocks and a block may be an array — TAAZE emits
     * three, one of which is a breadcrumb list — so callers get everything and
     * pick what they need.
     */
    public static List<JsonNode> blocks(String html) {
        List<JsonNode> nodes = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(html);

        while (matcher.find()) {
            try {
                JsonNode parsed = MAPPER.readTree(matcher.group(1).trim());
                if (parsed.isArray()) {
                    parsed.forEach(nodes::add);
                } else {
                    nodes.add(parsed);
                }
            } catch (Exception ignored) {
                // One malformed block must not cost us the others. A page with
                // no usable block at all surfaces as 查無 further up, which is
                // the honest answer when we cannot read a price.
            }
        }
        return nodes;
    }

    /**
     * The ISBN a block declares, from whichever field that 通路 uses.
     *
     * Order matters. sku is last because it is only sometimes the ISBN: 墊腳石
     * publishes nothing else, but 金石堂 puts its own internal product number
     * there while giving the ISBN its own field. Reading sku first would make
     * that shop appear to declare an ISBN it never claimed.
     */
    public static String isbnOf(JsonNode node) {
        for (String field : new String[] {"isbn", "gtin13", "mpn", "sku"}) {
            JsonNode value = node.get(field);
            if (value != null && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    /**
     * 書封 URL declared by the block, or null.
     *
     * schema.org allows either a single string or an array of them; 金石堂 and
     * 墊腳石 use the array form, 三民 the string. The first entry is taken —
     * where a shop lists several, they are sizes of the same cover.
     */
    public static String imageOf(JsonNode node) {
        JsonNode value = node.get("image");
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            value = value.isEmpty() ? null : value.get(0);
        }
        if (value == null || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString().trim();
    }

    /**
     * 書封 for a product page: whatever the matched block declares, else the
     * page-level og:image.
     *
     * The fallback is what makes one call work for all five 通路 — 金石堂, 三民 and
     * 墊腳石 put it in the JSON-LD, 五南 and 讀冊生活 only in the meta tag — instead
     * of each parser knowing which kind of shop it is reading.
     */
    public static String coverOf(JsonNode node, String html) {
        String declared = imageOf(node);
        return declared != null ? declared : ogImage(html);
    }

    /**
     * 書封 from the og:image meta tag, for the 通路 whose JSON-LD carries none.
     *
     * 五南 and 讀冊生活 publish a cover this way and only this way. Both attribute
     * orders appear in the wild, so property and content are matched in either
     * position rather than assuming one layout.
     */
    public static String ogImage(String html) {
        Matcher matcher = OG_IMAGE.matcher(html);
        if (matcher.find()) {
            String url = matcher.group(1).trim();
            return url.isBlank() ? null : url;
        }
        matcher = OG_IMAGE_REVERSED.matcher(html);
        if (matcher.find()) {
            String url = matcher.group(1).trim();
            return url.isBlank() ? null : url;
        }
        return null;
    }

    /**
     * 售價 in whole NT$, or null when the block carries no usable offer.
     *
     * TAAZE writes 260.0 and Kingstone writes 261, so the value is parsed as a
     * decimal and rounded rather than assumed to be an integer string.
     */
    public static Integer priceOf(JsonNode node) {
        JsonNode offers = node.get("offers");
        if (offers == null) {
            return null;
        }
        JsonNode offer = offers.isArray() ? (offers.isEmpty() ? null : offers.get(0)) : offers;
        if (offer == null) {
            return null;
        }

        JsonNode price = offer.get("price");
        if (price == null || price.asText().isBlank()) {
            return null;
        }

        try {
            int rounded = (int) Math.round(Double.parseDouble(price.asText().trim()));
            return rounded > 0 ? rounded : null;
        } catch (NumberFormatException cause) {
            return null;
        }
    }

    /** 庫存 as schema.org states it, reduced to the words the table shows. */
    public static String availabilityOf(JsonNode node) {
        JsonNode offers = node.get("offers");
        if (offers == null) {
            return "有貨";
        }
        JsonNode offer = offers.isArray() ? (offers.isEmpty() ? null : offers.get(0)) : offers;
        JsonNode availability = (offer == null) ? null : offer.get("availability");

        if (availability == null) {
            return "有貨";
        }
        String value = availability.asText().toLowerCase();
        if (value.contains("outofstock")) {
            return "缺貨";
        }
        if (value.contains("preorder")) {
            return "可預購";
        }
        return "有貨";
    }

    /** True when the block describes a used copy, which TAAZE lists alongside new. */
    public static boolean isUsed(JsonNode node) {
        JsonNode offers = node.get("offers");
        JsonNode offer = (offers != null && offers.isArray() && !offers.isEmpty())
                ? offers.get(0)
                : offers;

        for (JsonNode candidate : new JsonNode[] {node.get("itemCondition"),
                (offer == null) ? null : offer.get("itemCondition")}) {
            if (candidate != null && candidate.asText().toLowerCase().contains("used")) {
                return true;
            }
        }
        return false;
    }
}
