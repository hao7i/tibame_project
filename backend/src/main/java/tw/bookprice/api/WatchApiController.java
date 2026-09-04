package tw.bookprice.api;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.watchlist.WatchService;
import tw.bookprice.watchlist.dto.TargetPriceRequest;
import tw.bookprice.watchlist.dto.WatchItemView;

/**
 * 追蹤清單, for the signed-in 會員 only.
 *
 * Everything here sits under /api/me, which the security configuration requires
 * ROLE_MEMBER for — so no method needs to check who is calling, and none can
 * forget to. The 會員 is taken from the session Principal rather than from a
 * parameter, so one reader can never address the list of another.
 */
@RestController
@RequestMapping("/api/me/watchlist")
public class WatchApiController {

    private final WatchService watchService;

    public WatchApiController(WatchService watchService) {
        this.watchService = watchService;
    }

    @GetMapping
    public List<WatchItemView> list(Principal principal) {
        return watchService.list(principal.getName());
    }

    /** Just the number, for the 導覽列 count on every page. */
    @GetMapping("/count")
    public Map<String, Long> count(Principal principal) {
        return Map.of("count", watchService.count(principal.getName()));
    }

    /** 追蹤 a 作品. PUT because pressing it twice must mean the same as once. */
    @PutMapping("/{isbn}")
    public ResponseEntity<Void> add(@PathVariable String isbn, Principal principal) {
        watchService.add(principal.getName(), isbn);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{isbn}")
    public ResponseEntity<Void> remove(@PathVariable String isbn, Principal principal) {
        watchService.remove(principal.getName(), isbn);
        return ResponseEntity.noContent().build();
    }

    /** 清空清單. */
    @DeleteMapping
    public ResponseEntity<Void> clear(Principal principal) {
        watchService.clear(principal.getName());
        return ResponseEntity.noContent().build();
    }

    /** 設定目標價; a null targetPrice clears it back to 未設目標價. */
    @PutMapping("/{isbn}/target-price")
    public WatchItemView setTargetPrice(@PathVariable String isbn,
            @Valid @RequestBody TargetPriceRequest request,
            Principal principal) {
        return watchService.setTargetPrice(principal.getName(), isbn, request.targetPrice());
    }
}
