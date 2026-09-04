package tw.bookprice.catalogue.dto;

import java.time.Instant;

/** One 報價 as the 管理後台 lists it for editing. */
public record AdminOfferRow(
        Long id,
        String isbn,
        String channelName,
        String channelCode,
        int price,
        String stockStatus,
        Instant fetchedAt) {
}
