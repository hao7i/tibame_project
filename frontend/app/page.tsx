import Link from "next/link";
import { Blueprint } from "@/components/Blueprint";
import { SearchForm } from "@/components/SearchForm";
import { listChannels } from "@/lib/api";
import { channelTintStyle } from "@/lib/channels";
import styles from "./page.module.css";

/** The five 熱門搜尋 the design names; each runs that 搜尋 straight away. */
const HOT_SEARCHES = [
  "原子習慣",
  "人類大歷史",
  "被討厭的勇氣",
  "設計的設計",
  "如何閱讀一本書",
];

const HOW_IT_WORKS = [
  { step: "01", label: "輸入書名或 ISBN" },
  { step: "02", label: "比較售價與版本" },
  { step: "03", label: "設定目標價追蹤" },
];

/**
 * Rendered per request rather than prerendered. The 收錄通路 strip reads 通路 from
 * the database, which the 管理後台 can edit, and a prerendered page would freeze
 * that at build time — and would make every production build require a running
 * backend.
 */
export const dynamic = "force-dynamic";

export default async function Home() {
  const channels = await listChannels();

  return (
    <>
      <section className={styles.hero}>
        <div className={styles.heroInner}>
          <h6 className={styles.kicker}>六大通路一次比</h6>
          <h2 className={styles.headline}>輸入書名，看它在台灣各網路書店賣多少</h2>

          <SearchForm variant="hero" />

          <div className={styles.hot}>
            <span className={styles.hotLabel}>熱門搜尋</span>
            {HOT_SEARCHES.map((term) => (
              <Link
                key={term}
                href={`/search?q=${encodeURIComponent(term)}`}
                className={`tag tag-outline ${styles.hotTag}`}
              >
                {term}
              </Link>
            ))}
          </div>
        </div>
      </section>

      <div className={styles.below}>
        <section className={styles.strip} aria-label="收錄通路">
          {channels.map((channel) => (
            <div
              key={channel.code}
              className={styles.stripCell}
              style={channelTintStyle(channel.code)}
            >
              <span className={styles.stripName}>{channel.name}</span>
              <span className={styles.stripKind}>{channel.kind}</span>
            </div>
          ))}
        </section>

        <Blueprint className={`card ${styles.method}`}>
          <p className="card-kicker">運作方式</p>
          <ol className={styles.steps}>
            {HOW_IT_WORKS.map(({ step, label }) => (
              <li key={step} className={styles.step}>
                <span className={styles.stepNumber}>{step}</span>
                <span>{label}</span>
              </li>
            ))}
          </ol>
        </Blueprint>
      </div>
    </>
  );
}
