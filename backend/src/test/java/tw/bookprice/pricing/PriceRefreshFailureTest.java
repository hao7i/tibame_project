package tw.bookprice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.pricing.provider.KingstonePriceProvider;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * 單一通路取價失敗.
 *
 * The rule this pins is the one that decides whether the site is usable on a
 * bad day: 金石堂 being unreachable must cost 金石堂 its freshness and nothing
 * else. The other five keep their prices, the 作品 keeps its 最低價, and the
 * failing row says so instead of disappearing or reading as out of stock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PriceRefreshFailureTest {

    private static final String ATOMIC_PAPER = "9789861755267";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PriceRefreshService priceRefreshService;

    @Autowired
    private CatalogueSeeder seeder;

    @MockitoBean
    private KingstonePriceProvider kingstone;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
        given(kingstone.channelCode()).willReturn("KINGSTONE");
        given(kingstone.fetch(anyString()))
                .willThrow(new IllegalStateException("連線逾時"));
    }

    @Test
    @DisplayName("一家失敗不影響其他五家，報表逐通路分開計數")
    void oneFailingChannelDoesNotStopTheOthers() {
        RefreshReport report = priceRefreshService.refreshAll(true);

        var kingstoneRow = report.channels().stream()
                .filter(row -> row.channelCode().equals("KINGSTONE"))
                .findFirst().orElseThrow();

        assertThat(kingstoneRow.failed()).isPositive();
        assertThat(kingstoneRow.updated()).isZero();
        assertThat(kingstoneRow.note()).isEqualTo("連線逾時");

        assertThat(report.channels().stream()
                .filter(row -> !row.channelCode().equals("KINGSTONE"))
                .allMatch(row -> row.failed() == 0 && row.updated() > 0))
                .as("其餘五家仍要完成取價")
                .isTrue();

        assertThat(report.totalUpdated()).isPositive();
        assertThat(report.totalFailed()).isEqualTo(kingstoneRow.failed());
    }

    @Test
    @DisplayName("失敗的通路標記為過期，其餘維持現行，最低價照常算得出來")
    void theFailingChannelIsMarkedStaleAndTheRestAreNot() throws Exception {
        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works/" + ATOMIC_PAPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].stale")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].price")
                        .value(org.hamcrest.Matchers.hasItem(280)))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KOBO')].stale")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.bestPrice.price").value(231));
    }

    @Test
    @DisplayName("下一次成功的取價會清掉失敗標記")
    void aLaterSuccessClearsTheFlag() throws Exception {
        priceRefreshService.refreshAll(true);

        // willReturn(...).given(...) rather than given(mock.call()): the latter
        // would invoke the throwing stub that is already in place.
        willReturn(java.util.Optional.of(new FetchedPrice(275, "現貨", null)))
                .given(kingstone).fetch(anyString());
        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works/" + ATOMIC_PAPER))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].stale")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].price")
                        .value(org.hamcrest.Matchers.hasItem(275)));
    }
}
