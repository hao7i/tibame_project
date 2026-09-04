package tw.bookprice.discovery;

import java.util.List;
import org.springframework.stereotype.Service;
import tw.bookprice.pricing.PriceRefreshService;

/**
 * 搜尋不到時的完整流程: 拿書名去通路找 → 寫進書目 → 取價.
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
     * @return ISBNs of the books added, empty when the 通路 knew nothing that we
     *         did not already hold
     */
    public List<String> lookup(String title) {
        List<String> imported = importService.importByTitle(title, IMPORT_LIMIT);
        if (!imported.isEmpty()) {
            priceRefreshService.refreshAll(false);
        }
        return imported;
    }
}
