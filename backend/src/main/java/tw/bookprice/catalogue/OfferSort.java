package tw.bookprice.catalogue;

/** The two orders the 單書比價 offer table can be read in. */
public enum OfferSort {
    /** 價格低→高, the default the design opens with. */
    PRICE,
    /** 依通路 — the fixed presentation order every other screen uses. */
    CHANNEL
}
