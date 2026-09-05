"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { App } from "antd";
import { useRouter } from "next/navigation";

type LookupState = {
  /** True while a 找書 is in flight, whichever screen started it. */
  running: boolean;
  /** Whole seconds since the running 找書 began; 0 when nothing is running. */
  elapsed: number;
  start: (term: string) => void;
};

const LookupContext = createContext<LookupState | null>(null);

/** 63 → 「1 分 3 秒」, 42 → 「42 秒」. */
function describeSeconds(seconds: number): string {
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return rest === 0 ? `${minutes} 分` : `${minutes} 分 ${rest} 秒`;
}

/**
 * 找書 that does not hold the reader still.
 *
 * 找書 takes a while — a 搜尋 at 金石堂, a 商品頁 per candidate, then 取價 at five
 * 通路, with a pause between requests to the same host. It used to run as a
 * Server Action, which shares a queue with router navigation, so for that whole
 * time every link on the page did nothing when clicked. It now goes through a
 * plain route handler, which is outside that queue, so 登入 and everything else
 * stays clickable.
 *
 * The state lives here, in the layout, rather than in the button. App Router
 * navigation is client-side, so this provider survives moving from 搜尋結果 to
 * anywhere else — which means the reader can wander off and still be told when
 * the book arrives.
 *
 * How long it has been going is part of that state. A minute of silence with no
 * sense of whether it is nearly done reads as broken, and the run is long enough
 * that the reader deserves to see it moving.
 *
 * The outcome is an antd notification, the same as 登入 and 登出: 找書 has already
 * finished by the time there is anything to say, so there is nothing for the
 * reader to decide and no reason to make them dismiss a modal to carry on.
 */
export function LookupProvider({ children }: { children: ReactNode }) {
  const { notification } = App.useApp();
  const router = useRouter();
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);

  const running = startedAt !== null;

  // Ticks only while a 找書 is out, and is cleared with it — an interval left
  // running would re-render the whole tree once a second for nothing. The
  // stale count between runs is never read: the context reports 0 unless
  // something is actually running.
  useEffect(() => {
    if (startedAt === null) {
      return;
    }
    const id = setInterval(
      () => setElapsed(Math.floor((Date.now() - startedAt) / 1000)),
      1000,
    );
    return () => clearInterval(id);
  }, [startedAt]);

  const start = useCallback(
    (term: string) => {
      const trimmed = term.trim();
      // One at a time: a second press while the first is still out would cost
      // the 通路 another run of fetches for no more answer.
      if (!trimmed || running) {
        return;
      }

      const began = Date.now();
      setStartedAt(began);
      setElapsed(0);

      // Measured here rather than taken from the tick, so the figure reported is
      // the real duration even if the tab was backgrounded and throttled.
      const took = () => describeSeconds(Math.round((Date.now() - began) / 1000));

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
              description: `收錄了 ${imported} 本書，價格也一併取回來了。耗時 ${took()}。`,
              placement: "top",
            });
            // Only worth re-running the screen when something actually arrived
            // for it to show.
            router.refresh();
          } else {
            notification.info({
              title: "找書完成",
              description: `各通路都沒有這個書名或 ISBN 的書。耗時 ${took()}。`,
              placement: "top",
            });
          }
        })
        .catch(() => {
          notification.error({
            title: "找書失敗",
            description: `請稍後再試一次。耗時 ${took()}。`,
            placement: "top",
          });
        })
        .finally(() => setStartedAt(null));
    },
    [running, notification, router],
  );

  return (
    <LookupContext.Provider value={{ running, elapsed: running ? elapsed : 0, start }}>
      {children}
    </LookupContext.Provider>
  );
}

/** Null when no provider is above, which is how a 找書 button knows to stay hidden. */
export function useLookup(): LookupState | null {
  return useContext(LookupContext);
}
