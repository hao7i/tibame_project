package tw.bookprice.watchlist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 設定目標價.
 *
 * @param targetPrice NT$, or null to clear it back to 未設目標價. Zero and negative
 *                    numbers are refused: they are not prices a 通路 could ever
 *                    reach, so accepting them would mean a row stuck at 尚差.
 */
public record TargetPriceRequest(
        @Min(value = 1, message = "目標價需大於 0")
        @Max(value = 100000, message = "目標價過高")
        Integer targetPrice) {
}
