package tw.bookprice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import tw.bookprice.member.MemberDetailsService;

/**
 * Two identity systems that never meet.
 *
 * They are separate filter chains with separate AuthenticationManagers, so
 * neither can authenticate the accounts of the other even by accident:
 *
 *   - /admin/**       管理後台 operator, HTTP Basic against one account from
 *                     application.yml. No database row, no 註冊 route.
 *   - everything else 前台會員, form login against the member table with BCrypt,
 *                     carried by a JSESSIONID session cookie.
 *
 * The AuthenticationManager on each chain is built here rather than left to
 * Spring Boot. Publishing a UserDetailsService bean — MemberDetailsService is
 * one — makes the auto-configured spring.security.user back off, which would
 * silently delete the 管理後台 account and leave /admin authenticating 會員
 * instead: exactly the merge these two systems must never have.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String adminUsername;
    private final String adminPassword;

    public SecurityConfig(
            @Value("${spring.security.user.name}") String adminUsername,
            @Value("${spring.security.user.password}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    /**
     * 會員 passwords. BCrypt, so what the database holds is a salted hash and
     * the plaintext cannot be recovered from it.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 管理後台. First in order, so /admin is decided here and never falls through
     * to the 會員 chain.
     *
     * The account lives in application.yml on purpose: it belongs to whoever
     * runs the service, not to the catalogue, and it must not be something a
     * 註冊 form could ever create.
     *
     * The configured password is hashed with the same encoder the 會員 use. It
     * must not be stored with a {noop} prefix: that marker is only understood
     * by a DelegatingPasswordEncoder, and against BCrypt it silently fails
     * every comparison — an 管理後台 nobody can sign in to, with no error to
     * say so.
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        AuthenticationManager operators = new ProviderManager(
                daoProvider(new InMemoryUserDetailsManager(User
                        .withUsername(adminUsername)
                        .password(passwordEncoder().encode(adminPassword))
                        .roles("ADMIN")
                        .build())));

        return http
                .securityMatcher("/admin", "/admin/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("ADMIN"))
                .authenticationManager(operators)
                .httpBasic(Customizer.withDefaults())
                // Stateless: the 管理後台 must not hand out a session cookie that
                // could be mistaken for a 會員 one.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                /*
                 * CSRF stays on here, unlike the 會員 chain: the 管理後台 is a
                 * browser talking to forms, and a browser that has answered the
                 * Basic challenge once will attach those credentials to a POST
                 * another site provokes.
                 *
                 * The token lives in a cookie rather than the session because
                 * this chain issues no session — one named JSESSIONID here
                 * would collide with the 會員 one on the same host, which is the
                 * whole reason it is stateless. The request handler is the
                 * eager one so the token is resolved while the Thymeleaf form
                 * is being rendered, rather than after it is too late to embed.
                 */
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfHandler()))
                .build();
    }

    /**
     * 前台會員 and the public 書目.
     *
     * 登入 is Spring Security form login with JSON-friendly handlers rather than
     * redirects: the caller is the Next.js server, which needs a status code,
     * not a 302 to a login page that does not exist on this origin.
     */
    @Bean
    @Order(2)
    SecurityFilterChain memberFilterChain(HttpSecurity http,
            MemberDetailsService memberDetailsService) throws Exception {

        AuthenticationManager members =
                new ProviderManager(daoProvider(memberDetailsService));

        return http
                .authorizeHttpRequests(requests -> requests
                        // The 書目 is public: 搜尋 and 比價 need no account.
                        .requestMatchers("/api/works/**", "/api/channels", "/api/facets")
                        .permitAll()
                        // 註冊 and 登入 themselves cannot require being signed in.
                        .requestMatchers("/api/members", "/api/session").permitAll()
                        // 會員專屬 endpoints all live under /api/me.
                        .requestMatchers("/api/me/**").hasRole("MEMBER")
                        .anyRequest().permitAll())
                .authenticationManager(members)
                .formLogin(form -> form
                        .loginProcessingUrl("/api/session")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) -> {
                            // The same sentence whether the 電子郵件 is unknown or
                            // the 密碼 is wrong, so this cannot be used to find
                            // out who holds an account here.
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\":{\"code\":\"BAD_CREDENTIALS\","
                                            + "\"message\":\"帳號或密碼不正確\"}}");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/session/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true))
                // 401 rather than a redirect: this chain only ever answers the
                // Next.js server, which reads status codes.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                /*
                 * CSRF is off on this chain because no browser ever holds this
                 * session cookie. The JSESSIONID is issued to the Next.js
                 * server, which keeps it in an httpOnly cookie on its own origin
                 * and attaches it server-side; a page on another site cannot
                 * make a browser send it here, because the browser does not have
                 * it to send.
                 *
                 * The forgery surface is therefore the Next.js server action,
                 * which Next guards with its own Origin check. If a browser is
                 * ever pointed straight at this API, CSRF has to come back on
                 * with it.
                 */
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Resolves the CSRF token as the page renders instead of deferring it, so
     * the hidden field is actually in the form Thymeleaf produces.
     */
    private static CsrfTokenRequestHandler eagerCsrfHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    private DaoAuthenticationProvider daoProvider(UserDetailsService users) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
