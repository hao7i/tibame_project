"use client";

import { useEffect, useRef } from "react";
import { App } from "antd";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * The 註冊 / 登入 / 登出 confirmation.
 *
 * It cannot live on /login: all three succeed by redirecting away from it, so
 * the destination is the only place still on screen when there is something to
 * confirm. The action puts `?notice=` on the redirect and this reads it.
 *
 * Mounted in the root layout so it works wherever the redirect lands — 首頁
 * today, 單書比價 when the reader came through the 追蹤 detour.
 *
 * Shown as an antd notification at the top rather than a modal dialog: 登入 and
 * 登出 succeeded, so there is nothing for the reader to decide and no reason to
 * make them dismiss anything before carrying on.
 */
const NOTICES = {
  registered: {
    type: "success",
    title: "註冊成功",
    body: "帳號已建立，並且已經為你登入。",
  },
  signedIn: { type: "success", title: "登入成功", body: "歡迎回來。" },
  signedOut: {
    type: "info",
    title: "已登出",
    body: "你已經登出，追蹤清單需要重新登入才能查看。",
  },
} as const;

type NoticeKey = keyof typeof NOTICES;

function isNoticeKey(value: string | null): value is NoticeKey {
  return value !== null && value in NOTICES;
}

export function AuthNotice() {
  const { notification } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const key = searchParams.get("notice");

  // Which notice has already been announced. Without it a change of
  // searchParams identity between firing and the URL catching up would announce
  // the same 登入 twice; reset when the parameter is gone so the next 登入 is
  // announced normally.
  const announced = useRef<string | null>(null);

  useEffect(() => {
    if (!isNoticeKey(key)) {
      announced.current = null;
      return;
    }
    if (announced.current === key) {
      return;
    }
    announced.current = key;

    const notice = NOTICES[key];
    notification[notice.type]({
      title: notice.title,
      description: notice.body,
      placement: "top",
    });

    // Dropped as soon as it has been shown, so a reload — or a link someone
    // shares — does not announce a 登入 that happened once, minutes ago.
    const next = new URLSearchParams(searchParams.toString());
    next.delete("notice");
    const queryString = next.toString();
    router.replace(queryString ? `${pathname}?${queryString}` : pathname);
  }, [key, notification, router, pathname, searchParams]);

  return null;
}
