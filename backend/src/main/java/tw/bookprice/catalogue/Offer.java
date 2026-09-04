package tw.bookprice.catalogue;

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

/**
 * 報價 — what one 通路 asks for one 版本, as of one 取價 moment.
 *
 * 折扣 is deliberately not stored. It is 售價 measured against the 定價 of the
 * 版本, so computing it on the way out keeps a single source of truth; the
 * arithmetic reproduces every discount label in the design prototype exactly.
 */
@Entity
@Table(name = "offer", uniqueConstraints = @UniqueConstraint(
        name = "uk_offer_edition_channel", columnNames = {"edition_id", "channel_id"}))
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private int price;

    /** 庫存／到貨, verbatim from the 通路: 24 小時到貨, 現貨, 立即下載… */
    @Column(name = "stock_status", nullable = false, length = 60)
    private String stockStatus;

    /** 取價 time, shown as the 取價時間 stamp on the results and detail screens. */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /**
     * When the most recent 取價 attempt for this 報價 failed, or null when the
     * last attempt succeeded.
     *
     * A failed attempt deliberately keeps the 售價 it had: the number is still
     * the last thing the 通路 actually said, and blanking it would throw away
     * true information because of a transient outage. What changes is that the
     * screen may no longer present it as current.
     */
    /**
     * The 商品頁 this 售價 was read from, when it came from a real 取價.
     *
     * Null for a 示意 報價, which falls back to the 通路 search link. Storing it
     * is what lets 前往購買 land on the page that actually states this price —
     * required of a comparison site, and the difference between citing a source
     * and passing its work off as our own.
     */
    @Column(name = "product_url", length = 500)
    private String productUrl;

    /**
     * The 通路 answered that it does not carry this book.
     *
     * Distinct from a failed 取價: that one could not read the shop and keeps
     * the last known price, this one got a clear answer and therefore has no
     * price at all. The row is kept rather than deleted so a later 取價 can
     * bring it back — 書名 matching is deliberately conservative and may report
     * 查無 for a book the shop does stock.
     */
    /*
     * Nullable on purpose. A NOT NULL column cannot be added to a table that
     * already holds rows without a default, so ddl-auto silently fails to
     * create it and every later query dies on the missing column. Nullable
     * lets it be added, and null simply reads as "not disowned".
     */
    @Column(name = "unavailable")
    private Boolean unavailable;

    @Column(name = "fetch_failed_at")
    private Instant fetchFailedAt;

    protected Offer() {
        // for JPA
    }

    public Offer(Channel channel, int price, String stockStatus, Instant fetchedAt) {
        this.channel = channel;
        this.price = price;
        this.stockStatus = stockStatus;
        this.fetchedAt = fetchedAt;
    }

    void setEdition(Edition edition) {
        this.edition = edition;
    }

    public Long getId() {
        return id;
    }

    public Edition getEdition() {
        return edition;
    }

    public Channel getChannel() {
        return channel;
    }

    public int getPrice() {
        return price;
    }

    public String getStockStatus() {
        return stockStatus;
    }

    /**
     * Record what a 取價 just found.
     *
     * One method rather than three setters: 售價, 庫存 and 取價時間 only ever change
     * together, and a 報價 whose price moved without its timestamp moving would
     * be a lie about when it was true.
     */
    public void recordFetch(int price, String stockStatus, Instant fetchedAt) {
        recordFetch(price, stockStatus, fetchedAt, this.productUrl);
    }

    public void recordFetch(int price, String stockStatus, Instant fetchedAt, String productUrl) {
        this.price = price;
        this.stockStatus = stockStatus;
        this.fetchedAt = fetchedAt;
        this.productUrl = productUrl;
        // A success clears both previous states: the 報價 is current, and the
        // 通路 evidently does carry the book after all.
        this.fetchFailedAt = null;
        this.unavailable = Boolean.FALSE;
    }

    /**
     * Record that the 通路 could not be read this time.
     *
     * 售價 and 取價時間 are untouched, so the screen can keep showing the last
     * known price while saying plainly that it is no longer fresh.
     */
    /** The 通路 says it does not carry this book. */
    public void markUnavailable() {
        this.unavailable = Boolean.TRUE;
    }

    public boolean isUnavailable() {
        return Boolean.TRUE.equals(unavailable);
    }

    public void recordFetchFailure(Instant attemptedAt) {
        this.fetchFailedAt = attemptedAt;
    }

    /** True when the last 取價 attempt failed and the 售價 is therefore stale. */
    public boolean isFetchFailed() {
        return fetchFailedAt != null;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public Instant getFetchFailedAt() {
        return fetchFailedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
