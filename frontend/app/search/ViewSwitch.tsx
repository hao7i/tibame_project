"use client";

import { useOptimistic, useTransition } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { DEFAULT_VIEW, VIEWS, parseView, type ViewValue } from "@/lib/filters";
import styles from "./search.module.css";

/**
 * 呈現方式 — 列表 / 卡片 / 表格.
 *
 * The choice lives in the URL like every other 搜尋結果 condition, so it survives
 * 篩選, 分頁 and the back button, and a link opens in the view it was shared
 * from. 桌機 and 手機 both read that one value, which is what 「桌機與手機共用」
 * asks for: two switchers, never two states.
 *
 * 分頁 is deliberately kept. Changing how the results are drawn does not change
 * which results they are, so sending the reader back to page 1 would be
 * gratuitous — unlike a 篩選 change, which really does reshape the result set.
 *
 * The pick is held optimistically because the URL only catches up once the
 * server has answered; without it the radio would refuse to move until then.
 */
export function ViewSwitch() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [, startTransition] = useTransition();
  const [view, setView] = useOptimistic<ViewValue>(
    parseView(searchParams.get("view") ?? undefined),
  );

  const pick = (next: ViewValue) => {
    const params = new URLSearchParams(searchParams.toString());
    // 列表 is the default, so it stays out of the URL and the plain /search
    // link keeps working.
    if (next === DEFAULT_VIEW) {
      params.delete("view");
    } else {
      params.set("view", next);
    }
    const queryString = params.toString();

    startTransition(() => {
      setView(next);
      router.push(queryString ? `${pathname}?${queryString}` : pathname);
    });
  };

  return (
    <div className={styles.viewSwitch}>
      <span className={styles.viewLabel}>呈現方式</span>
      <div className="seg">
        {VIEWS.map((option) => (
          <label key={option.value} className="seg-opt">
            <input
              type="radio"
              name="view"
              value={option.value}
              checked={view === option.value}
              onChange={() => pick(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </div>
  );
}
