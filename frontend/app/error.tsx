"use client";

import { useEffect } from "react";
import styles from "./error.module.css";

/**
 * Catches a failed render so a backend that is down, restarting or rejecting a
 * request shows this instead of the framework error page.
 *
 * Deliberately plain: the states the design calls for — the 取價 skeleton, the
 * per-通路 failure row, the 取價時間 stamp — belong to the loading-and-failure
 * work, and this only has to stop the whole screen collapsing before then.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  // The reader gets a fixed sentence; the detail goes to the console, where it
  // can be matched against the backend log by digest.
  useEffect(() => {
    console.error("比價頁面渲染失敗", error);
  }, [error]);

  return (
    <div className="page-shell">
      <div className={`card ${styles.card}`}>
        <p className="card-kicker">發生錯誤</p>
        <p className="card-title">目前拿不到比價資料</p>
        <p className="card-body">
          後端服務可能正在重新啟動，或這個查詢它看不懂。稍後再試一次，問題持續的話請確認
          後端是否在 http://localhost:8080 執行中。
        </p>

        <button type="button" className="btn btn-secondary" onClick={reset}>
          重新載入
        </button>
      </div>
    </div>
  );
}
