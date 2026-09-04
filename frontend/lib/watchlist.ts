import { BACKEND_URL, type BestPrice } from "@/lib/api";
import { sessionHeader } from "@/lib/session";

/**
 * 追蹤清單 reads.
 *
 * Kept apart from the actions in lib/watch-actions.ts: everything exported from
 * a "use server" module becomes an endpoint the browser can call, and these are
 * plain reads the pages do for themselves.
 */

/** Where a 作品 stands against its 目標價. The wording lives in the view. */
export type WatchStatus = "NO_TARGET" | "REACHED" | "ABOVE_TARGET";

export type WatchItem = {
  isbn: string;
  title: string;
  author: string;
  publisher: string;
  publicationYear: number;
  bestPrice: BestPrice;
  /** Absent when 未設目標價. */
  targetPrice?: number;
  status: WatchStatus;
  /** 尚差 how much; absent unless status is ABOVE_TARGET. */
  gap?: number;
};

/** The signed-in 會員 list, or empty when nobody is signed in. */
export async function listWatchItems(): Promise<WatchItem[]> {
  const response = await fetch(`${BACKEND_URL}/api/me/watchlist`, {
    headers: { Accept: "application/json", ...(await sessionHeader()) },
    cache: "no-store",
  });

  if (!response.ok) {
    return [];
  }
  return (await response.json()) as WatchItem[];
}

/**
 * How many books are tracked, for the 導覽列 count.
 *
 * Never throws and never blocks a page: a header count is not worth failing a
 * render over, so an unreachable backend simply reads as 0.
 */
export async function watchCount(): Promise<number> {
  try {
    const response = await fetch(`${BACKEND_URL}/api/me/watchlist/count`, {
      headers: { Accept: "application/json", ...(await sessionHeader()) },
      cache: "no-store",
    });

    if (!response.ok) {
      return 0;
    }
    const body = (await response.json()) as { count: number };
    return body.count;
  } catch {
    return 0;
  }
}

/** Whether one 作品 is already tracked, for the 追蹤中 state of its button. */
export async function isWatched(isbn: string): Promise<boolean> {
  const items = await listWatchItems();
  return items.some((item) => item.isbn === isbn);
}
