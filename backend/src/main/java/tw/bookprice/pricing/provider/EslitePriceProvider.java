package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** 誠品線上 — 示意實作; see SeedChannelPriceProvider. */
@Component
public class EslitePriceProvider extends SeedChannelPriceProvider {

    public EslitePriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "ESLITE";
    }
}
