package tw.bookprice.discovery;

/**
 * One book a 通路 says it carries, as found by 書名.
 *
 * This is the bridge between "the reader typed a title we do not stock" and the
 * rest of the system, which is keyed on ISBN throughout. Discovery exists only
 * to turn the former into the latter — once an ISBN is known, the ordinary
 * 取價 path prices it at all five 通路, including the ones that cannot be
 * searched by title at all.
 *
 * @param isbn            ISBN-13, the key everything downstream joins on
 * @param title           書名 as the 通路 words it
 * @param author          作者, or null when the page does not say
 * @param publisher       出版社, or null
 * @param publicationYear 出版年, or 0 when unknown
 *
 * 內容簡介 is deliberately absent. KingstonePriceProvider records the position
 * this project takes: tabulated price data is not copyrightable subject matter,
 * but the 著作 next to it is. 書封 was excepted by an explicit decision; that
 * exception is not extended here on its own.
 */
public record DiscoveredBook(
        String isbn,
        String title,
        String author,
        String publisher,
        int publicationYear) {
}
