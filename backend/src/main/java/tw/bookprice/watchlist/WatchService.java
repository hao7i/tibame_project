package tw.bookprice.watchlist;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.CatalogueService;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.catalogue.dto.WorkSummary;
import tw.bookprice.member.Member;
import tw.bookprice.member.MemberRepository;
import tw.bookprice.member.MemberService;
import tw.bookprice.watchlist.dto.WatchItemView;
import tw.bookprice.watchlist.dto.WatchStatus;

/**
 * 追蹤清單 and 目標價.
 *
 * 目前最低價 is never stored on the 追蹤 row. It is recomputed from the 報價 on every
 * read, because the entire purpose of 追蹤 is that the price moves after the
 * reader stopped looking; a copy taken at 追蹤 time would freeze the one number
 * the screen exists to report.
 *
 * This build does not send email. 已達目標價 is a state the screen shows, and the
 * 通知狀態 column says so — nothing here queues a message.
 */
@Service
@Transactional(readOnly = true)
public class WatchService {

    private final WatchItemRepository watchItemRepository;
    private final MemberRepository memberRepository;
    private final WorkRepository workRepository;
    private final CatalogueService catalogueService;

    public WatchService(WatchItemRepository watchItemRepository,
            MemberRepository memberRepository,
            WorkRepository workRepository,
            CatalogueService catalogueService) {
        this.watchItemRepository = watchItemRepository;
        this.memberRepository = memberRepository;
        this.workRepository = workRepository;
        this.catalogueService = catalogueService;
    }

    /** Every 追蹤 row of one 會員, oldest first, priced as of now. */
    public List<WatchItemView> list(String email) {
        List<WatchItem> items = watchItemRepository.findAllForMember(normalise(email));

        Map<Long, WorkSummary> summaries = catalogueService.summariesByWorkId(
                items.stream().map(item -> item.getWork().getId()).toList());

        return items.stream()
                .map(item -> toView(item, summaries.get(item.getWork().getId())))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public long count(String email) {
        return watchItemRepository.countByMemberEmail(normalise(email));
    }

    /**
     * 追蹤 one 作品, addressed by either of its 版本 ISBNs.
     *
     * Idempotent: pressing 追蹤 twice leaves one row and keeps the 目標價 already
     * set, rather than resetting it or failing on the unique constraint.
     */
    @Transactional
    public void add(String email, String isbn) {
        String normalised = normalise(email);
        if (watchItemRepository.findForMemberByIsbn(normalised, isbn).isPresent()) {
            return;
        }

        Work work = workRepository.findByEditionIsbn(isbn)
                .orElseThrow(() -> new NoSuchElementException("找不到 ISBN 為 " + isbn + " 的作品"));

        watchItemRepository.save(new WatchItem(member(normalised), work, Instant.now()));
    }

    /** 移除 one row. Removing something not tracked is not an error. */
    @Transactional
    public void remove(String email, String isbn) {
        watchItemRepository.findForMemberByIsbn(normalise(email), isbn)
                .ifPresent(watchItemRepository::delete);
    }

    /** 清空清單. */
    @Transactional
    public void clear(String email) {
        watchItemRepository.deleteByMemberEmail(normalise(email));
    }

    /**
     * 設定目標價, or clear it with null.
     *
     * @throws NoSuchElementException when the 作品 is not on this 會員 list, so a
     *                                stale form cannot silently create a row
     */
    @Transactional
    public WatchItemView setTargetPrice(String email, String isbn, Integer targetPrice) {
        WatchItem item = watchItemRepository.findForMemberByIsbn(normalise(email), isbn)
                .orElseThrow(() -> new NoSuchElementException("追蹤清單裡沒有這本書: " + isbn));

        item.setTargetPrice(targetPrice);

        Map<Long, WorkSummary> summaries =
                catalogueService.summariesByWorkId(List.of(item.getWork().getId()));

        return toView(item, summaries.get(item.getWork().getId()))
                .orElseThrow(() -> new NoSuchElementException("這本書目前沒有任何報價: " + isbn));
    }

    private Member member(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("找不到會員: " + email));
    }

    /**
     * A 追蹤 row whose 作品 carries no 報價 at all is left out rather than drawn with
     * a blank price — the same rule the 搜尋結果 applies.
     */
    private static java.util.Optional<WatchItemView> toView(WatchItem item, WorkSummary summary) {
        if (summary == null) {
            return java.util.Optional.empty();
        }

        Integer target = item.getTargetPrice();
        int best = summary.bestPrice().price();

        WatchStatus status;
        Integer gap = null;
        if (target == null) {
            status = WatchStatus.NO_TARGET;
        } else if (best <= target) {
            status = WatchStatus.REACHED;
        } else {
            status = WatchStatus.ABOVE_TARGET;
            gap = best - target;
        }

        return java.util.Optional.of(new WatchItemView(
                summary.isbn(),
                summary.title(),
                summary.author(),
                summary.publisher(),
                summary.publicationYear(),
                summary.bestPrice(),
                target,
                status,
                gap));
    }

    private static String normalise(String email) {
        return MemberService.normalise(email);
    }
}
