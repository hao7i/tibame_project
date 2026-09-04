import styles from "./work.module.css";

/**
 * 單書比價 的骨架畫面.
 *
 * This screen prices six 通路 before it can draw its table, which is the longest
 * wait in the site. The skeleton keeps the three-column shape so the cover, the
 * offer table and the side cards do not shuffle into place one after another.
 */
export default function Loading() {
  return (
    <div className={styles.page} aria-busy="true" aria-live="polite">
      <span className="sr-only">正在取得各通路報價…</span>

      <div className={styles.layout}>
        <div className={`${styles.skeleton} ${styles.skeletonCover}`} />

        <div>
          <div className={`${styles.skeleton} ${styles.skeletonHeading}`} />
          <div className={`${styles.skeleton} ${styles.skeletonSub}`} />

          {Array.from({ length: 6 }, (_, index) => (
            <div key={index} className={`${styles.skeleton} ${styles.skeletonRow}`} />
          ))}
        </div>

        <div className={styles.side}>
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className={`${styles.skeleton} ${styles.skeletonCard}`} />
          ))}
        </div>
      </div>
    </div>
  );
}
