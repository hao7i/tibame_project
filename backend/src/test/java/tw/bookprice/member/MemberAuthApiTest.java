package tw.bookprice.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 會員註冊與登入.
 *
 * The load-bearing rules here are security ones, so each is pinned by a test
 * that fails loudly if it regresses: passwords are never stored as typed, an
 * unauthenticated caller cannot reach 會員專屬 endpoints, and the 管理後台
 * account and the 會員 accounts cannot authenticate against each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberAuthApiTest {

    private static final String EMAIL = "reader@example.com";
    private static final String PASSWORD = "correct horse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearMembers() {
        memberRepository.deleteAll();
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("可以註冊、登入、登出")
    void aMemberCanRegisterSignInAndOut() throws Exception {
        register(EMAIL, PASSWORD);

        HttpSession session = mockMvc.perform(post("/api/session")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(post("/api/session/logout").session((MockHttpSession) session))
                .andExpect(status().isNoContent());

        // The session is invalidated, so the same one no longer identifies anybody.
        mockMvc.perform(get("/api/me").session((MockHttpSession) session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("密碼以 BCrypt 雜湊儲存，資料庫中不存明文")
    void thePasswordIsStoredOnlyAsABcryptHash() throws Exception {
        register(EMAIL, PASSWORD);

        Member stored = memberRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("未登入呼叫會員專屬的 API 會得到未授權回應")
    void memberOnlyEndpointsRefuseAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("書目 API 仍然公開，不因為加了會員而需要登入")
    void theCatalogueStaysPublic() throws Exception {
        mockMvc.perform(get("/api/works")).andExpect(status().isOk());
        mockMvc.perform(get("/api/channels")).andExpect(status().isOk());
        mockMvc.perform(get("/api/facets")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("密碼錯誤回 401，且訊息不透露這個電子郵件是否存在")
    void badCredentialsAreRefusedWithoutRevealingWhoHasAnAccount() throws Exception {
        register(EMAIL, PASSWORD);

        String wrongPassword = mockMvc.perform(post("/api/session")
                        .param("email", EMAIL)
                        .param("password", "not the password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/session")
                        .param("email", "stranger@example.com")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    @DisplayName("重複的電子郵件回 409，且大小寫不同視為同一個帳號")
    void theSameEmailCannotBeRegisteredTwice() throws Exception {
        register(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Reader@Example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("大小寫不同的電子郵件可以登入同一個帳號")
    void signingInIsNotCaseSensitive() throws Exception {
        register(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/session")
                        .param("email", "READER@example.com")
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("太短的密碼或格式錯誤的電子郵件會被擋下")
    void registrationValidatesItsInput() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("會員帳號無法登入 /admin，管理帳號也無法登入前台")
    void theTwoIdentitySystemsAreSeparate() throws Exception {
        register(EMAIL, PASSWORD);

        // A 會員 is not an operator: /admin refuses the account entirely.
        mockMvc.perform(get("/admin").with(httpBasic(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());

        // And the 管理後台 account is not a 會員: it has no row to sign in with.
        mockMvc.perform(post("/api/session")
                        .param("email", "admin")
                        .param("password", "admin1234"))
                .andExpect(status().isUnauthorized());

        assertThat(memberRepository.findByEmail("admin")).isEmpty();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor httpBasic(
            String username, String password) {
        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.httpBasic(username, password);
    }
}
