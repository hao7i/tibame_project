package tw.bookprice.pricing;

/**
 * A 通路 could not be read this time.
 *
 * Distinct from an empty result: 沒有這本書 is an answer, this is the absence of
 * one. PriceRefreshService catches it per 通路 and records 取價失敗, which is what
 * keeps one unreachable shop from emptying the whole comparison.
 */
public class PriceFetchException extends RuntimeException {

    public PriceFetchException(String message) {
        super(message);
    }

    public PriceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
