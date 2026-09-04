package tw.bookprice.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tw.bookprice.catalogue.CatalogueAdminService;
import tw.bookprice.pricing.PriceRefreshService;
import tw.bookprice.pricing.RefreshReport;

/**
 * 管理後台 — the only MVC Controller in this project, per ADR 0001.
 *
 * Everything a reader sees is served by Next.js; this renders Thymeleaf for
 * whoever runs the service. The routes therefore live entirely under /admin and
 * cannot collide with the front end, which owns /, /search, /works, /watch,
 * /advanced and /login.
 *
 * No Repository is injected here. The rule is not softened because the audience
 * is internal: a Controller that writes to a Repository is a Controller holding
 * 商業邏輯, whoever is looking at it.
 */
@Controller
public class AdminController {

    private final CatalogueAdminService catalogueAdminService;
    private final PriceRefreshService priceRefreshService;

    public AdminController(CatalogueAdminService catalogueAdminService,
            PriceRefreshService priceRefreshService) {
        this.catalogueAdminService = catalogueAdminService;
        this.priceRefreshService = priceRefreshService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("channelCount", catalogueAdminService.listChannels().size());
        model.addAttribute("works", catalogueAdminService.listWorks());
        return "admin/dashboard";
    }

    @GetMapping("/admin/channels")
    public String channels(Model model) {
        model.addAttribute("channels", catalogueAdminService.listChannels());
        return "admin/channels";
    }

    @PostMapping("/admin/channels/{code}")
    public String updateChannel(@PathVariable String code,
            @RequestParam(required = false) String searchUrlTemplate,
            RedirectAttributes redirect) {
        catalogueAdminService.updateChannelSearchUrl(code, searchUrlTemplate);
        redirect.addFlashAttribute("message", code + " 的購買連結已更新");
        return "redirect:/admin/channels";
    }

    @GetMapping("/admin/works")
    public String works(Model model) {
        model.addAttribute("works", catalogueAdminService.listWorks());
        return "admin/works";
    }

    @GetMapping("/admin/works/{isbn}")
    public String work(@PathVariable String isbn, Model model) {
        model.addAttribute("title", catalogueAdminService.titleOf(isbn));
        model.addAttribute("isbn", isbn);
        model.addAttribute("offers", catalogueAdminService.listOffers(isbn));
        return "admin/work";
    }

    @PostMapping("/admin/works/{isbn}/offers/{offerId}")
    public String updateOffer(@PathVariable String isbn,
            @PathVariable Long offerId,
            @RequestParam int price,
            @RequestParam(required = false) String stockStatus,
            RedirectAttributes redirect) {
        catalogueAdminService.updateOffer(isbn, offerId, price,
                (stockStatus == null) ? "" : stockStatus);
        redirect.addFlashAttribute("message", "報價已更新");
        return "redirect:/admin/works/" + isbn;
    }

    /**
     * 手動觸發一次取價.
     *
     * The report is passed through the redirect rather than rendered from the
     * POST, so a refresh of the browser does not run 取價 a second time.
     */
    @PostMapping("/admin/refresh")
    public String refresh(RedirectAttributes redirect) {
        RefreshReport report = priceRefreshService.refreshAll();
        redirect.addFlashAttribute("report", report);
        return "redirect:/admin";
    }
}
