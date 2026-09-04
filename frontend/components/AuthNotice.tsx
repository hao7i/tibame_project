"use client";

import { useEffect, useRef } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import styles from "./AuthNotice.module.css";

/**
 * The 註冊 / 登入 / 登出 confirmation popup.
 *
 * It cannot live on /login: all three succeed by redirecting away from it, so
 * the destination is the only place still on screen when there is something to
 * confirm. The action puts `?notice=` on the redirect and this reads it.
 *
 * Mounted in the root layout so it works wherever the redirect lands — 首頁
 * today, 單書比價 when the reader came through the 追蹤 detour.
 */
const NOTICES = {
  registered: { title: "註冊成功", body: "帳號已建立，並且已經為你登入。" },
  signedIn: { title: "登入成功", body: "歡迎回來。" },
  signedOut: { title: "已登出", body: "你已經登出，追蹤清單需要重新登入才能查看。" },
} as const;

type NoticeKey = keyof typeof NOTICES;

function isNoticeKey(value: string | null): value is NoticeKey {
  return value !== null && value in NOTICES;
}

export function AuthNotice() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const dialogRef = useRef<HTMLDialogElement>(null);

  const key = searchParams.get("notice");
  const notice = isNoticeKey(key) ? NOTICES[key] : null;

  useEffect(() => {
    if (notice) {
      dialogRef.current?.showModal();
    }
  }, [notice]);

  if (!notice) {
    return null;
  }

  // Dropping the parameter on close means a reload, or a link someone shares,
  // does not announce a 登入 that happened once, minutes ago.
  //
  // Hung on the close event rather than the button so that Esc, which closes a
  // native dialog on its own, cleans up the same way.
  const stripNotice = () => {
    const next = new URLSearchParams(searchParams.toString());
    next.delete("notice");
    const queryString = next.toString();
    router.replace(queryString ? `${pathname}?${queryString}` : pathname);
  };

  return (
    <dialog
      ref={dialogRef}
      className={`dialog ${styles.notice}`}
      aria-labelledby="auth-notice-title"
      onClose={stripNotice}
    >
      <p id="auth-notice-title" className="dialog-title">
        {notice.title}
      </p>
      <p className="dialog-body">{notice.body}</p>
      <div className="dialog-actions">
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => dialogRef.current?.close()}
        >
          知道了
        </button>
      </div>
    </dialog>
  );
}
