package tw.bookprice.pricing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place this project makes an outbound request to a 通路 website.
 *
 * Everything a polite crawler owes its host lives here rather than in each
 * provider, so no provider can forget it:
 *
 *   - an honest User-Agent naming the project and a contact address, so an
 *     operator who dislikes the traffic knows who to tell
 *   - a minimum interval between requests to the same host, enforced across
 *     threads, so a 取價 run arrives as a trickle rather than a burst
 *   - finite timeouts, because a shop that stops answering must not hold a
 *     取價 run open indefinitely
 *
 * Only pages robots.txt permits are fetched. That was checked per channel
 * before any provider was written; see
 * docs/research/book-price-channel-data-sources.md.
 */
@Component
public class HtmlFetcher {

    private static final Logger log = LoggerFactory.getLogger(HtmlFetcher.class);

    private final HttpClient client;
    private final String userAgent;
    private final Duration minimumInterval;
    private final Map<String, Long> lastRequestByHost = new ConcurrentHashMap<>();

    public HtmlFetcher(
            @Value("${bookprice.pricing.user-agent}") String userAgent,
            @Value("${bookprice.pricing.min-interval-ms:1500}") long minimumIntervalMs) {
        this.userAgent = userAgent;
        this.minimumInterval = Duration.ofMillis(minimumIntervalMs);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Kingstone search redirects; following them is normal browsing.
                .followRedirects(HttpClient.Redirect.NORMAL);

        trustWithExtraIntermediates().ifPresent(builder::sslContext);
        this.client = builder.build();
    }

    /**
     * @return the page body
     * @throws PriceFetchException on any non-200, timeout or transport failure —
     *                             one exception type, because every one of them
     *                             means the same thing to the caller: this 通路
     *                             could not be read this time
     */
    public String get(String url) {
        URI uri = URI.create(url);
        throttle(uri.getHost());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-TW,zh;q=0.9")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new PriceFetchException("HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        } catch (IOException cause) {
            throw new PriceFetchException("無法連線: " + cause.getMessage(), cause);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new PriceFetchException("取價被中斷", cause);
        }
    }

    /**
     * The default trust anchors plus any intermediate certificates we ship.
     *
     * 讀冊生活 serves an incomplete chain: the certificate naming its own issuer
     * is simply absent, and an unrelated older chain is sent instead. Browsers
     * and curl paper over it by following the caIssuers URL in the Authority
     * Information Access extension; the JDK does not, and its
     * enableAIAcaIssuers switch was measured against this host and did not fix
     * it either. Supplying the missing certificate ourselves does.
     *
     * This does not weaken verification. The JDK default anchors are all still
     * loaded, and the certificate added is the real issuer, published by Sectigo
     * at the address the site certificate names, chaining to a root the JDK
     * already trusts. Turning verification off instead would trade the transport
     * security of every outbound request for one shop price.
     *
     * Failure here is not fatal: an unreadable truststore falls back to the
     * default context, which is what every other 通路 needs anyway.
     */
    private static Optional<SSLContext> trustWithExtraIntermediates() {
        try {
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            try (InputStream in = Files.newInputStream(cacerts)) {
                trust.load(in, "changeit".toCharArray());
            }

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (Resource pem : new PathMatchingResourcePatternResolver()
                    .getResources("classpath:certs/*.pem")) {
                try (InputStream in = pem.getInputStream()) {
                    trust.setCertificateEntry(pem.getFilename(), factory.generateCertificate(in));
                }
            }

            TrustManagerFactory managers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trust);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return Optional.of(context);
        } catch (Exception cause) {
            log.warn("無法載入額外的中介憑證，改用預設信任設定", cause);
            return Optional.empty();
        }
    }

    /**
     * Spaces requests to one host.
     *
     * Deliberately per host rather than global: two 通路 are independent sites
     * and slowing one because the other was just asked would only make a run
     * take longer without being any kinder to anybody.
     */
    private void throttle(String host) {
        long wait;
        synchronized (lastRequestByHost) {
            long now = System.currentTimeMillis();
            long earliest = lastRequestByHost.getOrDefault(host, 0L) + minimumInterval.toMillis();
            wait = Math.max(0, earliest - now);
            lastRequestByHost.put(host, now + wait);
        }

        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new PriceFetchException("取價被中斷", cause);
            }
        }
    }
}
