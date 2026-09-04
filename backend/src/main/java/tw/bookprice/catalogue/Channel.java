package tw.bookprice.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 通路 — an online bookshop the site collects 報價 from. Six of them. */
@Entity
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key used by the price-fetch adapters, unlike the display name. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** 紙本 / 電子書 / 紙本 · 電子書 — the sub-label on the 收錄通路 strip. */
    @Column(nullable = false, length = 32)
    private String kind;

    /** Fixed presentation order, shared by the strip, the facets and 依通路 sort. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Channel() {
        // for JPA
    }

    public Channel(String code, String name, String kind, int displayOrder) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
