package tw.bookprice.pricing.provider;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import tw.bookprice.pricing.FetchedPrice;
import tw.bookprice.pricing.HtmlFetcher;
import tw.bookprice.pricing.PriceFetchException;

/**
 * Walking the candidate listings a 通路 search returned.
 *
 * Both live 通路 answer an ISBN search with several listings and neither ranks
 * the right one first, so the flow is always: try each, keep the one whose
 * JSON-LD declares the ISBN we asked about.
 *
 * The part worth stating: one dead candidate link must not cost us the rest.
 * A stale result pointing at a removed page throws, and if that ended the walk
 * the shop would be reported as unreachable while the listing we wanted sat
 * two positions further down. Failures are therefore remembered and only
 * raised if no candidate produced a price at all — the same tolerance the
 * 取價 run applies per 通路, applied here per listing.
 */
final class CandidateSearch {

    private CandidateSearch() {
    }

    static Optional<FetchedPrice> firstMatching(HtmlFetcher fetcher, Set<String> candidates,
            Function<String, String> toUrl,
            BiFunction<String, String, Optional<FetchedPrice>> parse) {

        PriceFetchException lastFailure = null;

        for (String candidate : candidates) {
            String url = toUrl.apply(candidate);
            try {
                Optional<FetchedPrice> found = parse.apply(url, fetcher.get(url));
                if (found.isPresent()) {
                    return found;
                }
            } catch (PriceFetchException cause) {
                lastFailure = cause;
            }
        }

        if (lastFailure != null) {
            // Nothing matched and something broke: that is a failure to read the
            // 通路, not a statement that it does not carry the book.
            throw lastFailure;
        }
        return Optional.empty();
    }
}
