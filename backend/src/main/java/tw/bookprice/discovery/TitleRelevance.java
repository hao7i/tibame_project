package tw.bookprice.discovery;

import java.util.Locale;

/**
 * Does this book have anything to do with what the reader typed.
 *
 * 金石堂 fills the top of a 搜尋結果 page with a promotional carousel, so the first
 * product links in document order are not results at all — they are whatever the
 * shop is pushing that day. Taking them on trust wrote 減脂餐 and 九州攻略 into the
 * 書目 under a search for 三國演義.
 *
 * Position on the page is the wrong thing to trust, and would need revisiting
 * every time the site is restyled. What the book is called does not move.
 */
public final class TitleRelevance {

    private TitleRelevance() {
    }

    /**
     * True when either title contains the other, ignoring case and whitespace.
     *
     * Containment both ways on purpose: a 搜尋 for 三國演義 should accept
     * 《三國演義英雄豪傑》, and a 搜尋 for the full 書名 of a book should still match
     * a shop that lists it under a shorter one.
     */
    public static boolean matches(String query, String title) {
        String needle = normalise(query);
        String candidate = normalise(title);

        if (needle.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        return candidate.contains(needle) || needle.contains(candidate);
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\s+", "");
    }
}
