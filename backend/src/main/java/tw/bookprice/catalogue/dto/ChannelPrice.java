package tw.bookprice.catalogue.dto;


/** One 通路 and what it asks, with the 載體 the 報價 belongs to. */
public record ChannelPrice(
        String channel,
        String channelCode,
        int price) {
}
