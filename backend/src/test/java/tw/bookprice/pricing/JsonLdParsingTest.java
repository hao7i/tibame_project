package tw.bookprice.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 取價 parsing, against JSON-LD captured from the real product pages.
 *
 * The fixtures are the schema.org blocks and nothing else — no cover image, no
 * 內容簡介 — which is the same restraint the live providers apply. They exist so
 * the fragile half of 取價 can be tested without asking either shop for a page
 * on every build.
 *
 * The prices in them are what those two sites actually said for 原子習慣 on the
 * day this was written: 金石堂 261, 讀冊生活 260.
 */
class JsonLdParsingTest {

    private static final String ATOMIC_ISBN = "9789861755267";

    private String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("金石堂：一次 JSON-LD 解析同時取得 ISBN、售價與供貨")
    void kingstoneProductPageYieldsPriceAndAvailability() throws Exception {
        var parsed = tw.bookprice.pricing.provider.KingstoneParsing.parse(
                ATOMIC_ISBN, "https://www.kingstone.com.tw/basic/2011760319040/",
                fixture("kingstone-product.html"));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().price()).isEqualTo(261);
        assertThat(parsed.get().stockStatus()).isEqualTo("有貨");
        assertThat(parsed.get().productUrl())
                .isEqualTo("https://www.kingstone.com.tw/basic/2011760319040/");
    }

    @Test
    @DisplayName("讀冊生活：取全新書的售價，小數點寫法要正確進位")
    void taazeProductPageYieldsTheNewCopyPrice() throws Exception {
        var parsed = tw.bookprice.pricing.provider.TaazeParsing.readNewCopy(
                ATOMIC_ISBN, "https://www.taaze.tw/products/11100876604.html",
                fixture("taaze-product.html"));

        assertThat(parsed).isPresent();
        // TAAZE writes 260.0, not 260.
        assertThat(parsed.get().price()).isEqualTo(260);
    }

    @Test
    @DisplayName("讀冊生活的二手書不得混進比價：那是讀者買不到的全新價")
    void aUsedListingIsNotTakenAsTheNewPrice() {
        String usedOnly = """
                <script type="application/ld+json">
                {"@type":"Product","isbn":"9789861755267",
                 "itemCondition":"https://schema.org/UsedCondition",
                 "offers":{"@type":"Offer","price":"94.0","priceCurrency":"TWD",
                 "availability":"Instock"}}
                </script>
                """;

        Optional<FetchedPrice> parsed = tw.bookprice.pricing.provider.TaazeParsing.readNewCopy(
                ATOMIC_ISBN, "https://www.taaze.tw/products/1.html", usedOnly);

        assertThat(parsed).isEmpty();
    }

    @Test
    @DisplayName("別本書的 JSON-LD 不會被誤認：ISBN 必須對得上")
    void aBlockForAnotherBookIsIgnored() {
        String otherBook = """
                <script type="application/ld+json">
                {"@type":"Product","isbn":"9789864792917",
                 "offers":{"@type":"Offer","price":"379","priceCurrency":"TWD"}}
                </script>
                """;

        assertThat(tw.bookprice.pricing.provider.KingstoneParsing.parse(
                ATOMIC_ISBN, "https://example.invalid/", otherBook)).isEmpty();
    }

    @Test
    @DisplayName("沒有可用報價的頁面回傳空值，而不是把定價當售價")
    void aPageWithoutAnOfferYieldsNothing() {
        String noOffer = """
                <script type="application/ld+json">
                {"@type":"Book","isbn":"9789861755267","name":"原子習慣"}
                </script>
                """;

        assertThat(tw.bookprice.pricing.provider.TaazeParsing.readNewCopy(
                ATOMIC_ISBN, "https://example.invalid/", noOffer)).isEmpty();
    }
}
