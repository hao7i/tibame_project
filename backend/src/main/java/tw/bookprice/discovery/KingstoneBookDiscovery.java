package tw.bookprice.discovery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bookprice.pricing.HtmlFetcher;

/**
 * 金石堂 — 依書名找書.
 *
 * 金石堂 is used for discovery for the same reasons it is used for 取價: its
 * robots.txt allows this crawler, its 搜尋 results are server-rendered, and its
 * 商品頁 publish JSON-LD carrying a real ISBN field rather than only the shop's
 * own product number.
 *
 * One search costs one results page plus one 商品頁 per candidate, and the
 * fetcher spaces requests per host, so the candidate count is capped rather
 * than driven by how broad the reader's 書名 happens to be.
 */
@Component
public class KingstoneBookDiscovery implements BookDiscovery {

    private static final Logger log = LoggerFactory.getLogger(KingstoneBookDiscovery.class);

    private static final String SEARCH = "https://www.kingstone.com.tw/search/key/";
    private static final String PRODUCT = "https://www.kingstone.com.tw";

    private final HtmlFetcher fetcher;
    private final boolean live;

    public KingstoneBookDiscovery(HtmlFetcher fetcher,
            @Value("${bookprice.discovery.kingstone.live:true}") boolean live) {
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "KINGSTONE";
    }

    /**
     * Product links paired with the 書名 shown beside them, in page order.
     *
     * The pairing is what makes the promotional carousel harmless: those entries
     * are indistinguishable from results by position, but not by what they are
     * called. Only listings whose 書名 relates to the 書名 searched for are worth
     * a page fetch.
     *
     * The 書名 sits in the alt of the cover image a little after the link, so the
     * gap is bounded rather than matched greedily — an unbounded match would
     * pair a link with the alt of some entirely different listing further down.
     */
    private static final Pattern LISTING = Pattern.compile(
            "/basic/([0-9]{10,})/.{0,400}?alt=\"([^\"]{1,120})\"", Pattern.DOTALL);

    @Override
    public List<DiscoveredBook> byTitle(String title, int limit) {
        if (!live || title == null || title.isBlank() || limit <= 0) {
            return List.of();
        }

        String results = fetcher.get(
                SEARCH + URLEncoder.encode(title.trim(), StandardCharsets.UTF_8));

        List<String> candidates = relevantSkus(results, title, limit);

        List<DiscoveredBook> found = new ArrayList<>();
        Set<String> seenIsbns = new LinkedHashSet<>();

        for (String sku : candidates) {
            try {
                String page = fetcher.get(PRODUCT + "/basic/" + sku + "/");
                KingstoneDiscoveryParsing.parse(page)
                        // Checked again against the 商品頁, which is the page that
                        // actually states what the book is: the 搜尋 listing only
                        // decided this was worth fetching.
                        .filter(book -> TitleRelevance.matches(title, book.title()))
                        // The same book reaches the results page more than once
                        // — 平裝 and a boxed set share an ISBN — and importing it
                        // twice would violate the unique ISBN on 版本.
                        .filter(book -> seenIsbns.add(book.isbn()))
                        .ifPresent(found::add);
            } catch (RuntimeException cause) {
                // One dead listing must not lose the other candidates: this is
                // a best-effort widening of the 書目, not a 取價 whose failure
                // the reader is waiting on.
                log.warn("找書失敗: 金石堂 sku {}", sku, cause);
            }
        }

        return List.copyOf(found);
    }

    /** Distinct product numbers whose listing 書名 relates to the search, capped. */
    private static List<String> relevantSkus(String html, String title, int limit) {
        Set<String> skus = new LinkedHashSet<>();
        Matcher matcher = LISTING.matcher(html);
        while (matcher.find() && skus.size() < limit) {
            if (TitleRelevance.matches(title, matcher.group(2))) {
                skus.add(matcher.group(1));
            }
        }
        return List.copyOf(skus);
    }
}
