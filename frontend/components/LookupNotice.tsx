"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import styles from "./LookupNotice.module.css";

type Outcome =
  | { kind: "imported"; count: number }
  | { kind: "none" }
  | { kind: "error" };

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
 */
export function LookupProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [running, setRunning] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  const start = useCallback(
    (term: string) => {
      const trimmed = term.trim();
      // One at a time: a second press while the first is still out would cost
      // the 通路 another run of fetches for no more answer.
      if (!trimmed || running) {
        return;
      }

      setRunning(true);
      setOutcome(null);

      fetch("/api/lookups", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ term: trimmed }),
      })
        .then(async (response) => {
          if (!response.ok) {
            return { kind: "error" } as Outcome;
          }
          const body = (await response.json()) as { imported: string[] };
          return body.imported.length > 0
            ? ({ kind: "imported", count: body.imported.length } as Outcome)
            : ({ kind: "none" } as Outcome);
        })
        .catch(() => ({ kind: "error" }) as Outcome)
        .then((result) => {
          setRunning(false);
          setOutcome(result);
          if (result.kind === "imported") {
            // Whatever screen the reader ended up on, its 書目 data was fetched
            // before these books existed.
            router.refresh();
          }
        });
    },
    [router, running],
  );

  useEffect(() => {
    if (outcome) {
      dialogRef.current?.showModal();
    }
  }, [outcome]);

  return (
    <LookupContext.Provider value={{ running, start }}>
      {children}

      <dialog
        ref={dialogRef}
        className={`dialog ${styles.notice}`}
        aria-labelledby="lookup-notice-title"
        onClose={() => setOutcome(null)}
      >
        <p id="lookup-notice-title" className="dialog-title">
          {outcome?.kind === "imported" ? "找到了" : "找書完成"}
        </p>
        <p className="dialog-body">
          {outcome?.kind === "imported"
            ? `收錄了 ${outcome.count} 本書，價格也一併取回來了。`
            : outcome?.kind === "none"
              ? "各通路都沒有這個書名或 ISBN 的書。"
              : "找書失敗，請稍後再試一次。"}
        </p>
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
    </LookupContext.Provider>
  );
}

/** Null when no provider is above, which is how a 找書 button knows to stay hidden. */
export function useLookup(): LookupState | null {
  return useContext(LookupContext);
}
