package tw.bookprice.pricing;

import java.io.IOException;
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

    private final HttpClient client;
    private final String userAgent;
    private final Duration minimumInterval;
    private final Map<String, Long> lastRequestByHost = new ConcurrentHashMap<>();

    public HtmlFetcher(
            @Value("${bookprice.pricing.user-agent}") String userAgent,
            @Value("${bookprice.pricing.min-interval-ms:1500}") long minimumIntervalMs) {
        this.userAgent = userAgent;
        this.minimumInterval = Duration.ofMillis(minimumIntervalMs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Kingstone search redirects; following them is normal browsing.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
