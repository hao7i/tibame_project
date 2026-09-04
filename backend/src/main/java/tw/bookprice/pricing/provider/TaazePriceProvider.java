package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** 讀冊生活 — 示意實作; see SeedChannelPriceProvider. */
@Component
public class TaazePriceProvider extends SeedChannelPriceProvider {

    public TaazePriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "TAAZE";
    }
}
