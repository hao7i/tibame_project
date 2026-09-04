package tw.bookprice.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * The Web API contract for 搜尋 and 最低價, exercised end to end over the real
 * JPA mappings against H2 (see application-test.yml).
 *
 * 最低價 is the interesting part: it is computed per request from the 報價 rows
 * rather than stored, so it has to be right across 版本 of both 載體 and right
 * when a 作品 has no 電子書 版本 at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogueSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueSeeder seeder;

    /** Idempotent, so it does not matter whether the runner already fired. */
    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    @Test
    @DisplayName("無查詢字串時回傳全部收錄的六個作品")
    void listsEveryWorkWhenQueryIsAbsent() throws Exception {
        mockMvc.perform(get("/api/works"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                // total counts every match; works holds one page of them.
                .andExpect(jsonPath("$.works.length()").value(4))
                .andExpect(jsonPath("$.query").doesNotExist())
                .andExpect(jsonPath("$.fetchedAt").exists());
    }

    @Test
    @DisplayName("以書名的子字串搜尋")
    void findsWorkByTitleSubstring() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "習慣"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("習慣"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));
    }

    @Test
    @DisplayName("以作者搜尋，且不分大小寫")
    void findsWorkByAuthorIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "harari"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("人類大歷史"));
    }

    @Test
    @DisplayName("以出版社搜尋")
    void findsWorkByPublisher() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "方智"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));
    }

    @Test
    @DisplayName("以紙本版本的 ISBN 搜尋")
    void findsWorkByPaperIsbn() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "9789861755267"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));
    }

    @Test
    @DisplayName("以電子書版本的 ISBN 搜尋，命中的是同一個作品")
    void findsSameWorkByEbookIsbn() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "9789861755274"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"))
                // 作品 is addressed by its 紙本 ISBN whichever 版本 matched.
                .andExpect(jsonPath("$.works[0].isbn").value("9789861755267"));
    }

    @Test
    @DisplayName("查無結果回傳空清單，不是退回全部書籍")
    void returnsEmptyResultSetWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "不存在的書名"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.works").isEmpty());
    }

    @Test
    @DisplayName("查詢字串裡的 LIKE 萬用字元被當成一般文字，不會擴大成全部")
    void likeWildcardsInTheQueryAreTreatedAsLiteralText() throws Exception {
        // Unescaped, "%_%" matches every non-empty 書名 and the screen would
        // report all six 作品 as the result for 「_」.
        mockMvc.perform(get("/api/works").param("q", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/works").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("最低價跨越紙本與電子書兩個版本取最小值")
    void bestPriceSpansEditionsOfBothFormats() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "原子習慣"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].listPrice").value(330))
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("樂天Kobo"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(231))
                .andExpect(jsonPath("$.works[0].bestPrice.discountPercent").value(70))
                // A round ten percent reads 「7 折」, not 「70 折」.
                .andExpect(jsonPath("$.works[0].bestPrice.discountLabel").value("7 折"));
    }

    @Test
    @DisplayName("作品沒有電子書版本時，最低價來自紙本報價")
    void bestPriceFallsToPaperWhenWorkHasNoEbookEdition() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "設計的設計"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("讀冊生活"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(492))
                .andExpect(jsonPath("$.works[0].bestPrice.discountPercent").value(82))
                .andExpect(jsonPath("$.works[0].bestPrice.discountLabel").value("82 折"))
                .andExpect(jsonPath("$.works[0].channelCount").value(4));
    }

    @Test
    @DisplayName("有貨通路數是實際有報價的通路數")
    void channelCountCountsChannelsHoldingAnOffer() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "如何閱讀一本書"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].channelCount").value(5));
    }

    @Test
    @DisplayName("通路價格對回傳全部通路，由前端決定顯示幾個")
    void returnsEveryChannelPriceRatherThanATruncatedList() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "原子習慣"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].channelPrices.length()").value(6))
                .andExpect(jsonPath("$.works[0].channelPrices[0].channel").value("五南文化廣場"))
                .andExpect(jsonPath("$.works[0].channelPrices[0].price").value(261))
                .andExpect(jsonPath("$.works[0].channelPrices[0].format").value("PAPER"))
                .andExpect(jsonPath("$.works[0].channelPrices[4].channel").value("樂天Kobo"))
                .andExpect(jsonPath("$.works[0].channelPrices[4].format").value("EBOOK"));
    }

    @Test
    @DisplayName("作品摘要帶出設計稿列表檢視需要的欄位")
    void workSummaryCarriesTheFieldsTheListViewRenders() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "被討厭的勇氣"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].isbn").value("9789861371955"))
                .andExpect(jsonPath("$.works[0].author").value("岸見一郎、古賀史健"))
                .andExpect(jsonPath("$.works[0].publisher").value("究竟"))
                .andExpect(jsonPath("$.works[0].publicationYear").value(2014))
                .andExpect(jsonPath("$.works[0].category").value("心理勵志"));
    }

    @Test
    @DisplayName("通路清單回傳六家，帶設計稿收錄通路區塊要的載體說明")
    void listsTheSixChannelsInDisplayOrder() throws Exception {
        mockMvc.perform(get("/api/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("五南文化廣場"))
                .andExpect(jsonPath("$[0].kind").value("紙本"))
                .andExpect(jsonPath("$[2].name").value("金石堂"))
                .andExpect(jsonPath("$[2].kind").value("紙本"))
                .andExpect(jsonPath("$[5].name").value("Readmoo"))
                .andExpect(jsonPath("$[5].kind").value("電子書"));
    }

    @Test
    @DisplayName("限定電子書時，最低價與通路只看電子書版本的報價")
    void ebookFilterNarrowsOffersToTheEbookEdition() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "原子習慣").param("format", "EBOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].channelPrices.length()").value(2))
                .andExpect(jsonPath("$.works[0].channelCount").value(2))
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("樂天Kobo"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(231));
    }

    @Test
    @DisplayName("限定紙本時，最低價改由最便宜的紙本通路出線")
    void paperFilterExcludesTheCheaperEbookOffers() throws Exception {
        mockMvc.perform(get("/api/works").param("q", "原子習慣").param("format", "PAPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].channelPrices.length()").value(4))
                .andExpect(jsonPath("$.works[0].channelCount").value(4))
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("五南文化廣場"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(261))
                .andExpect(jsonPath("$.works[0].bestPrice.discountLabel").value("79 折"));
    }

    @Test
    @DisplayName("限定電子書時，沒有電子書報價的作品不出現在結果中")
    void ebookFilterDropsWorksWithNoEbookOffer() throws Exception {
        mockMvc.perform(get("/api/works").param("format", "EBOOK"))
                .andExpect(status().isOk())
                // 設計的設計 has no 電子書 報價, so five of the six remain.
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.works[*].title")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("設計的設計"))));
    }

    @Test
    @DisplayName("載體參數留空等同全部版本，因為 GET 表單一定會送出這個欄位")
    void blankFormatMeansEveryEdition() throws Exception {
        mockMvc.perform(get("/api/works").param("format", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
    }

    @Test
    @DisplayName("載體參數給了不認得的值回 400 與一致的錯誤主體")
    void unknownFormatIsRejected() throws Exception {
        mockMvc.perform(get("/api/works").param("format", "AUDIOBOOK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("不支援的載體: AUDIOBOOK"));
    }
}
