package tw.bookprice.seed;

import java.util.Set;

/**
 * 通路 that no longer appear in CHANNELS.
 *
 * 誠品線上 became 三民網路書店, 博客來 became 五南文化廣場 and 樂天Kobo became 墊腳石,
 * each because the old one could not be priced at all. Readmoo was dropped
 * outright rather than replaced when 電子書 比價 was removed.
 *
 * A database seeded before any of that still holds the old rows, and the seeder
 * is idempotent, so it would never notice on its own.
 */
final class ChannelMigration {

    static final Set<String> RETIRED_CODES = Set.of("ESLITE", "BOOKS_TW", "KOBO", "READMOO");

    private ChannelMigration() {
    }
}
