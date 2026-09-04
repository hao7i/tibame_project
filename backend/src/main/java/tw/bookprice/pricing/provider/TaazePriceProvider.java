package tw.bookprice.pricing.provider;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.HtmlFetcher;
import tw.bookprice.pricing.JsonLdOffers;

/**
 * 讀冊生活 TAAZE — real 取價.
 *
 * The second live channel: robots.txt has one group for everybody and disallows
 * neither the search nor the product pages, and every product page carries
 * schema.org JSON-LD with ISBN and price. See the research document.
 *
 * Two things are specific to TAAZE and both are handled here rather than in the
 * shared parser:
 *
 *   - it is Taiwan main used-book channel, so one ISBN routinely returns a new
 *     listing and a used one. Only the 全新 copy is taken: a comparison table
 *     that silently mixed a used price into the same column would report a
 *     bargain the reader cannot actually buy new.
 *   - 絕版 titles publish 定價 and no 優惠價. Those are skipped rather than having
 *     the list price passed off as a selling price.
 */
@Component
public class TaazePriceProvider extends SeedChannelPriceProvider {

    private static final String SEARCH =
            "https://www.taaze.tw/rwd_searchResult.html?keyType%5B%5D=0&keyword%5B%5D=";
    private static final String HOST = "https://www.taaze.tw";

    private static final Pattern PRODUCT_LINK = Pattern.compile("/products/([0-9]{8,})[.]html");

    /** How many candidate listings one search is allowed to cost the host. */
    private static final int MAX_CANDIDATES = 6;

    private final HtmlFetcher fetcher;
    private final boolean live;

    public TaazePriceProvider(WorkRepository workRepository, HtmlFetcher fetcher,
            @Value("${bookprice.pricing.taaze.live:true}") boolean live) {
        super(workRepository);
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "TAAZE";
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        if (!live) {
            return super.fetch(isbn);
        }

        String results = fetcher.get(SEARCH + isbn);

        // Distinct ids, not distinct matches: both sites repeat the same link
        // several times per result, and counting repeats towards the limit left
        // only one listing actually tried.
        Set<String> candidates = new LinkedHashSet<>();
        Matcher matcher = PRODUCT_LINK.matcher(results);
        while (matcher.find() && candidates.size() < MAX_CANDIDATES) {
            candidates.add(matcher.group(1));
        }

        if (candidates.isEmpty()) {
            // 200 with no product links is how TAAZE says it has nothing.
            return Optional.empty();
        }

        return CandidateSearch.firstMatching(fetcher, candidates, id -> HOST + "/products/" + id + ".html",
                (url, html) -> TaazeParsing.readNewCopy(isbn, url, html));
    }

    /**
     * The 全新 listing on one product page, if that is what this page is.
     *
     * Package-private so the parser can be tested without touching the network.
     */
    static Optional<FetchedPrice> readNewCopy(String isbn, String productUrl, String html) {
        for (JsonNode block : JsonLdOffers.blocks(html)) {
            Integer price = JsonLdOffers.priceOf(block);
            if (price == null || JsonLdOffers.isUsed(block)) {
                continue;
            }

            String declared = JsonLdOffers.isbnOf(block);
            if (declared != null && !declared.equals(isbn)) {
                continue;
            }

            return Optional.of(new FetchedPrice(
                    price, JsonLdOffers.availabilityOf(block), productUrl,
                    JsonLdOffers.coverOf(block, html)));
        }
        return Optional.empty();
    }
}
