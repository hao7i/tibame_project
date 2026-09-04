package tw.bookprice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.catalogue.CatalogueService;
import tw.bookprice.catalogue.dto.FacetsView;

/**
 * The 篩選條件 rail options and their counts.
 *
 * Separate from the 搜尋 response because the counts are catalogue-wide totals:
 * they do not move with the query, so recomputing them on every 搜尋 would be
 * work that changes nothing.
 */
@RestController
@RequestMapping("/api/facets")
public class FacetApiController {

    private final CatalogueService catalogueService;

    public FacetApiController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /**
     * @param format 載體 the reader is looking at; counts exclude 通路 that cannot
     *               sell it, so no facet advertises a count it cannot deliver
     */
    @GetMapping
    public FacetsView list(@RequestParam(required = false) String format) {
        return catalogueService.listFacets(format);
    }
}
