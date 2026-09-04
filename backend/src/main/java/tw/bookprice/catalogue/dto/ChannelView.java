package tw.bookprice.catalogue.dto;

/** One 通路 for the 收錄通路 strip and the 通路 facet group. */
public record ChannelView(
        String code,
        String name,
        String kind) {
}
