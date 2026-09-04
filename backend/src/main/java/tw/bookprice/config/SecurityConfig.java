package tw.bookprice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security is on the classpath, which locks every path by default. The
 * catalogue is public, so /api is opened here.
 *
 * The two identity systems the project calls for are not built yet and are
 * deliberately absent rather than stubbed:
 *   - 前台會員 (追蹤清單, 目標價) — session cookie form login, added with 會員註冊與登入.
 *   - /admin 管理後台 — HTTP Basic against a single operator account, added with
 *     the 管理後台 work. It stays a separate identity system from 會員.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Default-closed: only the catalogue API is open. anyRequest().permitAll()
        // would silently publish /admin the moment the 管理後台 adds its first
        // @Controller, with no failing test to say so.
        //
        // CSRF protection stays at its default. The catalogue API is read-only,
        // so nothing here needs it turned off, and the 會員 work will want it on.
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
