package tw.bookprice.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 前台會員 — the account a reader signs in with to 追蹤 books and set 目標價.
 *
 * Deliberately its own table, unrelated to the single 管理後台 operator account
 * that guards /admin: the two are separate identity systems, and giving them
 * one table would mean a 會員 row could ever become an administrator.
 *
 * The password is only ever held as a BCrypt hash. Nothing in this class can
 * return the plaintext, because nothing is ever given it to keep.
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The 帳號 itself. Stored lower-cased so 登入 is not case-sensitive. */
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Member() {
        // for JPA
    }

    public Member(String email, String passwordHash, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
