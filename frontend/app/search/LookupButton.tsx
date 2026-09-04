"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { lookupByTitle, type LookupOutcome } from "@/lib/lookup";

/**
 * 到各通路找找看 — the way a 書名 the 書目 does not hold gets into it.
 *
 * 搜尋 only ever reads our own 書目, so a book nobody has imported returns
 * nothing however many shops stock it. This is the bridge, and it is a button
 * rather than something 搜尋 does on its own because one press costs 金石堂 a
 * 搜尋 plus a 商品頁 per candidate, then all five 通路 a 取價 — far too much to
 * spend on a typo.
 *
 * It takes the best part of a minute, so the pending state is not decoration:
 * without it the page would look broken.
 */
export function LookupButton({ query }: { query: string }) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [outcome, setOutcome] = useState<LookupOutcome | null>(null);

  const run = () => {
    setOutcome(null);
    startTransition(async () => {
      const result = await lookupByTitle(query);
      setOutcome(result);
      if (result.status === "imported") {
        router.refresh();
      }
    });
  };

  return (
    <div>
      <button type="button" className="btn btn-primary" onClick={run} disabled={pending}>
        {pending ? "查詢各通路中…" : "到各通路找找看"}
      </button>

      {pending ? (
        <p className="card-meta" role="status">
          正在向五家通路查詢並取價，可能需要一分鐘。
        </p>
      ) : null}

      {outcome?.status === "imported" ? (
        <p className="card-meta" role="status">
          收錄了 {outcome.count} 本，正在重新整理結果。
        </p>
      ) : null}

      {outcome?.status === "none" ? (
        <p className="card-meta" role="status">
          各通路也沒有這個書名的書。
        </p>
      ) : null}

      {outcome?.status === "error" ? (
        <p className="card-meta" role="alert">
          {outcome.message}
        </p>
      ) : null}
    </div>
  );
}
