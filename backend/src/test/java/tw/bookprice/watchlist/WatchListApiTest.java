package tw.bookprice.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tw.bookprice.member.MemberRepository;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * 追蹤清單 and 目標價.
 *
 * The rule under test is the 通知狀態 decision, which the design states three
 * ways: 未設目標價, 已達目標價 and 尚差 NT$ n. Each is pinned against a real 最低價
 * from the seeded 書目, including both sides of the boundary where 最低價 equals
 * the 目標價 exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchListApiTest {

    /** 原子習慣: 紙本 ISBN, 最低價 238 at Readmoo. */
    private static final String ATOMIC_PAPER = "9789861755267";
    private static final String ATOMIC_EBOOK = "9789861755274";
    private static final int ATOMIC_BEST = 238;

    private static final String EMAIL = "watcher@example.com";
    private static final String PASSWORD = "watch me now";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WatchItemRepository watchItemRepository;

    @Autowired
    private CatalogueSeeder seeder;

    private MockHttpSession session;

    @BeforeEach
    void signIn() throws Exception {
        seeder.seed();
        watchItemRepository.deleteAll();
        memberRepository.deleteAll();

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        session = (MockHttpSession) mockMvc.perform(post("/api/session")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private void setTarget(String isbn, String body) throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + isbn + "/target-price")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未登入不能讀取或修改追蹤清單")
    void theWatchListIsMembersOnly() throws Exception {
        mockMvc.perform(get("/api/me/watchlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/watchlist")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("加入與移除追蹤，計數跟著改變")
    void aMemberCanAddAndRemove() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/watchlist/count").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("原子習慣"))
                .andExpect(jsonPath("$[0].bestPrice.price").value(ATOMIC_BEST));

        mockMvc.perform(delete("/api/me/watchlist/" + ATOMIC_PAPER).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/watchlist/count").session(session))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("追蹤的對象是作品：兩個版本的 ISBN 指向同一筆，不會變成兩筆")
    void trackingIsPerWorkNotPerEdition() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_EBOOK).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(watchItemRepository.count()).isEqualTo(1);

        // 用電子書 ISBN 也移得掉同一個作品。
        mockMvc.perform(delete("/api/me/watchlist/" + ATOMIC_EBOOK).session(session))
                .andExpect(status().isNoContent());
        assertThat(watchItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("通知狀態：未設目標價")
    void withoutATargetTheStatusIsNoTarget() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("NO_TARGET"))
                .andExpect(jsonPath("$[0].targetPrice").doesNotExist())
                .andExpect(jsonPath("$[0].gap").doesNotExist());
    }

    @Test
    @DisplayName("通知狀態：尚差 NT$ n，差額是最低價減目標價")
    void aboveTheTargetReportsTheGap() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":200}");

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("ABOVE_TARGET"))
                .andExpect(jsonPath("$[0].targetPrice").value(200))
                .andExpect(jsonPath("$[0].gap").value(ATOMIC_BEST - 200));
    }

    @Test
    @DisplayName("通知狀態：最低價低於目標價即為已達目標價")
    void belowTheTargetIsReached() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":300}");

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("REACHED"))
                .andExpect(jsonPath("$[0].gap").doesNotExist());
    }

    @Test
    @DisplayName("邊界：最低價等於目標價算已達成，差一元則否")
    void theBoundaryIsInclusive() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));

        setTarget(ATOMIC_PAPER, "{\"targetPrice\":" + ATOMIC_BEST + "}");
        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("REACHED"));

        setTarget(ATOMIC_PAPER, "{\"targetPrice\":" + (ATOMIC_BEST - 1) + "}");
        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("ABOVE_TARGET"))
                .andExpect(jsonPath("$[0].gap").value(1));
    }

    @Test
    @DisplayName("目標價可以清掉，狀態回到未設目標價")
    void theTargetCanBeCleared() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":200}");
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":null}");

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$[0].status").value("NO_TARGET"));
    }

    @Test
    @DisplayName("零或負數的目標價會被擋下")
    void anImpossibleTargetIsRejected() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));

        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER + "/target-price")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPrice\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("重複追蹤不會清掉已設定的目標價")
    void addingTwiceKeepsTheTarget() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":200}");

        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].targetPrice").value(200));
    }

    @Test
    @DisplayName("清空清單")
    void theWholeListCanBeCleared() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        mockMvc.perform(put("/api/me/watchlist/9789864792917").session(session));

        mockMvc.perform(delete("/api/me/watchlist").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("追蹤項目與目標價存在資料庫，重新登入後仍在")
    void theListSurvivesANewSession() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));
        setTarget(ATOMIC_PAPER, "{\"targetPrice\":200}");

        mockMvc.perform(post("/api/session/logout").session(session))
                .andExpect(status().isNoContent());

        MockHttpSession fresh = (MockHttpSession) mockMvc.perform(post("/api/session")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/me/watchlist").session(fresh))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].targetPrice").value(200));
    }

    @Test
    @DisplayName("一個會員看不到也改不動另一個會員的清單")
    void oneMemberCannotReachTheListOfAnother() throws Exception {
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER).session(session));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        MockHttpSession other = (MockHttpSession) mockMvc.perform(post("/api/session")
                        .param("email", "other@example.com")
                        .param("password", PASSWORD))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/me/watchlist").session(other))
                .andExpect(jsonPath("$.length()").value(0));

        // 改別人追蹤的書會失敗，因為那本書不在自己的清單上。
        mockMvc.perform(put("/api/me/watchlist/" + ATOMIC_PAPER + "/target-price")
                        .session(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPrice\":100}"))
                .andExpect(status().isNotFound());

        // 原本那位會員的清單不受影響。
        mockMvc.perform(get("/api/me/watchlist").session(session))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("NO_TARGET"));
    }
}
