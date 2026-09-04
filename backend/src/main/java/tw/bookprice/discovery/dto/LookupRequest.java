package tw.bookprice.discovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 拿一個書名去各通路找書.
 *
 * @param title 書名 the reader searched for and the 書目 did not answer
 */
public record LookupRequest(
        @NotBlank(message = "請提供書名")
        @Size(max = 100, message = "書名過長")
        String title) {
}
