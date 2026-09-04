package tw.bookprice.discovery.dto;

import java.util.List;

/**
 * What one 找書 added to the 書目.
 *
 * An empty list is a real answer, not a failure: the 通路 either knew nothing by
 * that 書名, or knew only books already held.
 *
 * @param imported ISBNs written, in the order they were found
 */
public record LookupResult(List<String> imported) {

    public int count() {
        return imported.size();
    }
}
