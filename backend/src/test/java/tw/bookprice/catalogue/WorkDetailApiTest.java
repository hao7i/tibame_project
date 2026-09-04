package tw.bookprice.catalogue;

import static org.hamcrest.Matchers.hasSize;
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
 * The Web API contract behind 單書比價, the core screen: every 通路 報價 for one
 * 作品, which of them is 最低價, and where 前往購買 sends the reader.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkDetailApiTest {

    private static final String ATOMIC_HABITS_PAPER = "9789861755267";
    private static final String ATOMIC_HABITS_EBOOK = "9789861755274";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueSeeder seeder;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    @Test
    @DisplayName("以 ISBN 取得作品，帶出中欄要顯示的欄位")
    void returnsTheWorkBehindAnIsbn() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(ATOMIC_HABITS_PAPER))
                .andExpect(jsonPath("$.title").value("原子習慣"))
                .andExpect(jsonPath("$.author").value("James Clear"))
                .andExpect(jsonPath("$.category").value("心理勵志"))
                .andExpect(jsonPath("$.listPrice").value(330))
                .andExpect(jsonPath("$.channelCount").value(6))
                .andExpect(jsonPath("$.blurb").isNotEmpty())
                .andExpect(jsonPath("$.fetchedAt").exists())
                .andExpect(jsonPath("$.offers.length()").value(6));
    }

    @Test
    @DisplayName("電子書版本的 ISBN 指向同一個作品，回傳的識別碼是紙本 ISBN")
    void theEbookIsbnAddressesTheSameWork() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_EBOOK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("原子習慣"))
                .andExpect(jsonPath("$.isbn").value(ATOMIC_HABITS_PAPER));
    }

    @Test
    @DisplayName("報價預設由低到高排序")
    void offersAreSortedByPriceByDefault() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].price").value(238))
                // 五南 and 墊腳石 both ask 261; ties fall back to 通路 order, so the
                // table never reshuffles between requests.
                .andExpect(jsonPath("$.offers[1].price").value(261))
                .andExpect(jsonPath("$.offers[1].channelCode").value("WUNAN"))
                .andExpect(jsonPath("$.offers[2].price").value(261))
                .andExpect(jsonPath("$.offers[2].channelCode").value("TCSB"))
                .andExpect(jsonPath("$.offers[5].price").value(280));
    }

    @Test
    @DisplayName("排序改為依通路時，順序是固定的通路順序")
    void offersCanBeSortedByChannelOrder() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER).param("sort", "CHANNEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].channel").value("五南文化廣場"))
                .andExpect(jsonPath("$.offers[2].channel").value("金石堂"))
                .andExpect(jsonPath("$.offers[5].channel").value("Readmoo"));
    }

    @Test
    @DisplayName("最低價那一列被標記，且只有一列")
    void exactlyOneOfferIsMarkedAsTheBest() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[?(@.best == true)]", hasSize(1)))
                // Default sort is 價格低→高, so the 最低價 row is also the first one.
                .andExpect(jsonPath("$.offers[0].best").value(true))
                .andExpect(jsonPath("$.offers[0].channel").value("Readmoo"))
                .andExpect(jsonPath("$.offers[1].best").value(false))
                .andExpect(jsonPath("$.bestPrice.channel").value("Readmoo"))
                .andExpect(jsonPath("$.bestPrice.price").value(238))
                // 較定價省: 330 - 238
                .andExpect(jsonPath("$.bestPrice.savingVsListPrice").value(92));
    }

    @Test
    @DisplayName("限定紙本時，報價表與最低價卡一起改變")
    void theFormatFilterMovesBothTheTableAndTheBestPriceCard() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER).param("format", "PAPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(5))
                .andExpect(jsonPath("$.channelCount").value(5))
                .andExpect(jsonPath("$.bestPrice.channel").value("五南文化廣場"))
                .andExpect(jsonPath("$.bestPrice.price").value(261))
                .andExpect(jsonPath("$.offers[?(@.best == true)]", hasSize(1)))
                .andExpect(jsonPath("$.offers[0].best").value(true))
                .andExpect(jsonPath("$.offers[0].channel").value("五南文化廣場"));
    }

    @Test
    @DisplayName("每一列帶出報價表要的載體、庫存與折扣")
    void everyOfferCarriesTheColumnsTheTableRenders() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER).param("sort", "CHANNEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].channelCode").value("WUNAN"))
                .andExpect(jsonPath("$.offers[0].formatLabel").value("紙本平裝"))
                .andExpect(jsonPath("$.offers[0].stockStatus").value("24 小時到貨"))
                .andExpect(jsonPath("$.offers[0].discountLabel").value("79 折"))
                .andExpect(jsonPath("$.offers[4].channelCode").value("TCSB"))
                .andExpect(jsonPath("$.offers[4].formatLabel").value("紙本平裝"))
                // Readmoo is the only 電子書 通路 left, so it is the row that
                // proves the 版本 columns still differ per 報價.
                .andExpect(jsonPath("$.offers[5].formatLabel").value("電子書 EPUB"))
                .andExpect(jsonPath("$.offers[5].stockStatus").value("立即下載"));
    }

    @Test
    @DisplayName("前往購買帶的是該報價所屬版本的 ISBN，不是作品的識別碼")
    void purchaseUrlCarriesTheIsbnOfTheEditionTheOfferBelongsTo() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER).param("sort", "CHANNEL"))
                .andExpect(status().isOk())
                // 金石堂 sells the 紙本 版本.
                .andExpect(jsonPath("$.offers[2].purchaseUrl")
                        .value("https://www.kingstone.com.tw/search/key/" + ATOMIC_HABITS_PAPER))
                // 墊腳石 also sells the 紙本 版本, and its link is the ISBN itself.
                // No 電子書 通路 has an ISBN link template any more — Readmoo only
                // gets its site root, because its ISBN search is not established
                // and /search/ is robots-disallowed — so the 電子書 half of this
                // rule is no longer demonstrable here.
                .andExpect(jsonPath("$.offers[4].purchaseUrl")
                        .value("https://www.tcsb.com.tw/" + ATOMIC_HABITS_PAPER));
    }

    @Test
    @DisplayName("查不到的 ISBN 回 404 與一致的錯誤主體")
    void unknownIsbnIsNotFound() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", "9789999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("排序參數給了不認得的值回 400")
    void unknownSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/works/{isbn}", ATOMIC_HABITS_PAPER).param("sort", "RANDOM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
