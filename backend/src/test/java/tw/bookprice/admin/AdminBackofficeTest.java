package tw.bookprice.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.repository.Repository;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.CatalogueAdminService;
import tw.bookprice.member.MemberRepository;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * /admin 管理後台.
 *
 * The rules worth a test here are the boundaries, not the markup: who may in,
 * who may not, and that the one MVC Controller in the project keeps to the
 * layering the rest of the code follows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// 這個類別會改動 seed 資料（售價、通路連結）。Spring 的測試情境是跨類別共用的，
// 不回滾就會把後面每一個讀 seed 的測試一起弄壞。
@Transactional
class AdminBackofficeTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin1234";

    private static final String ATOMIC_PAPER = "9789861755267";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueSeeder seeder;

    @Autowired
    private CatalogueAdminService catalogueAdminService;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("未帶帳密時每個 /admin 頁面都要求驗證")
    void everyAdminPageIsProtected() throws Exception {
        for (String path : new String[] {
                "/admin", "/admin/channels", "/admin/works", "/admin/works/" + ATOMIC_PAPER}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/admin/refresh").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("前台會員帳號無法登入 /admin")
    void aMemberAccountCannotReachTheBackoffice() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@example.com\",\"password\":\"letmein12345\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin").with(httpBasic("reader@example.com", "letmein12345")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理帳號可以瀏覽總覽、通路與書目")
    void theOperatorCanBrowse() throws Exception {
        mockMvc.perform(get("/admin").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("手動觸發一次取價")));

        mockMvc.perform(get("/admin/channels").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("channels"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("五南文化廣場")));

        mockMvc.perform(get("/admin/works").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("原子習慣")));

        mockMvc.perform(get("/admin/works/" + ATOMIC_PAPER).with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("offers"));
    }

    @Test
    @DisplayName("可以維護報價，且手動修改不會改動取價時間")
    void anOfferCanBeCorrectedWithoutFakingAFetch() throws Exception {
        var before = catalogueAdminService.listOffers(ATOMIC_PAPER).stream()
                .filter(row -> row.channelCode().equals("WUNAN"))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/admin/works/" + ATOMIC_PAPER + "/offers/" + before.id())
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)).with(csrf())
                        .param("price", "199")
                        .param("stockStatus", "限量"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/works/" + ATOMIC_PAPER));

        var after = catalogueAdminService.listOffers(ATOMIC_PAPER).stream()
                .filter(row -> row.id().equals(before.id()))
                .findFirst().orElseThrow();

        assertThat(after.price()).isEqualTo(199);
        assertThat(after.stockStatus()).isEqualTo("限量");
        // 取價時間 belongs to the 通路, not to the operator editing a figure.
        assertThat(after.fetchedAt()).isEqualTo(before.fetchedAt());
    }

    @Test
    @DisplayName("可以維護通路的購買連結樣板")
    void aChannelLinkCanBeMaintained() throws Exception {
        mockMvc.perform(post("/admin/channels/WUNAN")
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)).with(csrf())
                        .param("searchUrlTemplate", "https://www.books.com.tw/products/{isbn}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/channels"));

        assertThat(catalogueAdminService.listChannels().stream()
                .filter(row -> row.code().equals("WUNAN"))
                .findFirst().orElseThrow()
                .searchUrlTemplate())
                .isEqualTo("https://www.books.com.tw/products/{isbn}");
    }

    @Test
    @DisplayName("手動觸發取價會回報每個通路的結果，且更新取價時間")
    void aManualRefreshReportsPerChannel() throws Exception {
        var before = catalogueAdminService.listOffers(ATOMIC_PAPER).getFirst();

        mockMvc.perform(post("/admin/refresh")
                        .with(httpBasic(ADMIN, ADMIN_PASSWORD)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("report"));

        var after = catalogueAdminService.listOffers(ATOMIC_PAPER).stream()
                .filter(row -> row.id().equals(before.id()))
                .findFirst().orElseThrow();

        // 示意實作 returns what is already on record, so the price holds and only
        // 取價時間 moves — which is exactly what a 示意 run should be able to claim.
        assertThat(after.price()).isEqualTo(before.price());
        assertThat(after.fetchedAt()).isAfterOrEqualTo(before.fetchedAt());
    }

    @Test
    @DisplayName("CSRF token 缺席時，會改變狀態的 POST 會被拒絕")
    void stateChangingPostsRequireACsrfToken() throws Exception {
        mockMvc.perform(post("/admin/refresh").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MVC Controller 不直接注入 Repository")
    void theControllerDependsOnServicesOnly() {
        Constructor<?> constructor = AdminController.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()))
                .as("管理後台的 Controller 必須經過 Service")
                .noneMatch(Repository.class::isAssignableFrom);
    }
}
