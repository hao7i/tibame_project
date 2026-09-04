import { Blueprint } from "@/components/Blueprint";
import styles from "./page.module.css";

/**
 * Placeholder home page. The real 首頁 — hero band, search row, hot searches,
 * 收錄通路 strip, 運作方式 card — is built in the catalogue/search ticket.
 * What is here exercises the ported design system so the shell can be
 * eyeballed at both renditions.
 */
export default function Home() {
  return (
    <div className={styles.page}>
      <p className="card-kicker">六大通路一次比</p>
      <h2>輸入書名，看它在台灣各網路書店賣多少</h2>

      <Blueprint className={`card ${styles.notice}`}>
        <p className="card-kicker">目前狀態</p>
        <p className="card-title">設計系統已就緒</p>
        <p className="card-body">
          字型、色彩、間距與元件樣式已從 Industry 設計系統移植完成，字型由本站自行提供，未使用任何 CDN。
          首頁的實際內容於書目與搜尋票中建置。
        </p>
        <div className={styles.row}>
          <span className="tag tag-accent">作品</span>
          <span className="tag tag-neutral">版本</span>
          <span className="tag tag-outline">報價</span>
        </div>
        <div className={styles.row}>
          <button type="button" className="btn btn-primary">
            比價
          </button>
          <button type="button" className="btn btn-secondary">
            進階搜尋
          </button>
          <button type="button" className="btn btn-ghost">
            清除篩選
          </button>
        </div>
      </Blueprint>
    </div>
  );
}
