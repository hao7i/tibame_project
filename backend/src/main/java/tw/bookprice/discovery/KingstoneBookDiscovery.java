package tw.bookprice.discovery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    /** An ISBN 搜尋 is precise, so only a couple of listings are ever worth opening. */
    private static final int MAX_ISBN_CANDIDATES = 2;

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

        // Fetched together, read in order. The fetcher still spaces requests to
        // 金石堂 by the same interval — it reserves each slot before sleeping, so
        // the shop sees exactly the rate it saw before — but the network time of
        // one page now overlaps the wait for the next instead of following it.
        // Sequentially this was [wait, fetch] three times over; now it is one
        // run of waits with the fetches tucked inside.
        for (String page : fetchPages(candidates)) {
            if (page == null) {
                continue;
            }
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
        }

        return List.copyOf(found);
    }

    /**
     * The 商品頁 for each candidate, in the order asked, with null where one could
     * not be read.
     *
     * A dead listing must not lose the others: this is a best-effort widening of
     * the 書目, not a 取價 whose failure the reader is waiting on. Virtual threads
     * because each task is a blocked socket, and the ordering is preserved so
     * that which duplicate ISBN wins does not depend on which page came back
     * first.
     */
    private List<String> fetchPages(List<String> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = skus.stream()
                    .map(sku -> pool.submit(() -> {
                        try {
                            return fetcher.get(PRODUCT + "/basic/" + sku + "/");
                        } catch (RuntimeException cause) {
                            log.warn("找書失敗: 金石堂 sku {}", sku, cause);
                            return null;
                        }
                    }))
                    .toList();

            List<String> pages = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                try {
                    pages.add(future.get());
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    pages.add(null);
                } catch (ExecutionException cause) {
                    log.warn("找書任務失敗", cause.getCause());
                    pages.add(null);
                }
            }
            return pages;
        }
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

    /**
     * 金石堂 product pages are keyed by its own SKU, not by ISBN, so even a
     * direct lookup goes through 搜尋 first. Searching for the ISBN itself is
     * precise enough that the shop returns the book and little else.
     *
     * No 書名 judgement here, and none is wanted: the 商品頁 declares an ISBN, and
     * it either is the one asked for or the candidate is discarded.
     */
    @Override
    public Optional<DiscoveredBook> byIsbn(String isbn) {
        if (!live || !Isbn13.isValid(isbn)) {
            return Optional.empty();
        }

        String results = fetcher.get(SEARCH + isbn);

        for (String sku : skus(results, MAX_ISBN_CANDIDATES)) {
            try {
                String page = fetcher.get(PRODUCT + "/basic/" + sku + "/");
                Optional<DiscoveredBook> book = KingstoneDiscoveryParsing.parse(page)
                        .filter(candidate -> candidate.isbn().equals(isbn));
                if (book.isPresent()) {
                    return book;
                }
            } catch (RuntimeException cause) {
                // A dead listing among the candidates is not the answer to
                // whether this ISBN exists; the remaining ones still might be.
                log.warn("依 ISBN 找書失敗: 金石堂 sku {}", sku, cause);
            }
        }

        return Optional.empty();
    }

    /** Distinct product numbers in page order, capped. */
    private static List<String> skus(String html, int limit) {
        Set<String> skus = new LinkedHashSet<>();
        Matcher matcher = LISTING.matcher(html);
        while (matcher.find() && skus.size() < limit) {
            skus.add(matcher.group(1));
        }
        return List.copyOf(skus);
    }
}
