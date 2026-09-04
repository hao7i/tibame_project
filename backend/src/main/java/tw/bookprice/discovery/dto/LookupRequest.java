package tw.bookprice.discovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 拿使用者輸入的字去各通路找書.
 *
 * One field rather than separate 書名 and ISBN ones: an ISBN is checkable, so the
 * service can tell which it is without the caller having to declare it, and a
 * caller that had to declare it could declare it wrongly.
 *
 * @param term 書名 or ISBN — whatever the reader searched for and the 書目 could
 *             not answer
 */
public record LookupRequest(
        @NotBlank(message = "請提供書名或 ISBN")
        @Size(max = 100, message = "查詢字串過長")
        String term) {
}
