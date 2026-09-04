package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tw.bookprice.catalogue.Format;

/**
 * One row of the 各通路報價 table.
 *
 * @param channelCode   picks the 通路 tint for the swatch beside the name
 * @param formatLabel   the 版本 column: 紙本平裝 / 電子書 EPUB
 * @param discountLabel absent when 售價 is at or above 定價
 * @param purchaseUrl   where 前往購買 goes, absent when the 通路 has no usable link
 * @param best          true for the 最低價 row, which the design tints and tags
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferView(
        String channel,
        String channelCode,
        Format format,
        String formatLabel,
        String stockStatus,
        int price,
        String discountLabel,
        String purchaseUrl,
        boolean best) {
}
