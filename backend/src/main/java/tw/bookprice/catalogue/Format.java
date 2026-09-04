package tw.bookprice.catalogue;

/**
 * 載體 — the publication medium of a 版本. The site covers exactly two, and the
 * 全部版本 / 紙本書 / 電子書 switcher in the design filters on this.
 *
 * PAPER is declared first because a 作品 is addressed by its 紙本 ISBN where it
 * has one; see {@link Work#getPrimaryIsbn()}.
 */
public enum Format {
    PAPER,
    EBOOK
}
