import Link from "next/link";
import { Plus, Star } from "lucide-react";
import { Blueprint, BlueprintCorners } from "@/components/Blueprint";
import { SearchForm } from "@/components/SearchForm";
import { listChannels, searchWorks, type Channel, type WorkSummary } from "@/lib/api";
import styles from "./search.module.css";

/** The 分類 facet group, fixed by the design rather than derived. */
const CATEGORIES = ["心理勵志", "人文史地", "藝術設計"];

/** 呈現方式. 卡片 and 表格 arrive with the card-and-table work. */
const VIEWS = ["列表", "卡片", "表格"];

const PRICE_CEILING = { min: 150, max: 700, step: 10 };

/** Only the first four 通路 pairs are shown; the rest live in the detail screen. */
const MAX_CHANNEL_PAIRS = 4;

type SearchPageProps = {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const query = firstValue(params.q);
  const format = firstValue(params.format);

  const [results, channels] = await Promise.all([
    searchWorks(query, format),
    listChannels(),
  ]);

  return (
    <>
      <SearchForm variant="bar" query={query} format={format} />

      <div className={styles.layout}>
        <FacetRail channels={channels} works={results.works} />

        <section className={styles.results}>
          <header className={styles.head}>
            <div>
              <h3 className={styles.heading}>
                {query ? `「${query}」比價結果` : "全部收錄書籍"}
              </h3>
              <p className={styles.sub}>
                共 {results.total} 筆 · 六家通路 · 取價時間{" "}
                {formatFetchedAt(results.fetchedAt)}
              </p>
            </div>

            <div className={styles.viewSwitch}>
              <span className={styles.viewLabel}>呈現方式</span>
              {/* 卡片 and 表格 are wired up by the card-and-table work. */}
              <div className="seg">
                {VIEWS.map((view) => (
                  <label key={view} className="seg-opt">
                    <input
                      type="radio"
                      name="view"
                      value={view}
                      defaultChecked={view === "列表"}
                    />
                    {view}
                  </label>
                ))}
              </div>
            </div>
          </header>

          {results.works.length === 0 ? (
            <Blueprint className={`card ${styles.empty}`}>
              <p className="card-title">找不到符合的作品</p>
              <p className="card-body">
                換一個書名、作者、出版社或 ISBN 再試一次。
              </p>
            </Blueprint>
          ) : (
            <ol className={styles.list}>
              {results.works.map((work) => (
                <ResultRow key={work.isbn} work={work} />
              ))}
            </ol>
          )}
        </section>
      </div>
    </>
  );
}

/**
 * 通路 and 分類 facets with live counts over the current result set.
 *
 * The counts are real; the controls are not wired yet — selecting a facet, the
 * price ceiling and the active-filter chips all arrive with the facets and
 * pagination work, which is what owns the OR-within/AND-across semantics.
 */
function FacetRail({
  channels,
  works,
}: {
  channels: Channel[];
  works: WorkSummary[];
}) {
  const channelCounts = channels.map((channel) => ({
    label: channel.name,
    count: works.filter((work) =>
      work.channelPrices.some((price) => price.channel === channel.name),
    ).length,
  }));

  const categoryCounts = CATEGORIES.map((category) => ({
    label: category,
    count: works.filter((work) => work.category === category).length,
  }));

  return (
    <aside className={styles.rail}>
      <h6 className={styles.railTitle}>篩選條件</h6>

      <FacetGroup name="通路" options={channelCounts} />
      <FacetGroup name="分類" options={categoryCounts} />

      <div className={styles.ceiling}>
        <label className={styles.ceilingLabel} htmlFor="price-ceiling">
          價格上限 NT$ {PRICE_CEILING.max}
        </label>
        <input
          id="price-ceiling"
          type="range"
          className={styles.range}
          min={PRICE_CEILING.min}
          max={PRICE_CEILING.max}
          step={PRICE_CEILING.step}
          defaultValue={PRICE_CEILING.max}
        />
      </div>

      <button type="button" className="btn btn-secondary btn-block">
        清除篩選
      </button>
    </aside>
  );
}

function FacetGroup({
  name,
  options,
}: {
  name: string;
  options: { label: string; count: number }[];
}) {
  return (
    <div className={styles.facetGroup}>
      <p className={styles.facetName}>{name}</p>
      {options.map((option) => (
        <label key={option.label} className={styles.facet}>
          <input type="checkbox" className={styles.checkbox} name={name} />
          <span className={styles.facetLabel}>{option.label}</span>
          <span className={styles.facetCount}>{option.count}</span>
        </label>
      ))}
    </div>
  );
}

/** One 作品 in 列表 view. */
function ResultRow({ work }: { work: WorkSummary }) {
  const pairs = work.channelPrices.slice(0, MAX_CHANNEL_PAIRS);
  const detailHref = `/works/${work.isbn}`;

  // The cheapest of the pairs actually shown, which is not always the 作品
  // 最低價: that one often belongs to an 電子書 通路 sorted past the fourth column,
  // and the right-hand column states it anyway.
  const cheapestShown = Math.min(...pairs.map((pair) => pair.price));

  return (
    <li className={styles.row}>
      <div className={`blueprint duotone ${styles.cover}`}>
        <span className={styles.coverLabel}>封面</span>
        <BlueprintCorners />
      </div>

      <div className={styles.body}>
        <h4 className={styles.title}>
          <Link href={detailHref} className={styles.titleLink}>
            {work.title}
          </Link>
        </h4>
        <p className={styles.byline}>{byline(work)}</p>

        <div className={styles.tags}>
          <span className="tag tag-neutral">{work.category}</span>
          <span className="tag tag-outline">定價 {work.listPrice}</span>
          <span className="tag tag-accent">{work.channelCount} 個通路有貨</span>
        </div>

        <div className={styles.pairs}>
          {pairs.map((pair) => (
            // A 通路 selling both 載體 of one 作品 (博客來 and 誠品線上 both do)
            // yields two rows for the same name, so the 載體 is part of the key.
            <div key={`${pair.channel}-${pair.format}`} className={styles.pair}>
              <span className={styles.pairChannel}>{pair.channel}</span>
              <span
                className={
                  pair.price === cheapestShown
                    ? `${styles.pairPrice} ${styles.pairPriceBest}`
                    : styles.pairPrice
                }
              >
                {pair.price}
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className={styles.aside}>
        <p className={styles.bestLabel}>最低價 · {work.bestPrice?.channel}</p>
        <p className={styles.bestPrice}>NT$ {work.bestPrice?.price}</p>
        <p className={styles.bestMeta}>
          {/* 折扣 is absent when 售價 is at or above 定價. */}
          {work.bestPrice?.discountLabel
            ? `約 ${work.bestPrice.discountLabel}｜`
            : ""}
          共 {work.channelCount} 個通路
        </p>

        {/* 追蹤 becomes a real toggle with the watch-list work. */}
        <button type="button" className={`btn btn-secondary ${styles.watch}`}>
          <Plus size={16} strokeWidth={1.5} className={styles.watchIconWide} />
          <Star size={16} strokeWidth={1.5} className={styles.watchIconNarrow} />
          <span className={styles.watchLabel}>追蹤價格</span>
        </button>

        <Link href={detailHref} className={`btn btn-primary blueprint ${styles.detail}`}>
          <span className={styles.detailWide}>看全部報價</span>
          <span className={styles.detailNarrow}>看 {work.channelCount} 個報價</span>
          <BlueprintCorners />
        </Link>
      </div>
    </li>
  );
}

/** The design writes this as 「作者 / 出版社, 出版年」. */
function byline(work: WorkSummary): string {
  return `${work.author} / ${work.publisher}, ${work.publicationYear}`;
}

/**
 * 取價時間 in Taipei time whichever machine renders this, so the stamp does not
 * shift with the server locale.
 */
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

/** A query string can repeat a key; the screens only ever mean the first one. */
function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
