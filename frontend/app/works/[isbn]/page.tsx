import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { fetchWorkDetail, type OfferView, type WorkDetail } from "@/lib/api";
import { channelSwatch } from "@/lib/channels";
import { currentMember } from "@/lib/session";
import { listWatchItems } from "@/lib/watchlist";
import { WatchToggle } from "@/components/WatchToggle";
import { DropNotificationCard } from "./DropNotificationCard";
import styles from "./work.module.css";

const SORT_TABS = [
  { label: "價格低→高", value: "PRICE" },
  { label: "依通路", value: "CHANNEL" },
];

type WorkPageProps = {
  params: Promise<{ isbn: string }>;
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export default async function WorkPage({ params, searchParams }: WorkPageProps) {
  const { isbn } = await params;
  const query = await searchParams;
  const sort = firstValue(query.sort) ?? "PRICE";

  const work = await fetchWorkDetail(isbn, sort);
  if (!work) {
    notFound();
  }

  // 追蹤 state is read per 作品, addressed by the canonical ISBN the response
  // carries rather than the one in the URL, so the two agree.
  // and both must show the same 追蹤中.
  const member = await currentMember();
  const watchItem = member
    ? (await listWatchItems()).find((item) => item.isbn === work.isbn)
    : undefined;
  const watched = watchItem !== undefined;
  const targetPrice = watchItem?.targetPrice;

  return (
    <div className={styles.page}>
      <Link href="/search" className={`btn btn-ghost ${styles.back}`}>
        <ChevronLeft size={16} strokeWidth={1.5} />
        回搜尋結果
      </Link>

      <div className={styles.layout}>
        {/* .blueprint stays: it is the only source of the 封面 框線. No 註冊記號. */}
        <div className={`blueprint duotone ${styles.cover}`}>
          <span className={styles.coverLabel}>封面</span>
        </div>

        <div className={styles.main}>
          <p className={styles.kicker}>{work.category}</p>
          <h2 className={styles.title}>{work.title}</h2>
          <p className={styles.byline}>
            {work.author} / {work.publisher}, {work.publicationYear}
          </p>

          <div className={styles.tags}>
            <span className="tag tag-outline">ISBN {work.isbn}</span>
            <span className="tag tag-neutral">定價 NT$ {work.listPrice}</span>
            <span className="tag tag-accent">{work.channelCount} 個通路有貨</span>
          </div>

          {work.blurb ? <p className={styles.blurb}>{work.blurb}</p> : null}

          <div className={styles.tableHead}>
            <h4 className={styles.tableTitle}>各通路報價</h4>
            <div className={styles.switches}>
              <TabGroup
                tabs={SORT_TABS}
                current={sort}
                hrefFor={(value) => hrefFor(work.isbn, value)}
              />
            </div>
          </div>

          {work.offers.length === 0 ? (
            <p className={styles.noOffers}>目前沒有任何通路的報價。</p>
          ) : (
            <div className={styles.tableScroll}>
              <table className={`table ${styles.offerTable}`}>
                <thead>
                  <tr>
                    <th>通路</th>
                    <th>庫存／到貨</th>
                    <th className={styles.numeric}>折扣</th>
                    <th className={styles.numeric}>售價</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {work.offers.map((offer) => (
                    <OfferRow key={offer.channelCode} offer={offer} />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className={styles.disclaimer}>
            價格為示意資料，實際售價以各通路網站為準。最後更新 {formatFetchedAt(work.fetchedAt)}
          </p>
        </div>

        <aside className={styles.side}>
          <BestPriceCard work={work} watched={watched} />
          <DropNotificationCard
            isbn={work.isbn}
            loggedIn={member !== null}
            targetPrice={targetPrice}
          />
        </aside>
      </div>
    </div>
  );
}

function OfferRow({ offer }: { offer: OfferView }) {
  return (
    <tr
      className={[offer.best ? styles.bestRow : "", offer.stale ? styles.staleRow : ""]
        .filter(Boolean)
        .join(" ")}
    >
      <td>
        <span className={styles.channelCell}>
          <span
            className={styles.swatch}
            style={{ background: channelSwatch(offer.channelCode) }}
            aria-hidden="true"
          />
          <span className={styles.channelName}>{offer.channel}</span>
          {offer.best ? <span className="tag tag-accent">最低價</span> : null}
          {/* A 通路 that could not be read this time. The price stays visible
              because it is still the last thing the shop actually said. */}
          {offer.stale ? <span className="tag tag-neutral">取價失敗</span> : null}
        </span>
      </td>
      <td className={styles.muted}>
        {offer.stale ? "暫時無法取得" : offer.stockStatus}
      </td>
      <td className={styles.numeric}>{offer.discountLabel ?? "—"}</td>
      <td className={`${styles.numeric} ${styles.price} ${offer.best ? styles.bestPriceCell : ""}`}>
        NT$ {offer.price}
        {offer.stale ? <span className={styles.staleNote}>上次取得</span> : null}
      </td>
      <td className={styles.numeric}>
        <BuyLink url={offer.purchaseUrl} label="前往購買" className="btn btn-secondary" />
      </td>
    </tr>
  );
}

function BestPriceCard({ work, watched }: { work: WorkDetail; watched: boolean }) {
  const best = work.bestPrice;

  return (
    <div className={`card ${styles.card}`}>
      <p className="card-kicker">目前最低</p>

      {best ? (
        <>
          <p className={styles.bigPrice}>NT$ {best.price}</p>
          <p className={styles.bestMeta}>
            {best.channel}
            {best.discountLabel ? `｜約 ${best.discountLabel}` : ""}
            ｜較定價省 NT$ {best.savingVsListPrice}
          </p>

          <BuyLink
            url={best.purchaseUrl}
            label={`前往 ${best.channel}`}
            className={`btn btn-primary ${styles.blockButton}`}
          />
        </>
      ) : (
        <p className="card-body">目前沒有報價可以比較。</p>
      )}

      <WatchToggle
        isbn={work.isbn}
        title={work.title}
        watched={watched}
        className={styles.blockButton}
      />
    </div>
  );
}


/**
 * A 通路 without an established link still gets a button, but a disabled one —
 * sending a reader to a URL we guessed would be worse than saying we have none.
 */
function BuyLink({
  url,
  label,
  className,
}: {
  url?: string;
  label: string;
  className: string;
}) {
  if (!url) {
    return (
      <button type="button" className={className} disabled>
        {label}
      </button>
    );
  }

  return (
    <a href={url} className={className} target="_blank" rel="noopener noreferrer">
      {label}
    </a>
  );
}

/** A segmented control built from links, so the page needs no client JavaScript. */
function TabGroup({
  tabs,
  current,
  hrefFor,
}: {
  tabs: { label: string; value: string }[];
  current: string;
  hrefFor: (value: string) => string;
}) {
  return (
    <div className="seg">
      {tabs.map((tab) => (
        <Link
          key={tab.label}
          href={hrefFor(tab.value)}
          aria-current={tab.value === current ? "true" : undefined}
          className={`seg-opt ${styles.tab} ${
            tab.value === current ? styles.tabSelected : ""
          }`}
        >
          {tab.label}
        </Link>
      ))}
    </div>
  );
}

function hrefFor(isbn: string, sort: string): string {
  const params = new URLSearchParams();
  if (sort && sort !== "PRICE") {
    params.set("sort", sort);
  }

  const queryString = params.toString();
  return `/works/${isbn}${queryString ? `?${queryString}` : ""}`;
}

/** 取價時間 in Taipei time, so the stamp does not shift with the server locale. */
function formatFetchedAt(iso?: string): string {
  if (!iso) {
    return "—";
  }

  return new Intl.DateTimeFormat("zh-TW", {
    timeZone: "Asia/Taipei",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
