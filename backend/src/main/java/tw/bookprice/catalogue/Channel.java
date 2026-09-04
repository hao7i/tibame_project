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

    /**
     * Where 前往購買 sends a reader, with "{isbn}" standing in for the ISBN of the
     * 版本 being bought. A template without that placeholder is used as-is, which
     * is how the 通路 whose ISBN search could not be established are handled.
     *
     * Nullable: ddl-auto adds this column to databases that already hold 通路
     * rows, and a NOT NULL column cannot be added to a populated table. The seed
     * backfills it.
     */
    @Column(name = "search_url_template", length = 300)
    private String searchUrlTemplate;

    protected Channel() {
        // for JPA
    }

    public Channel(String code, String name, String kind, int displayOrder,
            String searchUrlTemplate) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.displayOrder = displayOrder;
        this.searchUrlTemplate = searchUrlTemplate;
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

    public String getSearchUrlTemplate() {
        return searchUrlTemplate;
    }

    /**
     * Take over the identity of a 通路 this one replaces.
     *
     * 報價 point at the 通路 row, not at its code, so converting the row in place
     * carries them across instead of stranding them. It exists because 誠品線上
     * was replaced by 三民網路書店 after the 書目 had already been seeded, and the
     * seeder is idempotent: without this, an existing database would keep a 通路
     * that can no longer be priced.
     */
    public void replaceWith(String code, String name, String kind, String searchUrlTemplate) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.searchUrlTemplate = searchUrlTemplate;
    }

    public void setSearchUrlTemplate(String searchUrlTemplate) {
        this.searchUrlTemplate = searchUrlTemplate;
    }

    /** The 前往購買 target for one 版本, or null when this 通路 has no link. */
    public String purchaseUrlFor(String isbn) {
        if (searchUrlTemplate == null || searchUrlTemplate.isBlank()) {
            return null;
        }
        return searchUrlTemplate.replace("{isbn}", isbn);
    }
}
