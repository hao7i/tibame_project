package tw.bookprice.pricing.provider;

import org.springframework.stereotype.Component;
import tw.bookprice.catalogue.WorkRepository;

/** 博客來 — 示意實作; see SeedChannelPriceProvider. */
@Component
public class BooksTwPriceProvider extends SeedChannelPriceProvider {

    public BooksTwPriceProvider(WorkRepository workRepository) {
        super(workRepository);
    }

    @Override
    public String channelCode() {
        return "BOOKS_TW";
    }
}
