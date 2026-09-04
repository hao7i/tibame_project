package tw.bookprice.catalogue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 作品 — a piece of writing itself, independent of how it was published. This is
 * what a reader searches for and what a 會員 追蹤s.
 *
 * 出版社 and 出版年 sit here rather than on 版本, where they strictly belong: every
 * seeded 作品 has one publisher across its 版本, and 進階搜尋 queries 出版社 as a
 * single field. Move them down if a 作品 ever needs two publishers.
 */
@Entity
@Table(name = "work")
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String author;

    @Column(nullable = false, length = 120)
    private String publisher;

    @Column(name = "publication_year", nullable = false)
    private int publicationYear;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(length = 1000)
    private String blurb;

    /**
     * Ordered by ISBN so iteration is stable. getPrimaryIsbn() picks the first
     * 紙本 版本 it sees, and a 作品 with both a 平裝 and a 精裝 版本 would otherwise
     * be addressed by a different ISBN from one restart to the next, breaking
     * every /works/{isbn} link already shared.
     */
    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isbn")
    private Set<Edition> editions = new LinkedHashSet<>();

    protected Work() {
        // for JPA
    }

    public Work(String title, String author, String publisher, int publicationYear,
            String category, String blurb) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.category = category;
        this.blurb = blurb;
    }

    /** Keeps both sides of the association in step. */
    public void addEdition(Edition edition) {
        editions.add(edition);
        edition.setWork(this);
    }

    /**
     * The ISBN a 作品 is addressed by in URLs. 紙本 wins because it is the number a
     * reader recognises; an ebook-only 作品 falls back to its own lowest ISBN, so
     * that every 作品 has a stable address.
     */
    public String getPrimaryIsbn() {
        return editions.stream()
                .filter(edition -> edition.getFormat() == Format.PAPER)
                .map(Edition::getIsbn)
                .findFirst()
                .orElseGet(() -> editions.stream()
                        .map(Edition::getIsbn)
                        .min(Comparator.naturalOrder())
                        .orElseThrow(() -> new IllegalStateException(
                                "作品沒有任何版本: " + title)));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublisher() {
        return publisher;
    }

    public int getPublicationYear() {
        return publicationYear;
    }

    public String getCategory() {
        return category;
    }

    public String getBlurb() {
        return blurb;
    }

    public Set<Edition> getEditions() {
        return editions;
    }
}
