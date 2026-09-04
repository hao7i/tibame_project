package tw.bookprice.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bookprice.catalogue.CatalogueService;
import tw.bookprice.catalogue.dto.ChannelView;

/** JSON API over 通路, feeding the 收錄通路 strip and the 通路 facet group. */
@RestController
@RequestMapping("/api/channels")
public class ChannelApiController {

    private final CatalogueService catalogueService;

    public ChannelApiController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    public List<ChannelView> list() {
        return catalogueService.listChannels();
    }
}
