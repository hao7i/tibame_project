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
 * The 紙本 ISBNs are the real ones from the design prototype; the 電子書 ISBNs are
 * synthesised (the prototype hangs both 載體 off one ISBN, which ADR 0002 rejects).
 * A wrong check digit in a hand-written number would be invisible until someone
 * pasted it into a real bookshop, so the seed is checked here.
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
