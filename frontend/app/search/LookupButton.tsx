"use client";

import { useLookup } from "@/components/LookupNotice";

/**
 * 到各通路找找看 — the way a book the 書目 does not hold gets into it.
 *
 * 搜尋 only ever reads our own 書目, so a book nobody has imported returns
 * nothing however many shops stock it. This is the bridge, and it is a button
 * rather than something 搜尋 does on its own because one press costs 金石堂 a
 * 搜尋 plus a 商品頁 per candidate, then all five 通路 a 取價 — far too much to
 * spend on a typo.
 *
 * Only the press lives here. The request and its result belong to LookupProvider
 * in the layout, because the run outlasts this screen: the reader can go to 登入
 * or anywhere else while it finishes, and the answer still has to reach them.
 *
 * The term is either a 書名 or an ISBN; the server decides which by checking it,
 * so this component does not need to know.
 */
export function LookupButton({ term }: { term: string }) {
  const lookup = useLookup();
  if (!lookup) {
    return null;
  }

  return (
    <div>
      <button
        type="button"
        className="btn btn-primary"
        onClick={() => lookup.start(term)}
        disabled={lookup.running}
      >
        {lookup.running ? "查詢各通路中…" : "到各通路找找看"}
      </button>

      {lookup.running ? (
        <p className="card-meta" role="status">
          正在向五家通路查詢並取價，大約需要一分鐘。這段時間可以繼續瀏覽，
          找完會通知你。
        </p>
      ) : null}
    </div>
  );
}
