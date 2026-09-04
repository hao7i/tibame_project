package tw.bookprice.discovery;

/**
 * Is this string an ISBN-13.
 *
 * Shared because two very different things need it. 找書 uses it to refuse a
 * shop's own product number, which is thirteen digits as well and would key a
 * 作品 on something no other 通路 has heard of. 收錄 uses it to decide whether the
 * reader typed a 書名 or an ISBN, which are answered by completely different
 * routes — one has to search and guess, the other can go straight to the book.
 */
public final class Isbn13 {

    private Isbn13() {
    }

    /**
     * A Bookland prefix and a correct check digit.
     *
     * Both halves earn their place. The 978/979 prefix rejects 金石堂 product
     * numbers, which start 20; the check digit rejects one that was mistyped or
     * truncated. Neither proves the ISBN names any particular book — only that
     * it is an ISBN at all.
     */
    public static boolean isValid(String value) {
        if (value == null || !value.matches("97[89][0-9]{10}")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = value.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10 == value.charAt(12) - '0';
    }
}
