package tw.bookprice.pricing.provider;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.HtmlFetcher;
import tw.bookprice.pricing.PriceFetchException;

/**
 * 墊腳石 — real 取價.
 *
 * It replaced 樂天Kobo, whose Terms of Use are the only ones among the 通路
 * examined that ban 機器人 and 數據挖掘 outright, so that one could never be
 * priced honestly however the code was written.
 *
 * This is the most direct of the live 通路: robots.txt is a bare Allow for
 * everything, the ISBN is the product URL, and the product page carries
 * schema.org JSON-LD whose sku is that ISBN. One request, and the answer can be
 * checked against what was asked.
 *
 * A book it does not stock answers 404 rather than a page about something else,
 * which is cleaner than either 五南 (a real but unrelated book) or 金石堂 (200
 * with a wall of recommendations). That 404 is surfaced as 查無, not as a
 * failure — the shop answered, it simply does not have the book.
 */
@Component
public class TcsbPriceProvider extends SeedChannelPriceProvider {

    private static final String PRODUCT = "https://www.tcsb.com.tw/";

    private final HtmlFetcher fetcher;
    private final boolean live;

    public TcsbPriceProvider(WorkRepository workRepository, HtmlFetcher fetcher,
            @Value("${bookprice.pricing.tcsb.live:true}") boolean live) {
        super(workRepository);
        this.fetcher = fetcher;
        this.live = live;
    }

    @Override
    public String channelCode() {
        return "TCSB";
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        if (!live) {
            return super.fetch(isbn);
        }

        String productUrl = PRODUCT + isbn;
        try {
            return TcsbParsing.parse(isbn, productUrl, fetcher.get(productUrl));
        } catch (PriceFetchException cause) {
            // 404 is this shop saying it does not carry the book, which is an
            // answer rather than an outage, and must not be recorded as 取價失敗.
            if (cause.getMessage() != null && cause.getMessage().contains("HTTP 404")) {
                return Optional.empty();
            }
            throw cause;
        }
    }
}
