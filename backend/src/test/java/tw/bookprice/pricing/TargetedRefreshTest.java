package tw.bookprice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.pricing.provider.KingstonePriceProvider;
import tw.bookprice.seed.CatalogueSeeder;

/**
 * 找書只為它剛收錄的書取價。
 *
 * A reader who asks for one book should pay for one book. Refreshing the rest of
 * the 書目 on the same click makes a 找書 slower every time the 書目 grows — the
 * per-host pause between requests means the wait is proportional to how many
 * 報價 have gone stale, not to what was asked for.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TargetedRefreshTest {

    private static final String ATOMIC_PAPER = "9789861755267";
    private static final String HABITS_OTHER = "9789861371955";

    @Autowired
    private PriceRefreshService priceRefreshService;

    @Autowired
    private CatalogueSeeder seeder;

    @MockitoBean
    private KingstonePriceProvider kingstone;

    @BeforeEach
    void seedCatalogue() {
        seeder.seed();
        willReturn("KINGSTONE").given(kingstone).channelCode();
        willReturn(Optional.of(new FetchedPrice(199, "有貨", null, null)))
                .given(kingstone).fetch(anyString());
    }

    @Test
    @DisplayName("只取指定 ISBN 的價，其他作品不會被一併重抓")
    void refreshesOnlyTheGivenIsbns() {
        RefreshReport report = priceRefreshService.refreshIsbns(List.of(ATOMIC_PAPER));

        RefreshReport.ChannelResult result = report.channels().stream()
                .filter(entry -> entry.channelCode().equals("KINGSTONE"))
                .findFirst()
                .orElseThrow();

        // One 作品, one 版本, one 報價 at this 通路 — never the whole 書目.
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
    }

    @Test
    @DisplayName("沒有指定任何 ISBN 時什麼都不做，不會退回整份書目")
    void anEmptyRequestTouchesNothing() {
        RefreshReport report = priceRefreshService.refreshIsbns(List.of());

        assertThat(report.channels()).allSatisfy(result -> {
            assertThat(result.updated()).isZero();
            assertThat(result.notFound()).isZero();
            assertThat(result.failed()).isZero();
        });
    }

    @Test
    @DisplayName("指定的 ISBN 不在書目裡也不會誤抓別的書")
    void anUnknownIsbnTouchesNothing() {
        RefreshReport report = priceRefreshService.refreshIsbns(List.of("9789999999999"));

        assertThat(report.channels()).allSatisfy(result ->
                assertThat(result.updated()).isZero());
    }

    @Test
    @DisplayName("指定多本時，每一本都取到，且僅限這幾本")
    void refreshesEachGivenIsbnAndNoOthers() {
        RefreshReport report = priceRefreshService.refreshIsbns(
                List.of(ATOMIC_PAPER, HABITS_OTHER));

        RefreshReport.ChannelResult result = report.channels().stream()
                .filter(entry -> entry.channelCode().equals("KINGSTONE"))
                .findFirst()
                .orElseThrow();

        assertThat(result.updated()).isEqualTo(2);
    }
}
