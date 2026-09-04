package tw.bookprice.catalogue.dto;

import java.util.List;

/**
 * The 篩選條件 rail options with their counts.
 *
 * The counts are catalogue-wide totals, deliberately independent of the current
 * 搜尋 and of the other facets — the design prototype computes them over the
 * whole 書目, and the README does not override that. They are therefore served
 * from their own endpoint rather than recomputed inside every 搜尋 response.
 */
public record FacetsView(
        List<ChannelFacet> channels,
        List<CategoryFacet> categories) {

    /** @param count how many 作品 carry a 報價 from this 通路 */
    public record ChannelFacet(String code, String name, int count) {
    }

    /** @param count how many 作品 sit in this 分類 */
    public record CategoryFacet(String name, int count) {
    }
}
