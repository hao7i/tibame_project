package tw.bookprice.api;

/**
 * The one error shape every /api endpoint returns, so the front end can read a
 * failure without knowing which endpoint produced it.
 */
public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message));
    }

    public record ApiError(String code, String message) {
    }
}
