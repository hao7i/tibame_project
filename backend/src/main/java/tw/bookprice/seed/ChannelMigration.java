package tw.bookprice.seed;

import java.util.Set;

/**
 * 通路 that no longer appear in CHANNELS.
 *
 * Three have been replaced since the first seed — 誠品線上 by 三民網路書店, 博客來 by
 * 五南文化廣場, 樂天Kobo by 墊腳石 — each because the old one could not be priced at
 * all. A database seeded before a replacement still holds the old row, and the
 * seeder is idempotent, so it would never notice on its own.
 */
final class ChannelMigration {

    static final Set<String> RETIRED_CODES = Set.of("ESLITE", "BOOKS_TW", "KOBO");

    private ChannelMigration() {
    }
}
