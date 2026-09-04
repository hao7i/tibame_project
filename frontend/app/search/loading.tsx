import styles from "./search.module.css";

/**
 * 搜尋結果 的骨架畫面.
 *
 * The design asks for a skeleton rather than a spinner because this page is
 * fetched per request: the 書目 is read and six 通路 are priced before anything
 * can be drawn. The blocks below stand where the real rows will stand, so the
 * layout does not jump when they arrive.
 */
export default function Loading() {
  return (
    <div className={styles.layout} aria-busy="true" aria-live="polite">
      <span className="sr-only">正在取得各通路價格…</span>

      <aside className={styles.rail}>
        <div className={`${styles.skeleton} ${styles.skeletonTitle}`} />
        {Array.from({ length: 8 }, (_, index) => (
          <div key={index} className={`${styles.skeleton} ${styles.skeletonFacet}`} />
        ))}
      </aside>

      <section className={styles.results}>
        <div className={`${styles.skeleton} ${styles.skeletonHeading}`} />
        <div className={`${styles.skeleton} ${styles.skeletonSub}`} />

        <ol className={styles.list}>
          {Array.from({ length: 4 }, (_, index) => (
            <li key={index} className={styles.row}>
              <div className={`${styles.skeleton} ${styles.cover}`} />
              <div className={styles.body}>
                <div className={`${styles.skeleton} ${styles.skeletonHeading}`} />
                <div className={`${styles.skeleton} ${styles.skeletonSub}`} />
                <div className={`${styles.skeleton} ${styles.skeletonTags}`} />
              </div>
              <div className={styles.aside}>
                <div className={`${styles.skeleton} ${styles.skeletonPrice}`} />
              </div>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
