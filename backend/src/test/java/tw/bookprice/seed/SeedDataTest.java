package tw.bookprice.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Format;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;

/**
 * The 電子書 ISBNs are synthesised (the prototype hangs both 載體 off one ISBN,
 * which ADR 0002 rejects). The 紙本 ones are real, but two of them named the
 * wrong book until they were corrected — see CatalogueSeeder.CORRECTED_ISBNS.
 *
 * That is why the check-digit test below is not enough on its own and never was:
 * a valid check digit says the number is well formed, not that it is this book.
 * The mapping test is the one that would have caught it.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedDataTest {

    @Autowired
    private CatalogueSeeder seeder;

    @Autowired
    private WorkRepository workRepository;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
    }

    @Test
    @DisplayName("seed 的每一個 ISBN 都是合法的 ISBN-13")
    void everySeededIsbnHasAValidCheckDigit() {
        List<Work> works = workRepository.findAllWithOffers();

        assertThat(works).hasSize(6);
        assertThat(works)
                .flatExtracting(Work::getEditions)
                .isNotEmpty()
                .allSatisfy(edition -> assertThat(isValidIsbn13(edition.getIsbn()))
                        .as("ISBN-13 檢查碼 %s", edition.getIsbn())
                        .isTrue());
    }


    @Test
    @DisplayName("沒有電子書報價的作品不會被硬生出一個電子書版本")
    void worksWithoutEbookOffersGetNoEbookEdition() {
        Work designOfDesign = workRepository.findByEditionIsbn("9789866637155").orElseThrow();

        assertThat(designOfDesign.getEditions())
                .extracting(Edition::getFormat)
                .containsExactly(Format.PAPER);
    }

    @Test
    @DisplayName("重複執行 seed 不會產生重複資料")
    void seedingTwiceLeavesTheCatalogueUnchanged() {
        seeder.seed();
        seeder.seed();

        assertThat(workRepository.findAllWithOffers()).hasSize(6);
    }


    /**
     * Pins each 作品 to its ISBN.
     *
     * Two of these were wrong — the ISBN named a real but different book, so
     * 取價 happily filled the catalogue with 翻轉賽局's and 大師們的寫作課's prices,
     * covers and 商品頁 links. Nothing in the suite noticed, because every other
     * assertion here is about the shape of the number rather than what it names.
     *
     * These cannot be verified against a real bookshop from a test, so they are
     * pinned instead: repointing a 作品 at another ISBN now has to be a visible,
     * deliberate edit to this list.
     */
    @Test
    @DisplayName("每個作品的紙本 ISBN 就是查證過的那一個")
    void everyWorkKeepsTheIsbnThatWasVerified() {
        assertThat(workRepository.findAllWithOffers())
                .flatExtracting(Work::getEditions)
                .filteredOn(edition -> edition.getFormat() == Format.PAPER)
                .extracting(edition -> edition.getWork().getTitle(), Edition::getIsbn)
                .containsExactlyInAnyOrder(
                        tuple("原子習慣", "9789861755267"),
                        tuple("人類大歷史", "9789865258900"),
                        tuple("被討厭的勇氣", "9789861371955"),
                        tuple("正義：一場思辨之旅", "9789861343280"),
                        tuple("如何閱讀一本書", "9789570517989"),
                        tuple("設計的設計", "9789866637155"));
    }

    /**
     * The two numbers that used to be here name other books. Keeping them in a
     * test is the cheapest way to stop one being pasted back in.
     */
    @Test
    @DisplayName("被更正掉的兩個 ISBN 不會再出現在型錄裡")
    void theCorrectedIsbnsAreGoneForGood() {
        assertThat(workRepository.findAllWithOffers())
                .flatExtracting(Work::getEditions)
                .extracting(Edition::getIsbn)
                .doesNotContain("9789864792917", "9789861343181");
    }
    /** Weights 1 and 3 alternating over the first 12 digits; 13th is the check digit. */
    private static boolean isValidIsbn13(String isbn) {
        if (isbn == null || !isbn.matches("\\d{13}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == isbn.charAt(12) - '0';
    }
}
