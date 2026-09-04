package tw.bookprice.watchlist.dto;

/**
 * Where a 作品 stands against its 目標價.
 *
 * A code rather than a sentence: the three states drive a tag, a row tint and a
 * number, and the wording of each belongs to the screen that draws them.
 */
public enum WatchStatus {

    /** 未設目標價 — 追蹤 without a number to wait for. */
    NO_TARGET,

    /** 已達目標價 — 最低價 is at or below the 目標價. */
    REACHED,

    /** 尚差 NT$ n — still above, by the gap the view carries. */
    ABOVE_TARGET
}
