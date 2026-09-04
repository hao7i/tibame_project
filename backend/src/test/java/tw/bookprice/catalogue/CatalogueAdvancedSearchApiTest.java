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
 * 進階搜尋 — 欄位條件, 布林運算, 價格區間 and 出版年.
 *
 * The load-bearing rule is that the 查詢式預覽 on the form is not decoration: the
 * results page has to apply exactly what it describes. Each 布林 operator is
 * therefore pinned to a combination whose answer differs between AND, OR and
 * NOT, so a mix-up cannot pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogueAdvancedSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogueSeeder seeder;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    @Test
    @DisplayName("單一欄位條件只比對該欄位，不會擴散到其他欄位")
    void aFieldConditionMatchesOnlyThatField() throws Exception {
        // 究竟 is the 出版社 of 被討厭的勇氣 and appears in no 書名.
        mockMvc.perform(get("/api/works").param("field1", "publisher").param("term1", "究竟"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("被討厭的勇氣"));

        mockMvc.perform(get("/api/works").param("field1", "title").param("term1", "究竟"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("AND：兩列都成立才算數")
    void andRequiresBothRows() throws Exception {
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "習慣")
                        .param("op", "AND")
                        .param("field2", "publisher").param("term2", "方智"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));

        // 原子習慣 is 方智; 被討厭的勇氣 is 究竟. AND across the two leaves nothing.
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "勇氣")
                        .param("op", "AND")
                        .param("field2", "publisher").param("term2", "方智"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("OR：任一列成立即可")
    void orAcceptsEitherRow() throws Exception {
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "習慣")
                        .param("op", "OR")
                        .param("field2", "publisher").param("term2", "究竟"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.works[*].title").value(hasItem("原子習慣")))
                .andExpect(jsonPath("$.works[*].title").value(hasItem("被討厭的勇氣")));
    }

    @Test
    @DisplayName("NOT：第一列成立且第二列不成立")
    void notExcludesTheSecondRow() throws Exception {
        // 每一本的作者都含有母音 a 以外的字，改以出版年段落挑：先取全部，再排除 台灣商務。
        mockMvc.perform(get("/api/works")
                        .param("field1", "category").param("term1", "人文史地"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/works")
                        .param("field1", "publisher").param("term1", "商務"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // 書名含「書」的兩本：如何閱讀一本書。NOT 掉 台灣商務 之後應該一本都不剩。
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "書")
                        .param("op", "NOT")
                        .param("field2", "publisher").param("term2", "台灣商務"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // 同一組條件改成 AND 就會留下那一本，證明 NOT 真的取了反面。
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "書")
                        .param("op", "AND")
                        .param("field2", "publisher").param("term2", "台灣商務"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("如何閱讀一本書"));
    }

    @Test
    @DisplayName("空白的關鍵字不成為條件，布林運算跟著失效")
    void aBlankRowIsNotACondition() throws Exception {
        // 第二列空白時 NOT 不得把結果清空 — 根本沒有第二個條件可以否定。
        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "習慣")
                        .param("op", "NOT")
                        .param("field2", "publisher").param("term2", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));

        // 兩列皆空白等於沒有條件，回全部收錄書籍。
        mockMvc.perform(get("/api/works").param("term1", "").param("term2", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
    }

    @Test
    @DisplayName("價格區間比對的是算出來的最低價，上下限同時生效")
    void thePriceRangeAppliesToTheComputedBestPrice() throws Exception {
        mockMvc.perform(get("/api/works").param("minPrice", "300").param("maxPrice", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[*].title").value(hasItem("人類大歷史")))
                .andExpect(jsonPath("$.works[*].title").value(hasItem("如何閱讀一本書")))
                // 原子習慣 最低價 231, below the floor.
                .andExpect(jsonPath("$.works[*].title").value(not(hasItem("原子習慣"))));
    }

    @Test
    @DisplayName("出版年：指定年份與「或更早」")
    void publicationYearNarrowsTheCatalogue() throws Exception {
        mockMvc.perform(get("/api/works").param("year", "2019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"));

        // 六本的出版年都在 2024 之前，所以「2024 或更早」等於全部收錄書籍。
        mockMvc.perform(get("/api/works").param("year", "2024-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));

        mockMvc.perform(get("/api/works").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("譯者與系列是有效欄位，但書目尚未填，因此誠實地查無結果")
    void translatorAndSeriesAreRecognisedButEmpty() throws Exception {
        for (String field : new String[] {"translator", "series"}) {
            mockMvc.perform(get("/api/works").param("field1", field).param("term1", "林"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }
    }

    @Test
    @DisplayName("不支援的搜尋欄位或布林運算回 400，不是悄悄改查書名")
    void anUnknownFieldOrOperatorIsRejected() throws Exception {
        mockMvc.perform(get("/api/works").param("field1", "叢書").param("term1", "習慣"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/works")
                        .param("field1", "title").param("term1", "習慣")
                        .param("op", "XOR")
                        .param("field2", "title").param("term2", "勇氣"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("進階條件與既有的通路、載體篩選疊加")
    void advancedConditionsComposeWithTheExistingFilters() throws Exception {
        mockMvc.perform(get("/api/works")
                        .param("field1", "author").param("term1", "clear")
                        .param("channel", "TCSB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.works[0].title").value("原子習慣"))
                .andExpect(jsonPath("$.works[0].bestPrice.channel").value("墊腳石"))
                .andExpect(jsonPath("$.works[0].bestPrice.price").value(261));
    }
}
