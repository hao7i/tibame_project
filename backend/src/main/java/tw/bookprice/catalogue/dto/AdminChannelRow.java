package tw.bookprice.catalogue.dto;

/**
 * One 通路 as the 管理後台 maintains it.
 *
 * Carries the 購買連結樣板 that ChannelView deliberately does not: the public API
 * has no use for the template, only for the finished link, and widening the
 * public DTO to serve one internal screen would export a maintenance detail to
 * every caller.
 */
public record AdminChannelRow(
        String code,
        String name,
        String kind,
        int displayOrder,
        String searchUrlTemplate) {
}
