package tw.bookprice.catalogue;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.dto.BestPrice;
import tw.bookprice.catalogue.dto.ChannelPrice;
import tw.bookprice.catalogue.dto.ChannelView;
import tw.bookprice.catalogue.dto.FacetsView;
import tw.bookprice.catalogue.dto.FieldQuery;
import tw.bookprice.catalogue.dto.OfferView;
import tw.bookprice.catalogue.dto.SearchResponse;
import tw.bookprice.catalogue.dto.WorkDetail;
import tw.bookprice.catalogue.dto.WorkSearchQuery;
import tw.bookprice.catalogue.dto.WorkSummary;

/**
 * The one place 搜尋 and 最低價 are decided, and the only layer that touches
 * entities: everything above it sees DTOs.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    /** 分頁 size is the server decision, not the caller one. */
    static final int PAGE_SIZE = 4;

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;

    public CatalogueService(WorkRepository workRepository, ChannelRepository channelRepository) {
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * 搜尋 by 書名, 作者, 出版社 or ISBN, narrowed by 載體, 通路, 分類 and 價格上限.
     *
     * A blank query means 全部收錄書籍 rather than no results. A query that matches
     * nothing yields an empty set: the design prototype falls back to the whole
     * catalogue there, but the real site owes the reader an empty state instead
     * of a silently wrong list.
     *
     * 通路 and 分類 are matched as data, not as closed enums, so an unrecognised
     * value simply matches nothing rather than failing the request — a facet a
     * 管理後台 removed should not turn every old bookmark into an error.
     */
    public SearchResponse search(WorkSearchQuery query) {
        Format loadFilter = parseFormat(query.format());
        Set<String> channels = codeSet(query.channels());
        Set<String> categories = nameSet(query.categories());

        String term = (query.query() == null) ? null : query.query().trim();
        boolean searching = term != null && !term.isEmpty();

        List<Work> works = (searching
                ? workRepository.search(likePattern(term))
                : workRepository.findAllWithOffers())
                .stream()
                .filter(work -> categories.isEmpty() || categories.contains(work.getCategory()))
                .filter(work -> matchesYear(work, query.year()))
                .filter(work -> matchesFields(work, query.fields()))
                .toList();

        // The 作品 is kept alongside its summary so 取價時間 can be derived from
        // exactly the 作品 that survived every filter, rather than from the wider
        // set — they carry one timestamp today, but not once 取價 is real.
        record Matched(Work work, WorkSummary summary) {
        }

        List<Matched> matched = works.stream()
                .map(work -> toSummary(work, loadFilter, channels)
                        .map(summary -> new Matched(work, summary)))
                .flatMap(Optional::stream)
                // The 價格區間 applies to the computed 最低價, so it moves with the
                // 通路 selection rather than measuring against a hidden price.
                .filter(entry -> withinPrice(entry.summary().bestPrice().price(),
                        query.minPrice(), query.maxPrice()))
                .toList();

        int totalPages = (int) Math.ceil(matched.size() / (double) PAGE_SIZE);
        int page = clampPage(query.page(), totalPages);

        List<WorkSummary> pageOfWorks = matched.stream()
                .skip((long) (page - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .map(Matched::summary)
                .toList();

        return new SearchResponse(
                searching ? term : null,
                matched.size(),
                page,
                PAGE_SIZE,
                totalPages,
                latestFetchedAt(matched.stream().map(Matched::work).toList(),
                        loadFilter, channels),
                pageOfWorks);
    }

    /**
     * The 篩選條件 options with catalogue-wide counts, scoped to one 載體.
     *
     * 分類 is derived from the 書目 rather than hard-coded, and keeps the order the
     * 作品 are stored in, which is the order the design lists them. Every option
     * the 書目 knows stays on the rail whatever the 載體; only its count moves.
     *
     * @param format 載體 to count under, or null/blank to count every 版本
     */
    public FacetsView listFacets(String format) {
        Format loadFilter = parseFormat(format);

        List<Work> catalogue = workRepository.findAllWithOffers();

        // Counts stay catalogue-wide across 搜尋 and across the other facets, but
        // they do respect 載體: a 紙本-only 通路 advertising a count while 電子書 is
        // selected is a dead end, since ticking it can only ever return nothing.
        List<Work> works = catalogue.stream()
                .filter(work -> !offersOf(work, loadFilter, Set.of()).isEmpty())
                .toList();

        List<FacetsView.ChannelFacet> channels =
                channelRepository.findAllByOrderByDisplayOrderAsc().stream()
                        .map(channel -> new FacetsView.ChannelFacet(
                                channel.getCode(),
                                channel.getName(),
                                (int) works.stream()
                                        .filter(work -> carriesChannel(
                                                work, channel.getCode(), loadFilter))
                                        .count()))
                        .toList();

        // Keyed off the whole 書目, not off the 載體-narrowed set: an option that
        // disappears makes the group jump as the reader switches 載體, and 0 是
        // 誠實的答案, whereas a missing row reads as though the 分類 never existed.
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Work work : catalogue) {
            boolean sellable = !offersOf(work, loadFilter, Set.of()).isEmpty();
            byCategory.merge(work.getCategory(), sellable ? 1 : 0, Integer::sum);
        }

        List<FacetsView.CategoryFacet> categories = byCategory.entrySet().stream()
                .map(entry -> new FacetsView.CategoryFacet(entry.getKey(), entry.getValue()))
                .toList();

        return new FacetsView(channels, categories);
    }

    private static boolean carriesChannel(Work work, String channelCode, Format loadFilter) {
        return offersOf(work, loadFilter, Set.of()).stream()
                .anyMatch(offer -> offer.getChannel().getCode().equals(channelCode));
    }

    /** Out-of-range pages are clamped rather than answered with a blank screen. */
    private static int clampPage(Integer requested, int totalPages) {
        int lastPage = Math.max(1, totalPages);
        if (requested == null) {
            return 1;
        }
        return Math.min(Math.max(requested, 1), lastPage);
    }

    private static Set<String> codeSet(List<String> values) {
        return (values == null) ? Set.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> nameSet(List<String> values) {
        return (values == null) ? Set.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean withinPrice(int price, Integer min, Integer max) {
        return (min == null || price >= min) && (max == null || price <= max);
    }

    /**
     * 出版年: "2025" is that year exactly, "2024-" is 2024 或更早, blank is 不限.
     *
     * The 或更早 option is written as a trailing dash rather than as its own
     * parameter so that one control on the form stays one value in the URL.
     */
    private static boolean matchesYear(Work work, String year) {
        if (year == null || year.isBlank()) {
            return true;
        }

        String value = year.trim();
        boolean orEarlier = value.endsWith("-");
        String digits = orEarlier ? value.substring(0, value.length() - 1) : value;

        int bound;
        try {
            bound = Integer.parseInt(digits);
        } catch (NumberFormatException cause) {
            throw new IllegalArgumentException("不支援的出版年: " + year, cause);
        }

        return orEarlier
                ? work.getPublicationYear() <= bound
                : work.getPublicationYear() == bound;
    }

    /**
     * The 進階搜尋 欄位條件.
     *
     * A row with a blank 關鍵字 is not a condition, so it drops out and the 布林
     * operator with it: joining one condition to nothing has no meaning, and
     * treating the blank row as "matches everything" would silently turn NOT
     * into "nothing matches".
     */
    private static boolean matchesFields(Work work, FieldQuery fields) {
        if (fields == null || fields.isEmpty()) {
            return true;
        }

        boolean hasFirst = fields.term1() != null && !fields.term1().isBlank();
        boolean hasSecond = fields.term2() != null && !fields.term2().isBlank();

        if (hasFirst && !hasSecond) {
            return matchesCondition(work, fields.field1(), fields.term1());
        }
        if (!hasFirst) {
            return matchesCondition(work, fields.field2(), fields.term2());
        }

        boolean first = matchesCondition(work, fields.field1(), fields.term1());
        boolean second = matchesCondition(work, fields.field2(), fields.term2());

        return switch (parseBooleanOp(fields.op())) {
            case OR -> first || second;
            case NOT -> first && !second;
            case AND -> first && second;
        };
    }

    private enum BooleanOp { AND, OR, NOT }

    /** Blank means AND, the operator the first row implies. */
    private static BooleanOp parseBooleanOp(String op) {
        if (op == null || op.isBlank()) {
            return BooleanOp.AND;
        }
        try {
            return BooleanOp.valueOf(op.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException("不支援的布林運算: " + op, cause);
        }
    }

    /**
     * One 欄位條件, matched as a case-insensitive substring the way 簡易搜尋 does.
     *
     * An unknown 搜尋欄位 is rejected rather than quietly treated as 書名: a typo in
     * the parameter would otherwise return confident results for a field the
     * reader never asked about.
     */
    private static boolean matchesCondition(Work work, String field, String term) {
        String needle = term.trim().toLowerCase(Locale.ROOT);
        String key = (field == null || field.isBlank())
                ? "title"
                : field.trim().toLowerCase(Locale.ROOT);

        return switch (key) {
            case "title" -> containsIgnoringCase(work.getTitle(), needle);
            case "author" -> containsIgnoringCase(work.getAuthor(), needle);
            case "publisher" -> containsIgnoringCase(work.getPublisher(), needle);
            case "translator" -> containsIgnoringCase(work.getTranslator(), needle);
            case "series" -> containsIgnoringCase(work.getSeries(), needle);
            case "isbn" -> work.getEditions().stream()
                    .anyMatch(edition -> containsIgnoringCase(edition.getIsbn(), needle));
            default -> throw new IllegalArgumentException("不支援的搜尋欄位: " + field);
        };
    }

    /** 譯者 and 系列 are unset in the seeded 書目, and a null matches nothing. */
    private static boolean containsIgnoringCase(String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
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

        List<Offer> offers = offersOf(work, loadFilter, Set.of());
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

    /**
     * 最低價 and the rest of the card for a set of 作品, keyed by 作品 id.
     *
     * 追蹤清單 is the caller: it holds ids, needs prices, and must not reach into
     * the 書目 itself. No 載體 or 通路 filter applies — a 追蹤 row is about the
     * 作品 as a whole, so its price is the cheapest 報價 anywhere.
     */
    public java.util.Map<Long, WorkSummary> summariesByWorkId(
            java.util.Collection<Long> workIds) {
        if (workIds.isEmpty()) {
            return java.util.Map.of();
        }

        java.util.Map<Long, WorkSummary> byId = new LinkedHashMap<>();
        for (Work work : workRepository.findAllWithOffersByIdIn(workIds)) {
            toSummary(work, null, Set.of())
                    .ifPresent(summary -> byId.put(work.getId(), summary));
        }
        return byId;
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
    private static Optional<WorkSummary> toSummary(Work work, Format loadFilter,
            Set<String> channels) {
        List<Offer> offers = offersOf(work, loadFilter, channels);
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

    /**
     * Every 報價 of one 作品 that survives the 載體 and 通路 filters, in fixed 通路
     * order.
     *
     * This is the single choke point both filters act through, which is why
     * narrowing 通路 changes 最低價 and 有貨通路數 rather than only hiding rows.
     * An empty set means no 通路 filter, not "no 通路".
     */
    private static List<Offer> offersOf(Work work, Format loadFilter, Set<String> channels) {
        return work.getEditions().stream()
                .filter(edition -> loadFilter == null || edition.getFormat() == loadFilter)
                .flatMap(edition -> edition.getOffers().stream())
                .filter(offer -> channels.isEmpty()
                        || channels.contains(offer.getChannel().getCode()))
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

    private static Instant latestFetchedAt(List<Work> works, Format loadFilter,
            Set<String> channels) {
        return works.stream()
                .flatMap(work -> offersOf(work, loadFilter, channels).stream())
                .map(Offer::getFetchedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
