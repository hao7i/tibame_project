"use client";

import { Plus, Star } from "lucide-react";
import { addToWatchList, removeFromWatchList } from "@/lib/watch-actions";

/**
 * ＋追蹤價格 / 追蹤中, wherever a 作品 is shown.
 *
 * One component for all four places the design draws this control, so the
 * signed-out detour and the 追蹤中 appearance cannot drift apart between the
 * 列表, 卡片, 表格 and 單書比價 screens.
 *
 * Pressing it while signed out is not an error: the action sends the reader to
 * 登入 carrying this book, and adds it once they are in.
 */
export function WatchToggle({
  isbn,
  title,
  watched,
  variant = "label",
  className = "",
}: {
  isbn: string;
  title: string;
  watched: boolean;
  /** label = ＋追蹤價格 text; icon = the ☆/★ button the cards and table use. */
  variant?: "label" | "icon";
  className?: string;
}) {
  const action = watched
    ? removeFromWatchList.bind(null, isbn)
    : addToWatchList.bind(null, isbn, title);

  const appearance = watched ? "btn btn-primary" : "btn btn-secondary";

  return (
    <form action={action} className={className}>
      <button
        type="submit"
        className={variant === "icon" ? `${appearance}` : `${appearance} ${className}`}
        aria-pressed={watched}
        aria-label={watched ? `取消追蹤 ${title}` : `追蹤 ${title}`}
      >
        {watched ? (
          <Star size={16} strokeWidth={1.5} fill="currentColor" />
        ) : variant === "icon" ? (
          <Star size={16} strokeWidth={1.5} />
        ) : (
          <Plus size={16} strokeWidth={1.5} />
        )}
        {variant === "label" ? (watched ? "追蹤中" : "追蹤價格") : null}
      </button>
    </form>
  );
}
