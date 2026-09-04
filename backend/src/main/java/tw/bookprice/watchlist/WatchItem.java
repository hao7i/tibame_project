package tw.bookprice.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import tw.bookprice.catalogue.Work;
import tw.bookprice.member.Member;

/**
 * One 作品 a 會員 追蹤s, with the 目標價 they are waiting for.
 *
 * The foreign key points at 作品, never at 版本: a reader who wants 原子習慣 wants
 * whichever 版本 is cheapest, so tracking the 紙本 and the 電子書 separately would
 * split one intention into two rows that disagree.
 *
 * 目標價 is nullable because 追蹤 comes first and the number comes later, if ever.
 */
@Entity
@Table(name = "watch_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watch_member_work",
                columnNames = {"member_id", "work_id"}))
public class WatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    /** 目標價 in NT$, or null for 未設目標價. */
    @Column(name = "target_price")
    private Integer targetPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WatchItem() {
        // for JPA
    }

    public WatchItem(Member member, Work work, Instant createdAt) {
        this.member = member;
        this.work = work;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Work getWork() {
        return work;
    }

    public Integer getTargetPrice() {
        return targetPrice;
    }

    /** Null clears the 目標價 back to 未設目標價. */
    public void setTargetPrice(Integer targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
