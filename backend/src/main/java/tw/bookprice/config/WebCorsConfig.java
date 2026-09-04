package tw.bookprice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The single place CORS is opened, so no controller carries a @CrossOrigin of
 * its own.
 *
 * Only the browser needs this: the results screen is a Server Component and
 * calls the API from the Next.js server, which CORS does not apply to. It is
 * here for the client-side calls the 追蹤 and 進階搜尋 work will add.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);
    }
}
