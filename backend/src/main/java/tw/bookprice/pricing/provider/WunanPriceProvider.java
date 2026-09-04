package tw.bookprice.pricing.provider;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.HtmlFetcher;

/**
 * 五南文化廣場 — real 取價.
 *
 * It replaced 博客來 among the six because 博客來 can never be more than seed
 * data: its robots.txt blocks this crawler outright, so there is no version of
 * this project that prices it honestly. 五南 allows both search and product
 * pages, sets no crawl delay, and publishes a sitemap.
 *
 * The lookup is a single request — the ISBN is the product URL, so there is no
 * search page to disambiguate and no chance of landing on a neighbouring
 * edition. That is the same shape as 紀伊國屋 and better than the three
 * search-then-pick 通路 already live.
 *
 * The catch is that the ISBN in the URL is not verified by the site: it is
 * printed back on the page whatever it was, and some ISBNs return a real but
 * unrelated book instead of a not-found page. WunanParsing therefore checks the
 * 書名 and refuses anything else, which is why this provider passes the title
 * down. Its 搜尋 is not used at all: it returned nothing for a book the
 * catalogue demonstrably carries, so the index and the catalogue disagree.
 */
@Component
public class WunanPriceProvider extends SeedChannelPriceProvider {

    private static final String PRODUCT = "https://www.wunanbooks.com.tw/product.php?isbn=";

    private final HtmlFetcher fetcher;
    private final boolean live;

    public WunanPriceProvider(WorkRepository workRepository, HtmlFetcher fetcher,
            @Value("${bookprice.pricing.wunan.live:true}") boolean live) {
        super(workRepository);
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "WUNAN";
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        if (!live) {
            return super.fetch(isbn);
        }

        // Without a title to check the page against there is nothing to stop a
        // wrong book being priced, so no title means no answer.
        Optional<String> expected = expectedTitle(isbn);
        if (expected.isEmpty()) {
            return Optional.empty();
        }

        String productUrl = PRODUCT + isbn;
        return WunanParsing.parse(expected.get(), productUrl, fetcher.get(productUrl));
    }
}
