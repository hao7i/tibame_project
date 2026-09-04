import Link from "next/link";
import { ChevronLeft, ChevronRight, Plus, Star, X } from "lucide-react";
import { Blueprint, BlueprintCorners } from "@/components/Blueprint";
import { SearchForm } from "@/components/SearchForm";
import { listFacets, searchWorks, type Facets, type WorkSummary } from "@/lib/api";
import { FacetRail } from "./FacetRail";
import { PRICE_CEILING } from "@/lib/filters";
import styles from "./search.module.css";

/** 呈現方式. 卡片 and 表格 arrive with the card-and-table work. */
const VIEWS = ["列表", "卡片", "表格"];

/** Only the first four 通路 pairs are shown; the rest live in the detail screen. */
const MAX_CHANNEL_PAIRS = 4;

type SearchPageProps = {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;

  const query = firstValue(params.q);
  const format = firstValue(params.format);
  const channels = allValues(params.channel);
  const categories = allValues(params.category);
  const maxPrice = firstValue(params.maxPrice);
  const page = firstValue(params.page);

  const [results, facets] = await Promise.all([
    searchWorks({ q: query, format, channel: channels, category: categories, maxPrice, page }),
    listFacets(format),
  ]);

  const chips = activeChips(
    { channels, categories, maxPrice, query, format },
    facets,
  );

  return (
    <>
      <SearchForm
        variant="bar"
        query={query}
        format={format}
        filters={{ channels, categories, maxPrice }}
      />

      <div className={styles.layout}>
        <FacetRail facets={facets} />

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

          {chips.length > 0 ? (
            <div className={styles.chips}>
              {chips.map((chip) => (
                <Link
                  key={chip.key}
                  href={chip.href}
                  className={`tag tag-accent ${styles.chip}`}
                  aria-label={`移除篩選條件 ${chip.label}`}
                >
                  {chip.label}
                  <X size={12} strokeWidth={1.5} />
                </Link>
              ))}
            </div>
          ) : null}

          {results.works.length === 0 ? (
            <Blueprint className={`card ${styles.empty}`}>
              <p className="card-title">找不到符合的作品</p>
              <p className="card-body">
                換一個書名、作者、出版社或 ISBN 再試一次，或放寬左側的篩選條件。
              </p>
            </Blueprint>
          ) : (
            <ol className={styles.list}>
              {results.works.map((work) => (
                <ResultRow key={work.isbn} work={work} />
              ))}
            </ol>
          )}

          {results.totalPages > 1 ? (
            <Pagination
              page={results.page}
              totalPages={results.totalPages}
              params={params}
            />
          ) : null}
        </section>
      </div>
    </>
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

function Pagination({
  page,
  totalPages,
  params,
}: {
  page: number;
  totalPages: number;
  params: { [key: string]: string | string[] | undefined };
}) {
  const pageHref = (target: number) => {
    const next = toParams(params);
    if (target <= 1) {
      next.delete("page");
    } else {
      next.set("page", String(target));
    }
    const queryString = next.toString();
    return queryString ? `/search?${queryString}` : "/search";
  };

  const pages = Array.from({ length: totalPages }, (_, index) => index + 1);

  return (
    <nav className={styles.pagination} aria-label="分頁">
      {page > 1 ? (
        <Link href={pageHref(page - 1)} className="btn btn-secondary">
          <ChevronLeft size={16} strokeWidth={1.5} />
          上一頁
        </Link>
      ) : (
        <button type="button" className="btn btn-secondary" disabled>
          <ChevronLeft size={16} strokeWidth={1.5} />
          上一頁
        </button>
      )}

      {pages.map((target) => (
        <Link
          key={target}
          href={pageHref(target)}
          aria-current={target === page ? "page" : undefined}
          className={`btn btn-secondary ${styles.pageLink} ${
            target === page ? styles.pageCurrent : ""
          }`}
        >
          {target}
        </Link>
      ))}

      {page < totalPages ? (
        <Link href={pageHref(page + 1)} className="btn btn-secondary">
          下一頁
          <ChevronRight size={16} strokeWidth={1.5} />
        </Link>
      ) : (
        <button type="button" className="btn btn-secondary" disabled>
          下一頁
          <ChevronRight size={16} strokeWidth={1.5} />
        </button>
      )}

      <span className={styles.pageCount}>
        第 {page} 頁 / 共 {totalPages} 頁
      </span>
    </nav>
  );
}

type Chip = { key: string; label: string; href: string };

/**
 * The active-filter chips, which mirror the rail and remove one condition each.
 *
 * They are plain links rather than part of the client rail: removing a filter is
 * a one-shot navigation, and both read the same URL, so they cannot drift apart.
 */
function activeChips(
  selection: {
    channels: string[];
    categories: string[];
    maxPrice?: string;
    query?: string;
    format?: string;
  },
  facets: Facets,
): Chip[] {
  // 搜尋 term and 載體 are not 篩選條件, so every chip link carries them onward;
  // page is always dropped, since removing a filter can shrink the page count.
  const hrefWithout = (key: string, value: string) => {
    const params = new URLSearchParams();
    if (selection.query) {
      params.set("q", selection.query);
    }
    if (selection.format) {
      params.set("format", selection.format);
    }
    for (const code of selection.channels) {
      if (!(key === "channel" && code === value)) {
        params.append("channel", code);
      }
    }
    for (const name of selection.categories) {
      if (!(key === "category" && name === value)) {
        params.append("category", name);
      }
    }
    if (selection.maxPrice && key !== "maxPrice") {
      params.set("maxPrice", selection.maxPrice);
    }

    const queryString = params.toString();
    return queryString ? `/search?${queryString}` : "/search";
  };

  const chips: Chip[] = [];

  for (const code of selection.channels) {
    const name = facets.channels.find((channel) => channel.code === code)?.name ?? code;
    chips.push({
      key: `channel-${code}`,
      label: name,
      href: hrefWithout("channel", code),
    });
  }

  for (const name of selection.categories) {
    chips.push({
      key: `category-${name}`,
      label: name,
      href: hrefWithout("category", name),
    });
  }

  if (selection.maxPrice && Number(selection.maxPrice) < PRICE_CEILING.max) {
    chips.push({
      key: "maxPrice",
      label: `價格上限 NT$ ${selection.maxPrice}`,
      href: hrefWithout("maxPrice", selection.maxPrice),
    });
  }

  return chips;
}

function toParams(params: {
  [key: string]: string | string[] | undefined;
}): URLSearchParams {
  const result = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (Array.isArray(value)) {
      for (const entry of value) {
        result.append(key, entry);
      }
    } else if (value !== undefined) {
      result.set(key, value);
    }
  }
  return result;
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

/** Facet groups are multi-select, so every value counts. */
function allValues(value: string | string[] | undefined): string[] {
  if (value === undefined) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}
