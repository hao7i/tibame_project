package tw.bookprice.catalogue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Persistence for 通路. Six rows, always read in their fixed presentation order. */
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findAllByOrderByDisplayOrderAsc();
}
