package tw.bookprice.catalogue;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.MockMvc;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * 篩選 and 分頁 on 搜尋結果.
 *
 * The load-bearing rule is that 通路 does not merely hide rows: it narrows the
 * 報價 that 最低價 is computed from, so selecting one 通路 changes the price shown
 * for a 作品 rather than just removing 作品 from the list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogueFilterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueSeeder seeder;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    @Test
    @DisplayName("選了通路之後，最低價只從那些通路的報價裡算")
    void channelFilterRecomputesTheBestPrice() throws Exception {
        // Unfiltered, 原子習慣 is cheapest at 墊腳石 231.
        mockMvc.perform(get("/api/works").param("q", "原子習慣").param("channel", "KINGSTONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("金石堂"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(280))
                .andExpect(jsonPath("$.works[0].channelCount").value(1))
                .andExpect(jsonPath("$.works[0].channelPrices.length()").value(1));
    }

    @Test
    @DisplayName("同一組 facet 之內為 OR")
    void channelsWithinTheGroupAreOred() throws Exception {
        mockMvc.perform(get("/api/works")
                        .param("q", "原子習慣")
                        .param("channel", "TCSB")
                        .param("channel", "WUNAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].channelCount").value(2))
                // 墊腳石 and 五南 both ask 261 — the tie goes to the earlier 通路.
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(261));
    }

    @Test
    @DisplayName("跨組為 AND：分類與通路同時生效")
    void groupsAreAndedTogether() throws Exception {
        // 人文史地 alone is three 作品, and 墊腳石 stocks none of them.
        mockMvc.perform(get("/api/works").param("category", "人文史地"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/api/works")
                        .param("category", "人文史地")
                        .param("channel", "TCSB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // The same 通路 against the 分類 it does stock, so the empty answer above
        // is the AND narrowing rather than the 通路 being empty everywhere.
        mockMvc.perform(get("/api/works")
                        .param("category", "心理勵志")
                        .param("channel", "TCSB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @DisplayName("在選定通路沒有報價的作品會整個消失，不是顯示空價格")
    void worksWithNoOfferInTheChosenChannelsDropOut() throws Exception {
        mockMvc.perform(get("/api/works").param("channel", "TCSB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.works[*].title")
                        .value(not(hasItem("設計的設計"))));
    }

    @Test
    @DisplayName("價格上限過濾的是算出來的最低價")
    void priceCeilingFiltersOnTheComputedBestPrice() throws Exception {
        // 被討厭的勇氣 最低價 237; 原子習慣 261 已在上限之外。
        mockMvc.perform(get("/api/works").param("maxPrice", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[*].title").value(hasItem("被討厭的勇氣")));
    }

    @Test
    @DisplayName("價格上限與通路連動：通路一換，能過關的作品就變了")
    void priceCeilingMovesWithTheChannelSelection() throws Exception {
        // 金石堂 is the dearest 通路 for every 作品, so nothing survives 250 there,
        // even though two 作品 do when every 通路 counts.
        mockMvc.perform(get("/api/works").param("channel", "KINGSTONE").param("maxPrice", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("每頁五筆，總數是套用篩選後、取頁前的筆數")
    void resultsArePaged() throws Exception {
        mockMvc.perform(get("/api/works").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.works.length()").value(5));

        mockMvc.perform(get("/api/works").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.works.length()").value(1));
    }

    @Test
    @DisplayName("超出範圍的頁碼夾回最後一頁，不是回傳空白畫面")
    void anOutOfRangePageIsClampedToTheLastOne() throws Exception {
        mockMvc.perform(get("/api/works").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.works.length()").value(1));

        mockMvc.perform(get("/api/works").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("沒有結果時仍回報第一頁與零頁數")
    void anEmptyResultSetStillReportsSanePaging() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "不存在的書名"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.works").isEmpty());
    }

    @Test
    @DisplayName("facet 筆數是全站總數，不隨查詢字串改變")
    void facetCountsAreCatalogueWideTotals() throws Exception {
        mockMvc.perform(get("/api/facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.length()").value(5))
                .andExpect(jsonPath("$.channels[0].code").value("WUNAN"))
                .andExpect(jsonPath("$.channels[0].count").value(6))
                // 墊腳石 stocks two of the six 作品.
                .andExpect(jsonPath("$.channels[4].code").value("TCSB"))
                .andExpect(jsonPath("$.channels[4].count").value(2))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[?(@.name == '心理勵志')].count")
                        .value(hasItem(2)))
                .andExpect(jsonPath("$.categories[?(@.name == '人文史地')].count")
                        .value(hasItem(3)))
                .andExpect(jsonPath("$.categories[?(@.name == '藝術設計')].count")
                        .value(hasItem(1)));
    }


    @Test
    @DisplayName("非數字的頁碼回 400，不是 500")
    void aNonNumericPageIsARequestError() throws Exception {
        mockMvc.perform(get("/api/works").param("page", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/works").param("maxPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("篩選與搜尋字串可以疊加")
    void filtersComposeWithTheSearchTerm() throws Exception {
        mockMvc.perform(get("/api/works")
                        .param("q", "習慣")
                        .param("category", "心理勵志")
                        .param("channel", "TCSB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"))
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("墊腳石"));
    }
}
