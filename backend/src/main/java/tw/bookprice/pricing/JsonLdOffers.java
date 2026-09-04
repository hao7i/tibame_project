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

    /** The ISBN a block declares, from isbn, gtin13 or mpn, whichever it uses. */
    public static String isbnOf(JsonNode node) {
        for (String field : new String[] {"isbn", "gtin13", "mpn"}) {
            JsonNode value = node.get(field);
            if (value != null && !value.asText().isBlank()) {
                return value.asText().trim();
            }
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
