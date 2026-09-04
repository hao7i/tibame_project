import Link from "next/link";
import { Blueprint } from "@/components/Blueprint";
import { channelSwatch } from "@/lib/channels";
import { currentMember } from "@/lib/session";
import { listWatchItems, type WatchItem } from "@/lib/watchlist";
import { clearWatchList } from "@/lib/watch-actions";
import { TargetPriceField, RemoveButton } from "./WatchControls";
import styles from "./watch.module.css";

/**
 * 追蹤清單 — 會員專屬.
 *
 * 目前最低價 is whatever the 報價 say at the moment this page is drawn, not a
 * number copied in when the book was tracked: a 追蹤清單 that showed the old
 * price would be reporting the one thing it exists to watch.
 */
export default async function WatchPage() {
  const member = await currentMember();

  if (!member) {
    return <LockedCard />;
  }

  const items = await listWatchItems();

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h6 className={styles.kicker}>會員專屬</h6>
          <h2 className={styles.heading}>追蹤清單</h2>
          <p className={styles.sub}>
            {items.length} 本書 · 價格每 6 小時更新 · 達目標價時 email 通知
          </p>
        </div>

        <div className={styles.headActions}>
          <Link href="/search" className="btn btn-secondary">
            繼續搜尋書籍
          </Link>
          {items.length > 0 ? (
            <form action={clearWatchList}>
              <button type="submit" className="btn btn-secondary">
                清空清單
              </button>
            </form>
          ) : null}
        </div>
      </header>

      {items.length === 0 ? <EmptyCard /> : <WatchTable items={items} />}
    </div>
  );
}

/** 未登入: the design answers with one card, not an empty table. */
function LockedCard() {
  return (
    <div className={styles.page}>
      <Blueprint className={`card ${styles.locked}`}>
        <span className="card-kicker">會員專屬功能</span>
        <span className="card-title">追蹤清單需要登入</span>
        <p className="card-body">
          登入後可追蹤書籍、設定目標價，並在低於目標價時收到 email 通知。
        </p>
        <Link href="/login" className={`btn btn-primary ${styles.lockedCta}`}>
          登入 / 註冊
        </Link>
      </Blueprint>
    </div>
  );
}

function EmptyCard() {
  return (
    <Blueprint className={`card ${styles.empty}`}>
      <span className="card-kicker">清單是空的</span>
      <span className="card-title">還沒有追蹤任何書籍</span>
      <p className="card-body">
        在搜尋結果或單書比價頁按「＋ 追蹤價格」，就會出現在這裡，並可設定目標價。
      </p>
      <Link href="/search" className={`btn btn-primary ${styles.emptyCta}`}>
        前往搜尋
      </Link>
    </Blueprint>
  );
}

function WatchTable({ items }: { items: WatchItem[] }) {
  return (
    <>
      {/* Seven columns do not fit a narrow screen, so the table scrolls in its
          own box and the stacked cards below take over under 860px. */}
      <div className={styles.tableScroll}>
        <table className={`table ${styles.table}`}>
          <thead>
            <tr>
              <th scope="col">書名</th>
              <th scope="col">作者／出版</th>
              <th scope="col" className={styles.numeric}>
                目前最低價
              </th>
              <th scope="col">通路</th>
              <th scope="col" className={styles.targetColumn}>
                目標價
              </th>
              <th scope="col">通知狀態</th>
              <th scope="col" aria-label="操作" />
            </tr>
          </thead>

          <tbody>
            {items.map((item) => (
              <tr key={item.isbn} className={item.status === "REACHED" ? styles.reached : ""}>
                <td className={styles.title}>
                  <Link href={`/works/${item.isbn}`} className={styles.titleLink}>
                    {item.title}
                  </Link>
                </td>
                <td className={styles.byline}>
                  {item.author} / {item.publisher}, {item.publicationYear}
                </td>
                <td className={`${styles.numeric} ${styles.best}`}>
                  NT$ {item.bestPrice.price}
                </td>
                <td className={styles.channel}>
                  <span
                    className={styles.swatch}
                    style={{ background: channelSwatch(item.bestPrice.channelCode) }}
                    aria-hidden="true"
                  />
                  {item.bestPrice.channel}
                </td>
                <td>
                  <TargetPriceField isbn={item.isbn} targetPrice={item.targetPrice} />
                </td>
                <td>
                  <StatusTag item={item} />
                </td>
                <td className={styles.rowActions}>
                  <Link href={`/works/${item.isbn}`} className="btn btn-ghost">
                    比價
                  </Link>
                  <RemoveButton isbn={item.isbn} title={item.title} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className={styles.cards}>
        {items.map((item) => (
          <li
            key={item.isbn}
            className={`${styles.card} ${item.status === "REACHED" ? styles.reached : ""}`}
          >
            <Link href={`/works/${item.isbn}`} className={`${styles.title} ${styles.titleLink}`}>
              {item.title}
            </Link>
            <p className={styles.byline}>
              {item.author} / {item.publisher}, {item.publicationYear}
            </p>
            <p className={styles.cardPrice}>
              NT$ {item.bestPrice.price}
              <span className={styles.cardShop}>{item.bestPrice.channel}</span>
            </p>
            <StatusTag item={item} />
            <div className={styles.cardActions}>
              <TargetPriceField isbn={item.isbn} targetPrice={item.targetPrice} />
              <RemoveButton isbn={item.isbn} title={item.title} />
            </div>
          </li>
        ))}
      </ul>

      <p className={styles.footnote}>
        目標價達成時會寄送 email 通知。價格為示意資料，每 6 小時更新一次。
      </p>
    </>
  );
}

/** The three states the design names, and only those three. */
function StatusTag({ item }: { item: WatchItem }) {
  if (item.status === "NO_TARGET") {
    return <span className="tag tag-neutral">未設目標價</span>;
  }
  if (item.status === "REACHED") {
    return <span className="tag tag-accent">已達目標價</span>;
  }
  return <span className="tag tag-neutral">尚差 NT$ {item.gap}</span>;
}
