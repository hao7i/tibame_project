package tw.bookprice.member;

/** 註冊 with an 電子郵件 that already has an 帳號 — a 409, not a 500. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("這個電子郵件已經註冊過了: " + email);
    }
}
