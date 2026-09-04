package tw.bookprice.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.catalogue.CatalogueService;
import tw.bookprice.catalogue.dto.SearchResponse;
import tw.bookprice.catalogue.dto.WorkDetail;
import tw.bookprice.catalogue.dto.WorkSearchQuery;

/** JSON API over 作品 for the Next.js front end. */
@RestController
@RequestMapping("/api/works")
public class WorkApiController {

    private final CatalogueService catalogueService;

    public WorkApiController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /**
     * 搜尋 by 書名, 作者, 出版社 or ISBN, narrowed by 載體, 通路, 分類 and 價格上限.
     *
     * @param q        the 搜尋 term; omit it to get 全部收錄書籍
     * @param format   載體 to narrow to, PAPER or EBOOK; omit or leave blank for
     *                 全部版本. Taken as a String rather than the enum so that the
     *                 blank value a GET form submits for 全部版本 means 全部版本
     *                 instead of failing to bind.
     * @param channel  通路 codes, repeatable; OR within the group
     * @param category 分類 names, repeatable; OR within the group
     * @param maxPrice 價格上限, applied to the computed 最低價
     * @param page     1-based; out-of-range values are clamped, not rejected
     */
    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) List<String> channel,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer page) {
        return catalogueService.search(
                new WorkSearchQuery(q, format, channel, category, maxPrice, page));
    }

    /**
     * One 作品 with every 通路 報價, for 單書比價.
     *
     * @param isbn   ISBN of either 版本; both address the same 作品
     * @param format 載體 to narrow to; omit or leave blank for 全部版本
     * @param sort   PRICE (價格低→高, the default) or CHANNEL (依通路)
     */
    @GetMapping("/{isbn}")
    public WorkDetail findOne(
            @PathVariable String isbn,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String sort) {
        return catalogueService.findWork(isbn, format, sort);
    }
}
