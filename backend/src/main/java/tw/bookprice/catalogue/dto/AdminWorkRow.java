package tw.bookprice.catalogue.dto;

/** One 作品 in the 管理後台 maintenance list. */
public record AdminWorkRow(
        String isbn,
        String title,
        String author,
        String publisher,
        int publicationYear,
        String category,
        int editionCount,
        int offerCount) {
}
