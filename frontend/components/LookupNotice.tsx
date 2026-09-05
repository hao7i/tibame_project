"use client";

import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { App } from "antd";
import { useRouter } from "next/navigation";

type LookupState = {
  /** True while a 找書 is in flight, whichever screen started it. */
  running: boolean;
  start: (term: string) => void;
};

const LookupContext = createContext<LookupState | null>(null);

/**
 * 找書 that does not hold the reader still.
 *
 * 找書 takes over a minute — a 搜尋 at 金石堂, a 商品頁 per candidate, then 取價 at
 * five 通路. It used to run as a Server Action, which shares a queue with router
 * navigation, so for that whole minute every link on the page did nothing when
 * clicked. It now goes through a plain route handler, which is outside that
 * queue, so 登入 and everything else stays clickable.
 *
 * The state lives here, in the layout, rather than in the button. App Router
 * navigation is client-side, so this provider survives moving from 搜尋結果 to
 * anywhere else — which means the reader can wander off and still be told when
 * the book arrives.
 *
 * The outcome is an antd notification, the same as 登入 and 登出: 找書 has already
 * finished by the time there is anything to say, so there is nothing for the
 * reader to decide and no reason to make them dismiss a modal to carry on. It
 * also sidesteps what made the success case invisible before — a <dialog> holds
 * its open state in the DOM rather than in React, so the router.refresh() that
 * brings the new book into the list closed it again.
 */
export function LookupProvider({ children }: { children: ReactNode }) {
  const { notification } = App.useApp();
  const router = useRouter();
  const [running, setRunning] = useState(false);

  const start = useCallback(
    (term: string) => {
      const trimmed = term.trim();
      // One at a time: a second press while the first is still out would cost
      // the 通路 another run of fetches for no more answer.
      if (!trimmed || running) {
        return;
      }

      setRunning(true);

      fetch("/api/lookups", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ term: trimmed }),
      })
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(String(response.status));
          }
          const body = (await response.json()) as { imported: string[] };
          return body.imported.length;
        })
        .then((imported) => {
          if (imported > 0) {
            notification.success({
              title: "找到了",
              description: `收錄了 ${imported} 本書，價格也一併取回來了。`,
              placement: "top",
            });
            // Only worth re-running the screen when something actually arrived
            // for it to show.
            router.refresh();
          } else {
            notification.info({
              title: "找書完成",
              description: "各通路都沒有這個書名或 ISBN 的書。",
              placement: "top",
            });
          }
        })
        .catch(() => {
          notification.error({
            title: "找書失敗",
            description: "請稍後再試一次。",
            placement: "top",
          });
        })
        .finally(() => setRunning(false));
    },
    [running, notification, router],
  );

  return (
    <LookupContext.Provider value={{ running, start }}>
      {children}
    </LookupContext.Provider>
  );
}

/** Null when no provider is above, which is how a 找書 button knows to stay hidden. */
export function useLookup(): LookupState | null {
  return useContext(LookupContext);
}
