"use client";

import { removeFromWatchList, setTargetPrice } from "@/lib/watch-actions";
import styles from "./watch.module.css";

/**
 * 目標價, edited in place.
 *
 * The design gives this field no save button, so it commits on blur and on
 * Enter. It stays an uncontrolled input bound to a Server Action: the status
 * that depends on it is recomputed by the server on the next render, which is
 * the only place the comparison against 最低價 can honestly be made.
 */
export function TargetPriceField({
  isbn,
  targetPrice,
}: {
  isbn: string;
  targetPrice?: number;
}) {
  const commit = setTargetPrice.bind(null, isbn);

  return (
    <form action={commit} className={styles.targetForm}>
      <label className="sr-only" htmlFor={`target-${isbn}`}>
        目標價
      </label>
      <input
        id={`target-${isbn}`}
        name="targetPrice"
        className={`input ${styles.targetInput}`}
        inputMode="numeric"
        placeholder="NT$"
        defaultValue={targetPrice ?? ""}
        // Enter submits by itself; this is the blur half, so moving away from
        // the field is not a way to lose what was typed into it.
        onBlur={(event) => event.currentTarget.form?.requestSubmit()}
      />
    </form>
  );
}

/** 移除 one row. */
export function RemoveButton({ isbn, title }: { isbn: string; title: string }) {
  const remove = removeFromWatchList.bind(null, isbn);

  return (
    <form action={remove}>
      <button type="submit" className="btn btn-ghost" aria-label={`移除 ${title}`}>
        移除
      </button>
    </form>
  );
}
