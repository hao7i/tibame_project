package tw.bookprice.seed;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.Channel;
import tw.bookprice.catalogue.ChannelRepository;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Format;
import tw.bookprice.catalogue.Offer;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;

/**
 * Loads the six 作品 the design prototype ships with.
 *
 * Every 售價, 庫存 string and 定價 below is copied verbatim from the prototype, so
 * the built screens show the numbers the design was drawn against. Two things
 * differ from the prototype on purpose:
 *
 *   - The prototype hangs 紙本 and 電子書 報價 off a single ISBN. ADR 0002 rejects
 *     that, so each 作品 here gets a 紙本 版本 and, where the prototype has 電子書
 *     報價, a separate 電子書 版本.
 *   - 折扣 is absent. The prototype stores a label per 報價; it is computed from
 *     售價 against 定價 instead, which reproduces every one of those labels.
 *
 * This is data loading, not 商業邏輯, so it writes through the repositories
 * directly rather than through a 商業邏輯 service.
 */
@Component
public class CatalogueSeeder {

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;

    public CatalogueSeeder(WorkRepository workRepository, ChannelRepository channelRepository) {
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * Idempotent: a database that already holds 作品 is left exactly as it is.
     *
     * Called from SeedRunner rather than from this class, so that the proxy
     * applies and the whole seed lands in one transaction.
     */
    @Transactional
    public void seed() {
        // Ahead of the guard on purpose: this both creates the 通路 and backfills
        // fields added since a database was first seeded, and an already-seeded
        // database is precisely the one that needs the backfill.
        Map<String, Channel> channels = seedChannels();

        if (workRepository.count() > 0) {
            return;
        }

        Instant fetchedAt = Instant.now();

        WORKS.stream()
                .map(spec -> toWork(spec, channels, fetchedAt))
                .forEach(workRepository::save);
    }

    /**
     * 通路 that were replaced after databases had already been seeded.
     *
     * 誠品線上 became 三民網路書店 and 博客來 became 五南文化廣場. Both were replaced
     * for the same reason: neither could be priced. 誠品 renders its price only
     * in the browser and disallows /search for everybody; 博客來 blocks this
     * crawler outright in robots.txt, so it can never be more than seed data.
     *
     * 誠品 could not be priced at all — a client-rendered SPA with no price in
     * its HTML, behind a robots.txt that disallows /search for everybody — so
     * leaving it in a live database would mean a 通路 column that can only ever
     * be blank. The row is converted in place rather than deleted and re-created
     * because 報價 point at the row: converting it carries them across, and the
     * first 取價 then corrects the prices to what 三民 actually charges.
     */
    private void migrateReplacedChannels() {
        for (Channel channel : channelRepository.findAll()) {
            if (!ChannelMigration.RETIRED_CODES.contains(channel.getCode())) {
                continue;
            }

            Optional<ChannelSpec> replacement = CHANNELS.stream()
                    .filter(spec -> spec.displayOrder() == channel.getDisplayOrder())
                    .findFirst();

            if (replacement.isPresent()) {
                ChannelSpec spec = replacement.get();
                channel.replaceWith(
                        spec.code(), spec.name(), spec.kind(), spec.searchUrlTemplate());
            } else {
                // Nothing took its slot: the 通路 was dropped, not replaced.
                // Readmoo went this way when 電子書 比價 was removed — it was the
                // last 電子書 通路 and blocks this crawler in robots.txt, so it
                // could never have carried a real price anyway.
                channelRepository.delete(channel);
            }
        }
    }

    /**
     * Drop the 電子書 版本 an earlier seed created.
     *
     * The site compares 紙本 only now. Those 版本 carried ISBNs this project
     * synthesised rather than took from a registry, so they address nothing real
     * and no 取價 could ever answer for them. Removing the 版本 removes its 報價
     * with it, which is what the cascade is for.
     */
    private void removeNonPaperEditions() {
        for (Work work : workRepository.findAllWithOffers()) {
            List<Edition> stale = work.getEditions().stream()
                    .filter(edition -> edition.getFormat() != Format.PAPER)
                    .toList();
            if (!stale.isEmpty()) {
                work.getEditions().removeAll(stale);
                workRepository.save(work);
            }
        }
    }

    /**
     * Give a replaced 通路 the 報價 the seed says it should have.
     *
     * The 報價 of the 通路 it replaced can sit on the wrong 版本 — 樂天Kobo sold
     * 電子書 and 墊腳石 sells 紙本 — and moving a 報價 between 版本 is not safe. The
     * collection is mapped with orphanRemoval, so removing a 報價 from one 版本
     * schedules a delete that adding it to another does not undo; doing exactly
     * that once destroyed four rows. Rows are therefore created where they
     * belong and never moved.
     *
     * Existing rows are left untouched, so this cannot overwrite a 售價 a real
     * 取價 has already corrected, and running it twice changes nothing.
     */
    private void backfillSeededOffers(Channel channel) {
        for (WorkSpec spec : WORKS) {
            for (OfferSpec offerSpec : spec.offers()) {
                if (!offerSpec.channelCode().equals(channel.getCode())) {
                    continue;
                }
                String isbn = spec.paperIsbn();
                addOfferIfMissing(channel, isbn, offerSpec);
            }
        }
    }

    private void addOfferIfMissing(Channel channel, String isbn, OfferSpec offerSpec) {
        workRepository.findByEditionIsbn(isbn).ifPresent(work -> work.getEditions().stream()
                .filter(edition -> edition.getIsbn().equals(isbn))
                .findFirst()
                .ifPresent(edition -> {
                    boolean present = edition.getOffers().stream()
                            .anyMatch(offer -> offer.getChannel().getCode()
                                    .equals(channel.getCode()));
                    if (!present) {
                        edition.addOffer(new Offer(
                                channel, offerSpec.price(), offerSpec.stock(), Instant.now()));
                    }
                }));
    }

    private Map<String, Channel> seedChannels() {
        migrateReplacedChannels();
        removeNonPaperEditions();

        if (channelRepository.count() == 0) {
            channelRepository.saveAll(CHANNELS.stream()
                    .map(spec -> new Channel(spec.code(), spec.name(), spec.kind(),
                            spec.displayOrder(), spec.searchUrlTemplate()))
                    .toList());
        }

        Map<String, Channel> byCode = channelRepository.findAll().stream()
                .collect(Collectors.toMap(Channel::getCode, Function.identity()));

        // Backfill only. A database seeded before 前往購買 existed has 通路 rows
        // with no link; filling the gap keeps them usable without overwriting
        // anything an operator may have edited in the 管理後台.
        for (ChannelSpec spec : CHANNELS) {
            Channel channel = byCode.get(spec.code());
            if (channel == null) {
                continue;
            }
            if (channel.getSearchUrlTemplate() == null) {
                channel.setSearchUrlTemplate(spec.searchUrlTemplate());
            }
            // Also a backfill: a 通路 replacement can leave a 通路 holding fewer
            // 報價 than the seed describes. Adding only what is missing repairs
            // that without touching prices a real 取價 has since corrected.
            backfillSeededOffers(channel);
        }

        return byCode;
    }

    private static Work toWork(WorkSpec spec, Map<String, Channel> channels, Instant fetchedAt) {
        Work work = new Work(spec.title(), spec.author(), spec.publisher(),
                spec.publicationYear(), spec.category(), spec.blurb());

        Edition paper = new Edition(
                spec.paperIsbn(), Format.PAPER, spec.paperLabel(), spec.listPrice());
        work.addEdition(paper);

        for (OfferSpec offer : spec.offers()) {
            Channel channel = channels.get(offer.channelCode());
            if (channel == null) {
                throw new IllegalStateException("報價指向不存在的通路: " + offer.channelCode());
            }
            paper.addOffer(new Offer(channel, offer.price(), offer.stock(), fetchedAt));
        }

        return work;
    }

    private record ChannelSpec(String code, String name, String kind, int displayOrder,
            String searchUrlTemplate) {
    }

    private record OfferSpec(String channelCode, int price, String stock) {
    }

    private record WorkSpec(
            String title, String author, String publisher, int publicationYear,
            String category, int listPrice, String blurb,
            String paperIsbn, String paperLabel,
            List<OfferSpec> offers) {
    }

    private static final String WUNAN = "WUNAN";
    private static final String SANMIN = "SANMIN";
    private static final String KINGSTONE = "KINGSTONE";
    private static final String TAAZE = "TAAZE";
    private static final String TCSB = "TCSB";

    /**
     * 前往購買 targets, per docs/research/book-price-channel-data-sources.md.
     *
     * Five 通路 have an ISBN link that was verified against the live site:
     * 五南文化廣場, 三民網路書店, 金石堂, 讀冊生活 and 樂天Kobo. 五南 is the most
     * direct of them — the ISBN is the product URL itself.
     *
     * Only Readmoo falls back to its site root: ISBN is not a documented search
     * input there and /search/ is disallowed for everyone.
     *
     * 五南文化廣場 replaced 博客來, which blocks this crawler in robots.txt and so
     * could never be more than seed data.
     *
     * 三民網路書店 replaced 誠品線上. 誠品 is a client-rendered SPA that ships
     * no price in its HTML, and its robots.txt disallows /search for everybody,
     * so neither 取價 nor even an ISBN link was reachable. 三民 allows both and
     * is the only 通路 examined with a dedicated ISBN search parameter
     * (ct=isbn), which is what makes its lookup exact rather than a guess among
     * search hits.
     *
     * 票 10 replaces these with real product-page URLs for the 通路 it fetches.
     */
    private static final List<ChannelSpec> CHANNELS = List.of(
            new ChannelSpec(WUNAN, "五南文化廣場", "紙本", 0,
                    "https://www.wunanbooks.com.tw/product.php?isbn={isbn}"),
            new ChannelSpec(SANMIN, "三民網路書店", "紙本", 1,
                    "https://www.sanmin.com.tw/search/?ct=isbn&qu={isbn}"),
            new ChannelSpec(KINGSTONE, "金石堂", "紙本", 2,
                    "https://www.kingstone.com.tw/search/key/{isbn}"),
            new ChannelSpec(TAAZE, "讀冊生活", "紙本", 3,
                    "https://www.taaze.tw/rwd_searchResult.html?keyType%5B%5D=0&keyword%5B%5D={isbn}"),
            new ChannelSpec(TCSB, "墊腳石", "紙本", 4,
                    "https://www.tcsb.com.tw/{isbn}"));

    /**
     * 售價 are 示意資料 copied from the design prototype, and every one of them is
     * replaced the first time 取價 runs against the 通路 that publishes it.
     */
    private static final List<WorkSpec> WORKS = List.of(
            new WorkSpec("原子習慣", "James Clear", "方智", 2019, "心理勵志", 330,
                    "從細微改變累積成長的行為設計方法，說明習慣如何形成、如何替換，以及環境對行為的影響。",
                    "9789861755267", "紙本平裝",
                    List.of(
                            new OfferSpec(WUNAN, 261, "24 小時到貨"),
                            new OfferSpec(SANMIN, 264, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 280, "庫存有限"),
                            new OfferSpec(TAAZE, 271, "3-5 個工作日"),
                            new OfferSpec(TCSB, 261, "有貨"))),

            new WorkSpec("人類大歷史", "Yuval Noah Harari", "天下文化", 2018, "人文史地", 480,
                    "從認知革命到科學革命，重述人類作為一個物種如何改變地球與自身的敘事。",
                    // The prototype writes 9789864792916, whose check digit is wrong;
                    // corrected to 7 so every ISBN in the catalogue is a valid ISBN-13.
                    "9789864792917", "紙本精裝",
                    List.of(
                            new OfferSpec(WUNAN, 379, "24 小時到貨"),
                            new OfferSpec(SANMIN, 384, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 408, "現貨"),
                            new OfferSpec(TAAZE, 394, "3-5 個工作日"))),

            new WorkSpec("被討厭的勇氣", "岸見一郎、古賀史健", "究竟", 2014, "心理勵志", 300,
                    "以對話形式介紹阿德勒心理學的核心概念：課題分離、目的論與人際關係的距離。",
                    "9789861371955", "紙本平裝",
                    List.of(
                            new OfferSpec(WUNAN, 237, "24 小時到貨"),
                            new OfferSpec(SANMIN, 240, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 255, "現貨"),
                            new OfferSpec(TAAZE, 246, "3-5 個工作日"),
                            new OfferSpec(TCSB, 237, "有貨"))),

            new WorkSpec("正義：一場思辨之旅", "Michael J. Sandel", "先覺", 2018, "人文史地", 420,
                    "以電車難題等案例貫穿功利主義、自由至上主義與德性論的論證與彼此的衝突。",
                    "9789861343181", "紙本平裝",
                    List.of(
                            new OfferSpec(WUNAN, 332, "24 小時到貨"),
                            new OfferSpec(SANMIN, 336, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 357, "訂購後 5 日"),
                            new OfferSpec(TAAZE, 344, "3-5 個工作日"))),

            new WorkSpec("如何閱讀一本書", "Mortimer J. Adler", "台灣商務", 2003, "人文史地", 500,
                    "將閱讀分為四個層次，說明檢視閱讀與分析閱讀的具體步驟與筆記方法。",
                    "9789570517989", "紙本平裝",
                    List.of(
                            new OfferSpec(WUNAN, 395, "7 日內到貨"),
                            new OfferSpec(SANMIN, 400, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 425, "現貨"),
                            new OfferSpec(TAAZE, 410, "3-5 個工作日"))),

            new WorkSpec("設計的設計", "原研哉", "磐築創意", 2011, "藝術設計", 600,
                    "以 RE-DESIGN 展覽為軸，討論設計如何從既有事物中重新發現使用的本質。",
                    "9789866637155", "紙本平裝",
                    List.of(
                            new OfferSpec(WUNAN, 504, "7 日內到貨"),
                            new OfferSpec(SANMIN, 510, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, 540, "訂購後 5 日"),
                            new OfferSpec(TAAZE, 492, "3-5 個工作日"))));
}
