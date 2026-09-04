package tw.bookprice.seed;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    private Map<String, Channel> seedChannels() {
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
            if (channel != null && channel.getSearchUrlTemplate() == null) {
                channel.setSearchUrlTemplate(spec.searchUrlTemplate());
            }
        }

        return byCode;
    }

    private static Work toWork(WorkSpec spec, Map<String, Channel> channels, Instant fetchedAt) {
        Work work = new Work(spec.title(), spec.author(), spec.publisher(),
                spec.publicationYear(), spec.category(), spec.blurb());

        Edition paper = new Edition(
                spec.paperIsbn(), Format.PAPER, spec.paperLabel(), spec.listPrice());
        work.addEdition(paper);

        // The 電子書 版本 keeps the 定價 of the 紙本 one: the prototype computes its
        // 電子書 折扣 against that same figure, and copying it keeps those labels right.
        Edition ebook = null;
        if (spec.ebookIsbn() != null) {
            ebook = new Edition(
                    spec.ebookIsbn(), Format.EBOOK, spec.ebookLabel(), spec.listPrice());
            work.addEdition(ebook);
        }

        for (OfferSpec offer : spec.offers()) {
            Edition edition = (offer.format() == Format.EBOOK) ? ebook : paper;
            if (edition == null) {
                throw new IllegalStateException(
                        "報價指向不存在的版本: " + spec.title() + " / " + offer.channelCode());
            }
            Channel channel = channels.get(offer.channelCode());
            if (channel == null) {
                throw new IllegalStateException("報價指向不存在的通路: " + offer.channelCode());
            }
            edition.addOffer(new Offer(channel, offer.price(), offer.stock(), fetchedAt));
        }

        return work;
    }

    private record ChannelSpec(String code, String name, String kind, int displayOrder,
            String searchUrlTemplate) {
    }

    private record OfferSpec(String channelCode, Format format, int price, String stock) {
    }

    private record WorkSpec(
            String title, String author, String publisher, int publicationYear,
            String category, int listPrice, String blurb,
            String paperIsbn, String paperLabel,
            String ebookIsbn, String ebookLabel,
            List<OfferSpec> offers) {
    }

    private static final String BOOKS_TW = "BOOKS_TW";
    private static final String ESLITE = "ESLITE";
    private static final String KINGSTONE = "KINGSTONE";
    private static final String TAAZE = "TAAZE";
    private static final String KOBO = "KOBO";
    private static final String READMOO = "READMOO";

    /**
     * 前往購買 targets, per docs/research/book-price-channel-data-sources.md.
     *
     * Three 通路 have an ISBN search URL the research established and this build
     * re-checked (all 200): 金石堂, 讀冊生活 and 樂天Kobo. The other three get their
     * site root instead of a guessed link:
     *
     *   - 博客來: the search host is robots-Allowed but the query parameter name
     *     is recorded as Not established, and the research declined to guess it.
     *     One browser visit by a human settles it.
     *   - 誠品線上: robots.txt disallows /search for every user agent.
     *   - Readmoo: ISBN is not a documented search input and /search/ is
     *     disallowed for everyone.
     *
     * 票 10 replaces these with real product-page URLs for the 通路 it fetches.
     */
    private static final List<ChannelSpec> CHANNELS = List.of(
            new ChannelSpec(BOOKS_TW, "博客來", "紙本 / 電子書", 0,
                    "https://www.books.com.tw/"),
            new ChannelSpec(ESLITE, "誠品線上", "紙本 / 電子書", 1,
                    "https://www.eslite.com/"),
            new ChannelSpec(KINGSTONE, "金石堂", "紙本", 2,
                    "https://www.kingstone.com.tw/search/key/{isbn}"),
            new ChannelSpec(TAAZE, "讀冊生活", "紙本", 3,
                    "https://www.taaze.tw/rwd_searchResult.html?keyType%5B%5D=0&keyword%5B%5D={isbn}"),
            new ChannelSpec(KOBO, "樂天Kobo", "電子書", 4,
                    "https://www.kobo.com/tw/zh/search?query={isbn}"),
            new ChannelSpec(READMOO, "Readmoo", "電子書", 5,
                    "https://readmoo.com/"));

    private static final String EBOOK_LABEL = "電子書 EPUB";

    /**
     * The 電子書 ISBNs are synthesised, since the prototype has none: the 12th digit
     * of the 紙本 ISBN is stepped by one and the check digit recomputed. They are
     * valid ISBN-13 numbers but they are not real registrations, which is of a
     * piece with the 售價 above them being 示意資料. SeedDataTest checks the digits.
     */
    private static final List<WorkSpec> WORKS = List.of(
            new WorkSpec("原子習慣", "James Clear", "方智", 2019, "心理勵志", 330,
                    "從細微改變累積成長的行為設計方法，說明習慣如何形成、如何替換，以及環境對行為的影響。",
                    "9789861755267", "紙本平裝",
                    "9789861755274", EBOOK_LABEL,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 261, "24 小時到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 264, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 280, "庫存有限"),
                            new OfferSpec(TAAZE, Format.PAPER, 271, "3-5 個工作日"),
                            new OfferSpec(KOBO, Format.EBOOK, 231, "立即下載"),
                            new OfferSpec(READMOO, Format.EBOOK, 238, "立即下載"))),

            new WorkSpec("人類大歷史", "Yuval Noah Harari", "天下文化", 2018, "人文史地", 480,
                    "從認知革命到科學革命，重述人類作為一個物種如何改變地球與自身的敘事。",
                    // The prototype writes 9789864792916, whose check digit is wrong;
                    // corrected to 7 so every ISBN in the catalogue is a valid ISBN-13.
                    "9789864792917", "紙本精裝",
                    "9789864792924", EBOOK_LABEL,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 379, "24 小時到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 384, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 408, "現貨"),
                            new OfferSpec(TAAZE, Format.PAPER, 394, "3-5 個工作日"),
                            new OfferSpec(KOBO, Format.EBOOK, 336, "立即下載"),
                            new OfferSpec(READMOO, Format.EBOOK, 340, "立即下載"))),

            new WorkSpec("被討厭的勇氣", "岸見一郎、古賀史健", "究竟", 2014, "心理勵志", 300,
                    "以對話形式介紹阿德勒心理學的核心概念：課題分離、目的論與人際關係的距離。",
                    "9789861371955", "紙本平裝",
                    "9789861371962", EBOOK_LABEL,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 237, "24 小時到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 240, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 255, "現貨"),
                            new OfferSpec(TAAZE, Format.PAPER, 246, "3-5 個工作日"),
                            new OfferSpec(KOBO, Format.EBOOK, 210, "立即下載"),
                            new OfferSpec(READMOO, Format.EBOOK, 213, "立即下載"))),

            new WorkSpec("正義：一場思辨之旅", "Michael J. Sandel", "先覺", 2018, "人文史地", 420,
                    "以電車難題等案例貫穿功利主義、自由至上主義與德性論的論證與彼此的衝突。",
                    "9789861343181", "紙本平裝",
                    "9789861343198", EBOOK_LABEL,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 332, "24 小時到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 336, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 357, "訂購後 5 日"),
                            new OfferSpec(TAAZE, Format.PAPER, 344, "3-5 個工作日"),
                            new OfferSpec(KOBO, Format.EBOOK, 294, "立即下載"),
                            new OfferSpec(READMOO, Format.EBOOK, 298, "立即下載"))),

            new WorkSpec("如何閱讀一本書", "Mortimer J. Adler", "台灣商務", 2003, "人文史地", 500,
                    "將閱讀分為四個層次，說明檢視閱讀與分析閱讀的具體步驟與筆記方法。",
                    "9789570517989", "紙本平裝",
                    "9789570517996", EBOOK_LABEL,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 395, "7 日內到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 400, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 425, "現貨"),
                            new OfferSpec(TAAZE, Format.PAPER, 410, "3-5 個工作日"),
                            new OfferSpec(READMOO, Format.EBOOK, 350, "立即下載"))),

            // No 電子書 報價 in the prototype, so no 電子書 版本 is invented for it.
            new WorkSpec("設計的設計", "原研哉", "磐築創意", 2011, "藝術設計", 600,
                    "以 RE-DESIGN 展覽為軸，討論設計如何從既有事物中重新發現使用的本質。",
                    "9789866637155", "紙本平裝",
                    null, null,
                    List.of(
                            new OfferSpec(BOOKS_TW, Format.PAPER, 504, "7 日內到貨"),
                            new OfferSpec(ESLITE, Format.PAPER, 510, "3-5 個工作日"),
                            new OfferSpec(KINGSTONE, Format.PAPER, 540, "訂購後 5 日"),
                            new OfferSpec(TAAZE, Format.PAPER, 492, "3-5 個工作日"))));
}
