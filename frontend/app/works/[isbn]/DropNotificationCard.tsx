"use client";

import Link from "next/link";
import { Blueprint } from "@/components/Blueprint";
import { setTargetPrice } from "@/lib/watch-actions";
import styles from "./work.module.css";

/**
 * 降價通知.
 *
 * Signed out, this is an invitation to 登入 and the field is inert. Signed in,
 * the field writes a real 目標價 — but only for a 作品 already on the 追蹤清單,
 * which is why 未追蹤 shows the 追蹤 prompt instead of a field that would have
 * nowhere to save to.
 *
 * This build sends no email. The card keeps the design wording because that is
 * what the design says, and 追蹤清單 is where the state is actually shown.
 */
export function DropNotificationCard({
  isbn,
  loggedIn,
  targetPrice,
}: {
  isbn: string;
  loggedIn: boolean;
  /** Undefined when 未設目標價, or when the 作品 is not tracked at all. */
  targetPrice?: number;
}) {
  const commit = setTargetPrice.bind(null, isbn);

  return (
    <Blueprint className={`card ${styles.card}`}>
      <p className="card-kicker">降價通知</p>
      <p className="card-body">
        {loggedIn
          ? "設定目標價，低於此價時在追蹤清單顯示已達目標價。"
          : "降價通知為會員功能，登入後可設定目標價。"}
      </p>

      {loggedIn ? (
        <form action={commit}>
          <div className={`field ${styles.targetField}`}>
            <label htmlFor="target-price">目標價 NT$</label>
            <input
              id="target-price"
              name="targetPrice"
              className="input"
              inputMode="numeric"
              defaultValue={targetPrice ?? ""}
            />
          </div>

          <button type="submit" className={`btn btn-secondary ${styles.blockButton}`}>
            設定通知
          </button>
        </form>
      ) : (
        <>
          <div className={`field ${styles.targetField}`}>
            <label htmlFor="target-price">目標價 NT$</label>
            <input id="target-price" className="input" inputMode="numeric" disabled />
          </div>

          <Link href="/login" className={`btn btn-secondary ${styles.blockButton}`}>
            登入以設定通知
          </Link>
        </>
      )}
    </Blueprint>
  );
}
