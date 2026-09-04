package tw.bookprice.member.dto;

/**
 * A 會員 as the front end is allowed to see one.
 *
 * Carries no password material of any kind — not the hash, not its length.
 */
public record MemberView(Long id, String email) {
}
