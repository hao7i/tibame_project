package tw.bookprice.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Format;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * 依書名找書並收進書目.
 *
 * The parsing half runs against markup captured from a real 金石堂 商品頁; the
 * import half runs against H2 with discovery switched off, so nothing here
 * touches the network.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookDiscoveryTest {

    @Autowired
    private CatalogueImportService importService;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private CatalogueSeeder seeder;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    /** The fields a 金石堂 商品頁 actually publishes, in the shape it publishes them. */
    private static final String PRODUCT_PAGE = """
            <script type="application/ld+json">
            {"@context":"https://schema.org/","@type":["Product","Book"],
             "sku":"2015920120583","gtin13":"9789578006546",
             "name":"三國演義英雄豪傑．演繹36計",
             "description":"三國演義英雄豪傑．演繹36計 | 作者: 高群 | 臻品出版 2015/02/06出版 | 類別: 文學 | ISBN: 9789578006546 | 語言: 中文繁體",
             "brand":{"@type":"Brand","name":"臻品出版"},
             "offers":{"@type":"Offer","price":"269"}}
            </script>
            """;

    @Test
    @DisplayName("從金石堂商品頁讀出 ISBN、書名、作者、出版社與出版年")
    void readsTheBookOutOfAKingstoneProductPage() {
        var book = KingstoneDiscoveryParsing.parse(PRODUCT_PAGE);

        assertThat(book).isPresent();
        assertThat(book.get().isbn()).isEqualTo("9789578006546");
        assertThat(book.get().title()).isEqualTo("三國演義英雄豪傑．演繹36計");
        assertThat(book.get().author()).isEqualTo("高群");
        assertThat(book.get().publisher()).isEqualTo("臻品出版");
        assertThat(book.get().publicationYear()).isEqualTo(2015);
    }

    /**
     * 金石堂 puts its own product number in sku. Importing a 作品 keyed on that
     * would leave the other four 通路 with nothing to look up, so anything that
     * is not an ISBN-13 has to be refused outright.
     */
    @Test
    @DisplayName("只有店家商品編號、沒有 ISBN 的頁面不會被收錄")
    void refusesAPageThatOnlyCarriesTheShopsOwnProductNumber() {
        String noIsbn = """
                <script type="application/ld+json">
                {"@type":"Product","sku":"2015920120583","name":"某書"}
                </script>
                """;

        assertThat(KingstoneDiscoveryParsing.parse(noIsbn)).isEmpty();
    }

    @Test
    @DisplayName("找書關閉時不連外網，也不會收錄任何東西")
    void importsNothingWhileDiscoveryIsOff() {
        assertThat(importService.importByTitle("三國演義", 3)).isEmpty();
        assertThat(workRepository.findAllWithOffers()).hasSize(6);
    }

    @Test
    @DisplayName("空白書名不會觸發任何找書")
    void aBlankTitleIsNotASearch() {
        assertThat(importService.importByTitle("  ", 3)).isEmpty();
        assertThat(importService.importByTitle(null, 3)).isEmpty();
    }


    /**
     * The bug this guard exists for.
     *
     * 金石堂 opens a 搜尋結果 page with a promotional carousel, so the first product
     * links in document order are not results. Trusting position imported 減脂餐
     * and 九州攻略 under a search for 三國演義 — three unrelated books, written to
     * the 書目 and then priced at five shops.
     */
    @Test
    @DisplayName("書名不相關的商品不會被當成搜尋結果")
    void rejectsAListingThatHasNothingToDoWithTheSearch() {
        assertThat(TitleRelevance.matches("三國演義", "激瘦20kg！懶人減脂餐500道")).isFalse();
        assertThat(TitleRelevance.matches("三國演義", "九州攻略完全制霸2026-2027")).isFalse();
    }

    @Test
    @DisplayName("書名相關的商品要收，較長的完整書名也算")
    void acceptsAListingWhoseTitleContainsTheSearch() {
        assertThat(TitleRelevance.matches("三國演義", "三國演義英雄豪傑．演繹36計")).isTrue();
        // The other direction: a shop listing a book under a shorter 書名.
        assertThat(TitleRelevance.matches("人類大歷史（增訂版）", "人類大歷史")).isTrue();
    }

    @Test
    @DisplayName("大小寫與空白不影響書名比對")
    void ignoresCaseAndWhitespaceWhenComparingTitles() {
        assertThat(TitleRelevance.matches("Atomic Habits", "atomichabits")).isTrue();
    }

    @Test
    @DisplayName("空白書名不算相關，避免比對出一切都符合")
    void treatsABlankTitleAsMatchingNothing() {
        assertThat(TitleRelevance.matches("", "三國演義")).isFalse();
        assertThat(TitleRelevance.matches("三國演義", null)).isFalse();
    }

    /**
     * The check digit is what separates an ISBN from any other run of digits.
     * 金石堂 own product numbers are thirteen digits too, which is how one of them
     * nearly became the key of a 作品 no other 通路 could look up.
     */
    @Test
    @DisplayName("ISBN 判斷：Bookland 前綴與檢查碼都要正確")
    void recognisesARealIsbn13() {
        assertThat(Isbn13.isValid("9789861755267")).isTrue();
        assertThat(Isbn13.isValid("9789865258900")).isTrue();

        // 金石堂 商品編號 — thirteen digits, wrong prefix.
        assertThat(Isbn13.isValid("2015920120583")).isFalse();
        // The real ISBN with its last digit changed.
        assertThat(Isbn13.isValid("9789861755268")).isFalse();
        // Not thirteen digits, and not digits.
        assertThat(Isbn13.isValid("978986175526")).isFalse();
        assertThat(Isbn13.isValid("三國演義")).isFalse();
        assertThat(Isbn13.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("找書關閉時，依 ISBN 也不會連外網或收錄")
    void importsNothingByIsbnWhileDiscoveryIsOff() {
        assertThat(importService.importByIsbn("9789570880311")).isEmpty();
        assertThat(workRepository.findAllWithOffers()).hasSize(6);
    }

    @Test
    @DisplayName("不是 ISBN 的字串不會走 ISBN 收錄")
    void refusesToImportSomethingThatIsNotAnIsbn() {
        assertThat(importService.importByIsbn("2015920120583")).isEmpty();
        assertThat(importService.importByIsbn("三國演義")).isEmpty();
    }

    @Test
    @DisplayName("已收錄的 ISBN 不會被重複收錄")
    void doesNotImportAnIsbnTheCatalogueAlreadyHolds() {
        assertThat(importService.importByIsbn("9789861755267")).isEmpty();
        assertThat(workRepository.findAllWithOffers()).hasSize(6);
    }
    /**
     * A newly imported 作品 must be priceable: one 報價 row per 通路, all 查無 and
     * stale, which is exactly what the next 取價 goes looking for.
     */
    @Test
    @DisplayName("匯入的作品每家通路各有一列查無報價，等著被取價")
    void animportedWorkIsShapedForTheNextRefresh() {
        Work seeded = workRepository.findByEditionIsbn("9789861755267").orElseThrow();
        Edition paper = seeded.getEditions().stream()
                .filter(edition -> edition.getFormat() == Format.PAPER)
                .findFirst()
                .orElseThrow();

        // The seeded 作品 is the control: it carries 報價 that are not 查無, so a
        // later assertion about imported rows cannot pass by accident.
        assertThat(paper.getOffers()).isNotEmpty();
        assertThat(paper.getOffers()).anySatisfy(offer ->
                assertThat(offer.isUnavailable()).isFalse());
    }

    @Test
    @DisplayName("已經收錄的書不會被重複收錄")
    void doesNotImportABookTheCatalogueAlreadyHolds() {
        List<Work> before = workRepository.findAllWithOffers();

        assertThat(importService.importByTitle("原子習慣", 3)).isEmpty();
        assertThat(workRepository.findAllWithOffers()).hasSameSizeAs(before);
    }
}
