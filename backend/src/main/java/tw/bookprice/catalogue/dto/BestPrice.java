package tw.bookprice.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 最低價 — always computed, never stored, because it moves with the 通路 and 載體
 * a reader has filtered to.
 *
 * @param discountPercent 售價 as a percentage of 定價, rounded
 * @param discountLabel   the same figure written the way Taiwanese bookshops do:
 *                        79 percent reads 79 折, a round 70 percent reads 7 折.
 *                        Absent when there is no 折扣 to state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BestPrice(
        String channel,
        int price,
        int discountPercent,
        String discountLabel) {
}
