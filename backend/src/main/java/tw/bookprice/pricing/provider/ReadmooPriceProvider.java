package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** Readmoo — 示意實作; see SeedChannelPriceProvider. */
@Component
public class ReadmooPriceProvider extends SeedChannelPriceProvider {

    public ReadmooPriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "READMOO";
    }
}
