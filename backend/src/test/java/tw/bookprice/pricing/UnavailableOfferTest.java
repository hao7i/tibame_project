package tw.bookprice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
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
 * 通路 回報查無 的處理.
 *
 * A 取價 that comes back empty is a clear answer — the shop does not carry the
 * book — and is not the same as a failed fetch. The difference matters most
 * after a 通路 replacement: the row still holds the price seeded for whichever
 * shop used to occupy that column, and presenting it as the new shop price
 * would be inventing a number.
 *
 * 原子習慣 is priced 280 at 金石堂 in the seed and 231 at 墊腳石, so a 金石堂
 * that disowns the book must not change 最低價, while its own column must stop
 * claiming 280.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnavailableOfferTest {

    private static final String ATOMIC = "9789861755267";

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
        willReturn(Optional.empty()).given(kingstone).fetch(anyString());
    }

    @Test
    @DisplayName("通路回報查無時，該通路不再出現在報價表，而不是留著舊價格")
    void aDisownedOfferLeavesTheTable() throws Exception {
        mockMvc.perform(get("/api/works/" + ATOMIC))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].price")
                        .value(org.hamcrest.Matchers.hasItem(280)));

        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works/" + ATOMIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')]")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("查無不影響其他通路，最低價照常算得出來")
    void theRestOfTheComparisonIsUntouched() throws Exception {
        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works/" + ATOMIC))
                .andExpect(jsonPath("$.bestPrice.price").value(261))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'TCSB')].price")
                        .value(org.hamcrest.Matchers.hasItem(261)));
    }

    @Test
    @DisplayName("有貨通路數會扣掉查無的通路")
    void theChannelCountDrops() throws Exception {
        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works?q=原子習慣"))
                .andExpect(jsonPath("$.works[0].channelCount").value(4))
                .andExpect(jsonPath("$.works[0].channelPrices[?(@.channelCode == 'KINGSTONE')]")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("查無是可逆的：下一次取到價就會回到表上")
    void aLaterSuccessBringsItBack() throws Exception {
        priceRefreshService.refreshAll(true);

        willReturn(Optional.of(new FetchedPrice(275, "現貨", null)))
                .given(kingstone).fetch(anyString());
        priceRefreshService.refreshAll(true);

        mockMvc.perform(get("/api/works/" + ATOMIC))
                .andExpect(jsonPath("$.offers[?(@.channelCode == 'KINGSTONE')].price")
                        .value(org.hamcrest.Matchers.hasItem(275)));
    }

    @Test
    @DisplayName("查無不是取價失敗：不會被標記為過期")
    void itIsNotReportedAsAFailure() {
        RefreshReport report = priceRefreshService.refreshAll(true);

        var kingstoneRow = report.channels().stream()
                .filter(row -> row.channelCode().equals("KINGSTONE"))
                .findFirst().orElseThrow();

        assertThat(kingstoneRow.notFound()).isPositive();
        assertThat(kingstoneRow.failed()).isZero();
        assertThat(kingstoneRow.updated()).isZero();
    }
}
