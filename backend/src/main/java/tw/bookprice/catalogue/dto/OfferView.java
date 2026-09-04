package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of the 各通路報價 table.
 *
 * @param channelCode   picks the 通路 tint for the swatch beside the name
 * @param discountLabel absent when 售價 is at or above 定價
 * @param purchaseUrl   where 前往購買 goes, absent when the 通路 has no usable link
 * @param best          true for the 最低價 row, which the design tints and tags
 * @param stale         true when the last 取價 for this 通路 failed. The 售價 is
 *                      then the last known one rather than a current one, and
 *                      the table says so rather than passing it off as fresh.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferView(
        String channel,
        String channelCode,
        String stockStatus,
        int price,
        String discountLabel,
        String purchaseUrl,
        boolean best,
        boolean stale) {
}
