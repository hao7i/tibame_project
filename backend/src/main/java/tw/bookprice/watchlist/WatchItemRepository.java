package tw.bookprice.watchlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence for 追蹤清單. */
@Repository
public interface WatchItemRepository extends JpaRepository<WatchItem, Long> {

    /** Oldest first, so a list does not reshuffle when a 目標價 is edited. */
    @Query("""
            select wi from WatchItem wi
            join fetch wi.work
            where wi.member.email = :email
            order by wi.createdAt, wi.id
            """)
    List<WatchItem> findAllForMember(@Param("email") String email);

    @Query("""
            select wi from WatchItem wi
            join fetch wi.work w
            where wi.member.email = :email
              and exists (select 1 from Edition e where e.work = w and e.isbn = :isbn)
            """)
    Optional<WatchItem> findForMemberByIsbn(@Param("email") String email,
            @Param("isbn") String isbn);

    long countByMemberEmail(String email);

    void deleteByMemberEmail(String email);
}
