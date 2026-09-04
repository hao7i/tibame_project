package tw.bookprice.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 版本 — one concrete published form of a 作品, keyed by ISBN and carrying its own
 * 定價. The 紙本 and the 電子書 of one 作品 are two 版本, not one (ADR 0002).
 */
@Entity
@Table(name = "edition")
public class Edition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Format format;

    /** The finer-grained label the offer table shows: 紙本平裝 / 電子書 EPUB. */
    @Column(name = "format_label", nullable = false, length = 40)
    private String formatLabel;

    /** 定價 — belongs to the 版本, and is the basis every 折扣 is computed against. */
    @Column(name = "list_price", nullable = false)
    private int listPrice;

    /**
     * 書封 URL as one 通路 publishes it, hotlinked rather than copied — so it is
     * their asset on their CDN, and null whenever we have not fetched a page
     * that carries one. The 佔位框 covers the null, which is every 版本 until a
     * 取價 has actually run.
     *
     * Nullable, and it has to stay that way: a NOT NULL column cannot be added
     * to a table that already holds rows.
     */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @OneToMany(mappedBy = "edition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Offer> offers = new LinkedHashSet<>();

    protected Edition() {
        // for JPA
    }

    public Edition(String isbn, Format format, String formatLabel, int listPrice) {
        this.isbn = isbn;
        this.format = format;
        this.formatLabel = formatLabel;
        this.listPrice = listPrice;
    }

    /** Keeps both sides of the association in step. */
    public void addOffer(Offer offer) {
        offers.add(offer);
        offer.setEdition(this);
    }

    void setWork(Work work) {
        this.work = work;
    }

    public Long getId() {
        return id;
    }

    public Work getWork() {
        return work;
    }

    public String getIsbn() {
        return isbn;
    }

    public Format getFormat() {
        return format;
    }

    public String getFormatLabel() {
        return formatLabel;
    }

    public int getListPrice() {
        return listPrice;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    /**
     * Overwritten on every 取價 that carries a 書封 rather than kept from the
     * first one seen. A URL that has rotted is then replaced by whichever 通路
     * still serves one, instead of the 版本 being stuck with a dead image for good.
     */
    public void recordCoverImage(String url) {
        if (url != null && !url.isBlank()) {
            this.coverImageUrl = url.trim();
        }
    }

    public Set<Offer> getOffers() {
        return offers;
    }
}
