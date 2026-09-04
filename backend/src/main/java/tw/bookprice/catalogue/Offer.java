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

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
