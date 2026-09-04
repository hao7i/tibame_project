package tw.bookprice.catalogue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence for 作品 and everything hanging off it.
 *
 * Every query fetches 版本 → 報價 → 通路 in one go: 最低價 is computed per request
 * from those rows, so leaving them lazy would mean one query per 作品 per 版本.
 * The collections are Sets rather than Lists precisely so two fetch joins can be
 * combined without Hibernate raising MultipleBagFetchException.
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    @Query("""
            select distinct w from Work w
            left join fetch w.editions e
            left join fetch e.offers o
            left join fetch o.channel
            order by w.id
            """)
    List<Work> findAllWithOffers();

    /**
     * 搜尋 over 書名, 作者, 出版社 and ISBN.
     *
     * The caller passes an already-lowercased %pattern% with LIKE wildcards
     * escaped by "!" (see CatalogueService.likePattern). Both sides go through
     * lower() so the result cannot depend on collation — SQL Server compares
     * case-insensitively by default and H2, which the tests run on, does not.
     *
     * ISBN is matched through a subquery rather than the fetched alias: filtering
     * on a join-fetched collection would prune the 版本 loaded into each 作品 and
     * silently shrink the 報價 that 最低價 is computed from.
     */
    @Query("""
            select distinct w from Work w
            left join fetch w.editions e
            left join fetch e.offers o
            left join fetch o.channel
            where lower(w.title) like :pattern escape '!'
               or lower(w.author) like :pattern escape '!'
               or lower(w.publisher) like :pattern escape '!'
               or exists (select 1 from Edition x
                          where x.work = w and lower(x.isbn) like :pattern escape '!')
            order by w.id
            """)
    List<Work> search(@Param("pattern") String pattern);

    /** Either 版本 ISBN addresses the 作品; the 紙本 one is the canonical form. */
    @Query("""
            select distinct w from Work w
            left join fetch w.editions e
            left join fetch e.offers o
            left join fetch o.channel
            where exists (select 1 from Edition x where x.work = w and x.isbn = :isbn)
            """)
    Optional<Work> findByEditionIsbn(@Param("isbn") String isbn);
}
