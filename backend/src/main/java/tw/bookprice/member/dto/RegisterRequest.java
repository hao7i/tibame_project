package tw.bookprice.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 註冊新帳號.
 *
 * @param email    the 帳號; must look like an address and be unused
 * @param password 至少 8 個字元, the minimum the 登入 form advertises
 */
public record RegisterRequest(
        @NotBlank(message = "請輸入電子郵件")
        @Email(message = "電子郵件格式不正確")
        @Size(max = 200, message = "電子郵件過長")
        String email,

        @NotBlank(message = "請輸入密碼")
        @Size(min = 8, max = 72, message = "密碼至少 8 個字元")
        String password) {
}
