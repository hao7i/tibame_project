package tw.bookprice.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for 前台會員. */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** Email is the 帳號; it is stored lower-cased so this lookup is exact. */
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);
}
