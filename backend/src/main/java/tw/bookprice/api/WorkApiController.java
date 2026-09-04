package tw.bookprice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.catalogue.CatalogueService;
import tw.bookprice.catalogue.dto.SearchResponse;

/** JSON API over 作品 for the Next.js front end. */
@RestController
@RequestMapping("/api/works")
public class WorkApiController {

    private final CatalogueService catalogueService;

    public WorkApiController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /**
     * 搜尋 by 書名, 作者, 出版社 or ISBN.
     *
     * @param q      the 搜尋 term; omit it to get 全部收錄書籍
     * @param format 載體 to narrow to, PAPER or EBOOK; omit or leave blank for
     *               全部版本. Taken as a String rather than the enum so that the
     *               blank value a GET form submits for 全部版本 means 全部版本
     *               instead of failing to bind.
     */
    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String format) {
        return catalogueService.search(q, format);
    }
}
