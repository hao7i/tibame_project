import styles from "./SiteFooter.module.css";

/**
 * Desktop only, per the design: hidden at the mobile rendition.
 *
 * The 收錄通路 / 意見回饋 / 資料來源說明 links the design draws here were removed
 * on request. 收錄通路 is still reachable from the 導覽列; the other two routes
 * remain but are no longer linked from any page.
 */
export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <span className={styles.note}>
          BOOKPRICE.TW · 價格每 6 小時更新一次
        </span>
      </div>
    </footer>
  );
}
