package tw.bookprice.catalogue;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bookprice.catalogue.dto.AdminChannelRow;
import tw.bookprice.catalogue.dto.AdminOfferRow;
import tw.bookprice.catalogue.dto.AdminWorkRow;

/**
 * 書目維護 for the 管理後台.
 *
 * Separate from CatalogueService on purpose. That one answers the public 書目
 * and is read-only by design; this one writes, and is reachable from exactly
 * one place. Keeping them apart means a mistake here cannot widen what the
 * public API can do, and the read path keeps its readOnly transaction.
 *
 * It exists at all because a MVC Controller may not touch a Repository: the
 * 管理後台 needs the same discipline as the JSON API, not a shortcut because it
 * is internal.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueAdminService {

    private final WorkRepository workRepository;
    private final ChannelRepository channelRepository;

    public CatalogueAdminService(WorkRepository workRepository,
            ChannelRepository channelRepository) {
        this.workRepository = workRepository;
        this.channelRepository = channelRepository;
    }

    /** The six 通路 with their editable 購買連結樣板. */
    public List<AdminChannelRow> listChannels() {
        return channelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(channel -> new AdminChannelRow(
                        channel.getCode(),
                        channel.getName(),
                        channel.getKind(),
                        channel.getDisplayOrder(),
                        channel.getSearchUrlTemplate()))
                .toList();
    }

    /** Every 作品 with its 版本 and 報價 counts, for the maintenance list. */
    public List<AdminWorkRow> listWorks() {
        return workRepository.findAllWithOffers().stream()
                .map(work -> new AdminWorkRow(
                        work.getPrimaryIsbn(),
                        work.getTitle(),
                        work.getAuthor(),
                        work.getPublisher(),
                        work.getPublicationYear(),
                        work.getCategory(),
                        work.getEditions().size(),
                        work.getEditions().stream()
                                .mapToInt(edition -> edition.getOffers().size())
                                .sum()))
                .toList();
    }

    /** Every 報價 of one 作品, the rows the 報價 editor lists. */
    public List<AdminOfferRow> listOffers(String isbn) {
        Work work = workRepository.findByEditionIsbn(isbn)
                .orElseThrow(() -> new NoSuchElementException("找不到 ISBN 為 " + isbn + " 的作品"));

        return work.getEditions().stream()
                .flatMap(edition -> edition.getOffers().stream()
                        .map(offer -> new AdminOfferRow(
                                offer.getId(),
                                edition.getIsbn(),
                                edition.getFormatLabel(),
                                offer.getChannel().getName(),
                                offer.getChannel().getCode(),
                                offer.getPrice(),
                                offer.getStockStatus(),
                                offer.getFetchedAt())))
                .toList();
    }

    public String titleOf(String isbn) {
        return workRepository.findByEditionIsbn(isbn)
                .map(Work::getTitle)
                .orElseThrow(() -> new NoSuchElementException("找不到 ISBN 為 " + isbn + " 的作品"));
    }

    /**
     * 維護 one 報價 by hand.
     *
     * 取價時間 is left exactly as it was: this is an operator correcting a figure,
     * not the 通路 reporting a new one, and stamping it as a fresh 取價 would
     * claim the shop said something it never said.
     */
    @Transactional
    public void updateOffer(String isbn, Long offerId, int price, String stockStatus) {
        if (price <= 0) {
            throw new IllegalArgumentException("售價需大於 0");
        }

        Work work = workRepository.findByEditionIsbn(isbn)
                .orElseThrow(() -> new NoSuchElementException("找不到 ISBN 為 " + isbn + " 的作品"));

        Offer offer = work.getEditions().stream()
                .flatMap(edition -> edition.getOffers().stream())
                .filter(candidate -> candidate.getId().equals(offerId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("這個作品沒有編號 " + offerId + " 的報價"));

        offer.recordFetch(price, stockStatus, offer.getFetchedAt());
    }

    /** 通路 maintenance: the 前往購買 link template is the one editable field. */
    @Transactional
    public void updateChannelSearchUrl(String code, String searchUrlTemplate) {
        Channel channel = channelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(candidate -> candidate.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("找不到通路: " + code));

        String trimmed = (searchUrlTemplate == null) ? "" : searchUrlTemplate.trim();
        channel.setSearchUrlTemplate(trimmed.isEmpty() ? null : trimmed);
    }
}
