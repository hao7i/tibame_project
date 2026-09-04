package tw.bookprice.pricing.provider;

import java.util.Optional;
import tw.bookprice.catalogue.WorkRepository;
import tw.bookprice.pricing.ChannelPriceProvider;
import tw.bookprice.pricing.FetchedPrice;

/**
 * 示意實作 shared by every 通路 that is not fetched for real yet.
 *
 * It answers with the 報價 already on record, which is exactly what 示意資料 means:
 * the shop is not actually asked, so it can only say what the seed said. A 取價
 * against it therefore moves 取價時間 and nothing else — honest, and enough to
 * exercise the whole 取價 path end to end.
 *
 * 票 10 replaces one or two of the subclasses with real fetching. Nothing above
 * this interface changes when it does, which is the reason the interface exists.
 */
public abstract class SeedChannelPriceProvider implements ChannelPriceProvider {

    private final WorkRepository workRepository;


    protected SeedChannelPriceProvider(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    /**
     * The 書名 this ISBN belongs to in our own 書目.
     *
     * A live provider needs it when the 通路 offers nothing else to check a page
     * against — see WunanPriceProvider, where the only ISBN on the page is the
     * one we put in the URL.
     */
    protected Optional<String> expectedTitle(String isbn) {
        return workRepository.findByEditionIsbn(isbn).map(work -> work.getTitle());
    }

    @Override
    public Optional<FetchedPrice> fetch(String isbn) {
        return workRepository.findByEditionIsbn(isbn).stream()
                .flatMap(work -> work.getEditions().stream())
                .filter(edition -> edition.getIsbn().equals(isbn))
                .flatMap(edition -> edition.getOffers().stream())
                .filter(offer -> offer.getChannel().getCode().equals(channelCode()))
                .findFirst()
                .map(offer -> FetchedPrice.seeded(offer.getPrice(), offer.getStockStatus()));
    }
}
