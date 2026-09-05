package tw.bookprice.discovery;

import java.util.List;
import org.springframework.stereotype.Service;
import tw.bookprice.pricing.PriceRefreshService;

/**
 * 搜尋不到時的完整流程: 拿使用者輸入的字去通路找 → 寫進書目 → 取價.
 *
 * A separate bean from CatalogueImportService on purpose. The import is one
 * transaction and the 取價 that follows is a long run of network calls, which
 * must not sit inside it; and calling one method of a bean from another of the
 * same bean would go round the proxy and silently lose the transaction.
 *
 * The 取價 is the ordinary refreshAll, not a special one. Newly imported 報價 are
 * stamped at the epoch and therefore stale, so they are exactly the rows it
 * picks up, while everything already priced within the freshness window is
 * skipped — the new book is priced without re-asking five shops about the rest
 * of the 書目.
 */
@Service
public class BookLookupService {

    /** How many books one 書名 may add. Each one costs five 通路 a 取價. */
    private static final int IMPORT_LIMIT = 3;

    private final CatalogueImportService importService;
    private final PriceRefreshService priceRefreshService;

    public BookLookupService(CatalogueImportService importService,
            PriceRefreshService priceRefreshService) {
        this.importService = importService;
        this.priceRefreshService = priceRefreshService;
    }

    /**
     * 收錄 whatever the reader was looking for.
     *
     * An ISBN and a 書名 are answered by different routes, and the term itself
     * says which: an ISBN is checkable, so there is no need to ask the reader
     * which kind of thing they typed. The ISBN route is the better one wherever
     * it applies — it can confirm the book it found is the book asked for,
     * whereas a 書名 can only be judged for relevance.
     *
     * @return ISBNs of the books added, empty when nothing new was found
     */
    public List<String> lookup(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }

        String trimmed = term.trim();
        List<String> imported = Isbn13.isValid(trimmed)
                ? importService.importByIsbn(trimmed)
                : importService.importByTitle(trimmed, IMPORT_LIMIT);

        if (!imported.isEmpty()) {
            // Only the books just imported. Refreshing the whole 書目 here made
            // one reader's click pay for everything else that had gone stale,
            // and the per-host pause between requests meant that bill grew with
            // the 書目. What is stale elsewhere is the scheduler's business.
            priceRefreshService.refreshIsbns(imported);
        }
        return imported;
    }
}
