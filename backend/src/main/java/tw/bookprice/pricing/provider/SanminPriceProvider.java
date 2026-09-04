package tw.bookprice.pricing.provider;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.HtmlFetcher;

/**
 * 三民網路書店 — real 取價.
 *
 * It replaced 誠品線上 in the six because 誠品 could not be read at all: a
 * client-rendered SPA with no price in its HTML, behind a robots.txt that
 * disallows /search for everybody. 三民 allows both /search/ and /product/ and
 * serves its price in the HTML.
 *
 * What makes it the easiest of the 通路 examined: its search form has a
 * dedicated ISBN field (ct=isbn), so the lookup is exact. 金石堂 and 讀冊生活
 * both have to be searched by keyword and then disambiguated among the hits,
 * which is where the edition mix-ups on those two came from.
 *
 * Only 售價 and 供貨 are taken, plus the product URL to link back to — no cover
 * image, no 內容簡介.
 */
@Component
public class SanminPriceProvider extends SeedChannelPriceProvider {

    private static final String SEARCH = "https://www.sanmin.com.tw/search/?ct=isbn&qu=";
    private static final String HOST = "https://www.sanmin.com.tw";

    /**
     * The result container id, not any /product/index/ link on the page.
     *
     * 三民 renders a recommendation strip above the actual results, and those
     * links come first in the document — taking links in order gave five promo
     * products before the book that was searched for. Real results are the only
     * things wrapped in a sProduct container carrying this id, so keying on it
     * cuts the candidates from six to two and removes the guesswork.
     */
    private static final Pattern PRODUCT_LINK = Pattern.compile("id=\"Prod([0-9]{6,})\"");

    /** How many listings one search is allowed to cost the host. */
    private static final int MAX_CANDIDATES = 3;

    private final HtmlFetcher fetcher;
    private final boolean live;

    public SanminPriceProvider(WorkRepository workRepository, HtmlFetcher fetcher,
            @Value("${bookprice.pricing.sanmin.live:true}") boolean live) {
        super(workRepository);
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "SANMIN";
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        if (!live) {
            return super.fetch(isbn);
        }

        String results = fetcher.get(SEARCH + isbn);

        Set<String> candidates = new LinkedHashSet<>();
        Matcher matcher = PRODUCT_LINK.matcher(results);
        while (matcher.find() && candidates.size() < MAX_CANDIDATES) {
            candidates.add(matcher.group(1));
        }

        if (candidates.isEmpty()) {
            // A search that matched nothing still answers 200, so the absence of
            // a product link is the only honest signal that 三民 has no such book.
            return Optional.empty();
        }

        return CandidateSearch.firstMatching(fetcher, candidates,
                id -> HOST + "/product/index/" + id,
                (url, html) -> SanminParsing.parse(isbn, url, html));
    }
}
