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
import tw.bookprice.pricing.JsonLdOffers;

/**
 * 金石堂 — real 取價.
 *
 * Chosen as one of the two live channels because its robots.txt names this
 * crawler and allows it outright, its product pages are server-rendered, and
 * they publish schema.org JSON-LD carrying ISBN and price together. See
 * docs/research/book-price-channel-data-sources.md.
 *
 * The flow is always 搜尋 then 商品頁: the product URL is keyed by 金石堂 own SKU,
 * not by ISBN, so there is no way to address a book directly.
 *
 * Only 售價 and 供貨 are taken, plus the product URL to link back to. Cover
 * images and 內容簡介 are deliberately not read: TIPO holds that tabulated price
 * data is not copyrightable subject matter, but the 著作 sitting next to it is.
 */
@Component
public class KingstonePriceProvider extends SeedChannelPriceProvider {

    private static final String SEARCH = "https://www.kingstone.com.tw/search/key/";
    private static final String PRODUCT = "https://www.kingstone.com.tw";

    /** Product links on the results page: /basic/{13-digit Kingstone SKU}/ */
    private static final Pattern PRODUCT_LINK = Pattern.compile("/basic/([0-9]{10,})/");

    /** How many listings one search is allowed to cost the host. */
    private static final int MAX_CANDIDATES = 3;

    private final HtmlFetcher fetcher;
    private final boolean live;

    public KingstonePriceProvider(WorkRepository workRepository, HtmlFetcher fetcher,
            @Value("${bookprice.pricing.kingstone.live:true}") boolean live) {
        super(workRepository);
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "KINGSTONE";
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        // Turning the switch off falls back to the 示意 answer rather than to an
        // error: the site stays demonstrable when the network is unavailable or
        // when an operator wants the outside world left alone.
        if (!live) {
            return super.fetch(isbn);
        }

        String results = fetcher.get(SEARCH + isbn);

        // A search with no matches still answers 200 and fills the page with
        // recommendations, so the absence of a product link is the only honest
        // signal that 金石堂 does not carry this ISBN.
        Set<String> candidates = distinctProductIds(results);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 金石堂 lists the 電子書 edition first for a 紙本 ISBN, so the first link
        // is not necessarily the right listing. Each candidate is checked and
        // only the one whose JSON-LD declares this exact ISBN is taken.
        return CandidateSearch.firstMatching(fetcher, candidates, sku -> PRODUCT + "/basic/" + sku + "/",
                (url, html) -> KingstoneParsing.parse(isbn, url, html));
    }

    /** Up to MAX_CANDIDATES distinct SKUs, in the order the page lists them. */
    private static Set<String> distinctProductIds(String html) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = PRODUCT_LINK.matcher(html);
        while (matcher.find() && ids.size() < MAX_CANDIDATES) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

}
