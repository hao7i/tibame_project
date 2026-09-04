package tw.bookprice.catalogue;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.dto.BestPrice;
import tw.bookprice.catalogue.dto.ChannelPrice;
import tw.bookprice.catalogue.dto.ChannelView;
import tw.bookprice.catalogue.dto.OfferView;
import tw.bookprice.catalogue.dto.SearchResponse;
import tw.bookprice.catalogue.dto.WorkDetail;
import tw.bookprice.catalogue.dto.WorkSummary;

/**
 * The one place 搜尋 and 最低價 are decided, and the only layer that touches
 * entities: everything above it sees DTOs.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;

    public CatalogueService(WorkRepository workRepository, ChannelRepository channelRepository) {
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * 搜尋 by 書名, 作者, 出版社 or ISBN, optionally narrowed to one 載體.
     *
     * A blank query means 全部收錄書籍 rather than no results. A query that matches
     * nothing yields an empty set: the design prototype falls back to the whole
     * catalogue there, but the real site owes the reader an empty state instead
     * of a silently wrong list.
     *
     * @param query  搜尋 term, or null/blank for 全部收錄書籍
     * @param format 載體 to narrow to (PAPER or EBOOK), or null/blank for 全部版本
     */
    public SearchResponse search(String query, String format) {
        Format loadFilter = parseFormat(format);

        String term = (query == null) ? null : query.trim();
        boolean searching = term != null && !term.isEmpty();

        List<Work> works = searching
                ? workRepository.search(likePattern(term))
                : workRepository.findAllWithOffers();

        List<WorkSummary> summaries = works.stream()
                .map(work -> toSummary(work, loadFilter))
                .flatMap(Optional::stream)
                .toList();

        return new SearchResponse(
                searching ? term : null,
                summaries.size(),
                latestFetchedAt(works, loadFilter),
                summaries);
    }

    /**
     * One 作品 with every 通路 報價, for 單書比價.
     *
     * Either 版本 ISBN addresses the 作品; the response always identifies it by the
     * 紙本 one so the URL a reader ends up sharing is the canonical form.
     *
     * @param isbn   ISBN of any 版本 of the 作品
     * @param format 載體 to narrow to, or null/blank for 全部版本
     * @param sort   PRICE (default) or CHANNEL
     * @throws java.util.NoSuchElementException when no 作品 carries that ISBN
     */
    public WorkDetail findWork(String isbn, String format, String sort) {
        Format loadFilter = parseFormat(format);
        OfferSort order = parseSort(sort);

        Work work = workRepository.findByEditionIsbn(isbn)
                .orElseThrow(() -> new NoSuchElementException("找不到 ISBN 為 " + isbn + " 的作品"));

        List<Offer> offers = offersOf(work, loadFilter);
        Offer cheapest = offers.stream().min(Comparator.comparingInt(Offer::getPrice)).orElse(null);
        int listPrice = listPriceOf(work);

        List<OfferView> rows = offers.stream()
                .sorted(comparatorFor(order))
                .map(offer -> toOfferView(offer, offer == cheapest))
                .toList();

        long channelCount = offers.stream()
                .map(offer -> offer.getChannel().getId())
                .distinct()
                .count();

        return new WorkDetail(
                work.getPrimaryIsbn(),
                work.getTitle(),
                work.getAuthor(),
                work.getPublisher(),
                work.getPublicationYear(),
                work.getCategory(),
                work.getBlurb(),
                listPrice,
                (int) channelCount,
                offers.stream().map(Offer::getFetchedAt).max(Comparator.naturalOrder()).orElse(null),
                (cheapest == null) ? null : toBestPrice(cheapest, listPrice),
                rows);
    }

    /** 價格低→高 breaks ties on 通路 order, so the table never reshuffles. */
    private static Comparator<Offer> comparatorFor(OfferSort order) {
        Comparator<Offer> byChannel =
                Comparator.comparingInt(offer -> offer.getChannel().getDisplayOrder());
        return (order == OfferSort.CHANNEL)
                ? byChannel
                : Comparator.comparingInt(Offer::getPrice).thenComparing(byChannel);
    }

    private static OfferView toOfferView(Offer offer, boolean best) {
        Edition edition = offer.getEdition();
        int percent = discountPercent(offer.getPrice(), edition.getListPrice());

        return new OfferView(
                offer.getChannel().getName(),
                offer.getChannel().getCode(),
                edition.getFormat(),
                edition.getFormatLabel(),
                offer.getStockStatus(),
                offer.getPrice(),
                discountLabel(percent),
                offer.getChannel().purchaseUrlFor(edition.getIsbn()),
                best);
    }

    /** Blank means 價格低→高; anything else has to name a known order. */
    private static OfferSort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return OfferSort.PRICE;
        }
        try {
            return OfferSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException("不支援的排序方式: " + sort, cause);
        }
    }

    /** The six 通路, in the order every screen presents them. */
    public List<ChannelView> listChannels() {
        return channelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(channel -> new ChannelView(
                        channel.getCode(), channel.getName(), channel.getKind()))
                .toList();
    }

    /**
     * A 搜尋 term is text a reader typed, not a pattern: the LIKE wildcards have
     * to be neutralised or 「_」 matches every 書名 and the screen reports a full
     * catalogue as the result for it. "[" is only special to SQL Server, and is
     * escaped too so both engines behave alike.
     */
    private static String likePattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                .replace("[", "![");
        return "%" + escaped + "%";
    }

    /** Blank means 全部版本; anything else has to name a 載體. */
    private static Format parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return null;
        }
        try {
            return Format.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException("不支援的載體: " + format, cause);
        }
    }

    /**
     * A 作品 with no 報價 under the active 載體 is left out of the result set rather
     * than listed with a blank price — the same rule the design prototype applies.
     */
    private static Optional<WorkSummary> toSummary(Work work, Format loadFilter) {
        List<Offer> offers = offersOf(work, loadFilter);
        if (offers.isEmpty()) {
            return Optional.empty();
        }

        int listPrice = listPriceOf(work);

        List<ChannelPrice> channelPrices = offers.stream()
                .map(offer -> new ChannelPrice(
                        offer.getChannel().getName(),
                        offer.getChannel().getCode(),
                        offer.getPrice(),
                        offer.getEdition().getFormat()))
                .toList();

        BestPrice bestPrice = offers.stream()
                .min(Comparator.comparingInt(Offer::getPrice))
                .map(offer -> toBestPrice(offer, listPrice))
                .orElseThrow();

        long channelCount = offers.stream()
                .map(offer -> offer.getChannel().getId())
                .distinct()
                .count();

        return Optional.of(new WorkSummary(
                work.getPrimaryIsbn(),
                work.getTitle(),
                work.getAuthor(),
                work.getPublisher(),
                work.getPublicationYear(),
                work.getCategory(),
                listPrice,
                (int) channelCount,
                bestPrice,
                channelPrices));
    }

    /** Every 報價 of every matching 版本 of one 作品, in fixed 通路 order. */
    private static List<Offer> offersOf(Work work, Format loadFilter) {
        return work.getEditions().stream()
                .filter(edition -> loadFilter == null || edition.getFormat() == loadFilter)
                .flatMap(edition -> edition.getOffers().stream())
                .sorted(Comparator.comparingInt(offer -> offer.getChannel().getDisplayOrder()))
                .toList();
    }

    /**
     * 定價 of the 紙本 版本 — the figure the results screen shows as its 定價 tag.
     * It stays the 作品-level number whatever 載體 is filtered to; each 折扣 is
     * measured against the 定價 of its own 版本 instead.
     */
    private static int listPriceOf(Work work) {
        return work.getEditions().stream()
                .filter(edition -> edition.getFormat() == Format.PAPER)
                .findFirst()
                .or(() -> work.getEditions().stream().findFirst())
                .map(Edition::getListPrice)
                .orElse(0);
    }

    /**
     * 折扣 is measured against the 定價 of the 版本 the 報價 belongs to, while
     * 較定價省 is measured against the 作品 定價 the screen displays — so the two
     * figures on the card agree with the 定價 tag beside them.
     */
    private static BestPrice toBestPrice(Offer offer, int workListPrice) {
        Edition edition = offer.getEdition();
        int percent = discountPercent(offer.getPrice(), edition.getListPrice());

        return new BestPrice(
                offer.getChannel().getName(),
                offer.getChannel().getCode(),
                offer.getPrice(),
                percent,
                discountLabel(percent),
                Math.max(0, workListPrice - offer.getPrice()),
                offer.getChannel().purchaseUrlFor(edition.getIsbn()));
    }

    static int discountPercent(int price, int listPrice) {
        if (listPrice <= 0) {
            return 0;
        }
        return (int) Math.round(price * 100.0 / listPrice);
    }

    /**
     * 79 percent of 定價 reads 79 折; a round 70 percent reads 7 折.
     *
     * Null when there is no 折扣 to state: a 售價 at or above 定價, or a 版本 whose
     * 定價 is unknown. Both turn up once real 取價 replaces seed data, and writing
     * them as 「105 折」 or 「0 折」 would announce a discount that does not exist.
     */
    static String discountLabel(int percent) {
        if (percent <= 0 || percent >= 100) {
            return null;
        }
        return (percent % 10 == 0 ? percent / 10 : percent) + " 折";
    }

    private static Instant latestFetchedAt(List<Work> works, Format loadFilter) {
        return works.stream()
                .flatMap(work -> offersOf(work, loadFilter).stream())
                .map(Offer::getFetchedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
