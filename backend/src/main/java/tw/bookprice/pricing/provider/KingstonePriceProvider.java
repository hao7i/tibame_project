package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** 金石堂 — 示意實作; see SeedChannelPriceProvider. */
@Component
public class KingstonePriceProvider extends SeedChannelPriceProvider {

    public KingstonePriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "KINGSTONE";
    }
}
