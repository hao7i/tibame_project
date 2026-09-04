package tw.bookprice.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.Channel;
import tw.bookprice.catalogue.ChannelRepository;
import tw.bookprice.catalogue.Edition;
import tw.bookprice.catalogue.Format;
import tw.bookprice.catalogue.Offer;
import tw.bookprice.catalogue.Work;
import tw.bookprice.catalogue.WorkRepository;

/**
 * 把使用者搜尋不到的書收進書目.
 *
 * 搜尋 only ever looks at our own 書目, so a 書名 nobody has imported yet returns
 * nothing however many shops stock it. This closes that gap in the only way the
 * rest of the system can accept: find the ISBN, write the 作品, and then let the
 * ordinary 取價 path price it.
 *
 * The ISBN is what makes this work. Discovery needs a 通路 whose 搜尋 can be read
 * — only 金石堂 and 讀冊生活 qualify, and 墊腳石 renders its results in the browser
 * so it never will — but pricing needs only an ISBN, so all five 通路 end up
 * quoting a book that only one of them was able to find.
 *
 * 定價 is left at 0 rather than guessed. Neither 金石堂 nor 讀冊生活 publishes it
 * as a field; it could be reverse-engineered from 售價 and the 折扣 label, but a
 * number displayed to the reader as 定價 should not be arithmetic on a rounded
 * percentage. discountPercent already treats 0 as 定價未知 and stops claiming a
 * 折扣, which is the honest reading.
 */
@Service
public class CatalogueImportService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueImportService.class);

    /** 匯入的作品沒有 分類 資料; the screens show this instead of an empty tag. */
    private static final String UNKNOWN_CATEGORY = "未分類";

    /** 版本 label for an imported book, which is 紙本 unless a 通路 says otherwise. */
    private static final String PAPER_LABEL = "紙本";

    private final List<BookDiscovery> discoveries;
    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;

    public CatalogueImportService(List<BookDiscovery> discoveries,
            WorkRepository workRepository, ChannelRepository channelRepository) {
        this.discoveries = discoveries;
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * Find books matching 書名 and add the ones not already held.
     *
     * @param limit how many books one search may add, so a broad 書名 cannot
     *              pull half a shop into the 書目
     * @return the ISBNs actually written, empty when nothing new was found
     */
    @Transactional
    public List<String> importByTitle(String title, int limit) {
        if (title == null || title.isBlank()) {
            return List.of();
        }

        List<Channel> channels = channelRepository.findAll();
        if (channels.isEmpty()) {
            return List.of();
        }

        List<String> imported = new ArrayList<>();

        for (BookDiscovery discovery : discoveries) {
            for (DiscoveredBook book : discover(discovery, title, limit)) {
                if (imported.size() >= limit) {
                    return List.copyOf(imported);
                }
                // Already held, whether from the seed or an earlier import.
                if (workRepository.findByEditionIsbn(book.isbn()).isPresent()) {
                    continue;
                }

                workRepository.save(toWork(book, channels));
                imported.add(book.isbn());
                log.info("收錄新書: {} ({})", book.title(), book.isbn());
            }
        }

        return List.copyOf(imported);
    }

    /**
     * One 通路 failing to answer must not sink the search — the next one may
     * know the book, and the reader gets whatever could be found.
     */
    private List<DiscoveredBook> discover(BookDiscovery discovery, String title, int limit) {
        try {
            return discovery.byTitle(title, limit);
        } catch (RuntimeException cause) {
            log.warn("找書失敗: {}", discovery.channelCode(), cause);
            return List.of();
        }
    }

    /**
     * A 作品 with one 報價 row per 通路, every one of them 查無 and stamped at the
     * epoch.
     *
     * The rows exist so the 報價表 has a column per 通路 from the start; they are
     * 查無 because no 通路 has actually been asked yet, and the epoch stamp is
     * what makes the next 取價 treat them as stale and go and ask.
     */
    private static Work toWork(DiscoveredBook book, List<Channel> channels) {
        Work work = new Work(
                book.title(),
                book.author() == null ? "" : book.author(),
                book.publisher() == null ? "" : book.publisher(),
                book.publicationYear(),
                UNKNOWN_CATEGORY,
                null);

        Edition paper = new Edition(book.isbn(), Format.PAPER, PAPER_LABEL, 0);
        work.addEdition(paper);

        for (Channel channel : channels) {
            Offer offer = new Offer(channel, 0, "", Instant.EPOCH);
            offer.forgetFetched();
            paper.addOffer(offer);
        }

        return work;
    }
}
