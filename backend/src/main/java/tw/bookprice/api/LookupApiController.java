package tw.bookprice.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.discovery.BookLookupService;
import tw.bookprice.discovery.dto.LookupRequest;
import tw.bookprice.discovery.dto.LookupResult;

/**
 * 依書名或 ISBN 到各通路找書並收進書目.
 *
 * POST rather than GET although it reads like a search: it writes to the 書目 and
 * costs the 通路 a run of page fetches, neither of which may happen because a
 * crawler followed a link or a reader refreshed.
 *
 * Slow by nature — a 搜尋 at one 通路, a 商品頁 per candidate, then 取價 at five —
 * so the caller is expected to show that it is running.
 */
@RestController
@RequestMapping("/api/lookups")
public class LookupApiController {

    private final BookLookupService lookupService;

    public LookupApiController(BookLookupService lookupService) {
        this.lookupService = lookupService;
    }

    /**
     * @return 201 with the ISBNs added, or 200 with an empty list when nothing
     *         new was found — an empty result is an answer, not an error
     */
    @PostMapping
    public ResponseEntity<LookupResult> lookup(@Valid @RequestBody LookupRequest request) {
        List<String> imported = lookupService.lookup(request.term());
        LookupResult result = new LookupResult(imported);

        return imported.isEmpty()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
