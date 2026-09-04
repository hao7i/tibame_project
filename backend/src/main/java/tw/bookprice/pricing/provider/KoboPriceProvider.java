package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** 樂天Kobo — 示意實作; see SeedChannelPriceProvider. */
@Component
public class KoboPriceProvider extends SeedChannelPriceProvider {

    public KoboPriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "KOBO";
    }
}
