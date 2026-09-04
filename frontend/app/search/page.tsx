import Link from "next/link";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { Blueprint } from "@/components/Blueprint";
import { SearchForm } from "@/components/SearchForm";
import { listFacets, searchWorks, type Facets, type WorkSummary } from "@/lib/api";
import { FacetRail } from "./FacetRail";
import { WatchToggle } from "@/components/WatchToggle";
import { listWatchItems } from "@/lib/watchlist";
import { currentMember } from "@/lib/session";
import { ViewSwitch } from "./ViewSwitch";
import { DEFAULT_VIEW, PRICE_CEILING, parseView, type ViewValue } from "@/lib/filters";
import styles from "./search.module.css";

/** Only the first four 通路 pairs are shown; the rest live in the detail screen. */
const MAX_CHANNEL_PAIRS = 4;

type SearchPageProps = {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;

  const query = firstValue(params.q);
  const channels = allValues(params.channel);
  const maxPrice = firstValue(params.maxPrice);
  const page = firstValue(params.page);
  const view = parseView(firstValue(params.view));

  // 進階搜尋 conditions travel as their own parameters. This screen does not
  // render controls for them — the 進階搜尋 form owns that — so it passes them
  // straight through and only says, in the heading, that they are in force.
  const advanced = {
    minPrice: firstValue(params.minPrice),
    year: firstValue(params.year),
    field1: firstValue(params.field1),
    term1: firstValue(params.term1),
    op: firstValue(params.op),
    field2: firstValue(params.field2),
    term2: firstValue(params.term2),
  };
  const searchingAdvanced = Object.values(advanced).some(Boolean);

  const [results, facets] = await Promise.all([
    searchWorks({
      q: query,
      channel: channels,
      maxPrice,
      page,
      ...advanced,
    }),
    listFacets(),
  ]);

  // 追蹤中 state for the toggles. One read for the whole page rather than one
  // per row, and an empty set when nobody is signed in.
  const member = await currentMember();
  const watchedIsbns = new Set(
    member ? (await listWatchItems()).map((item) => item.isbn) : [],
  );

  const chips = activeChips(
    { channels, maxPrice, query, view, advanced },
    facets,
  );

  return (
    <>
      <SearchForm
        variant="bar"
        query={query}
        filters={{ channels, maxPrice, view }}
      />

      <div className={styles.layout}>
        <FacetRail facets={facets} />

        <section className={styles.results}>
          <header className={styles.head}>
            <div>
              <h3 className={styles.heading}>
                {query
                  ? `「${query}」比價結果`
                  : searchingAdvanced
                    ? "進階搜尋結果"
                    : "全部收錄書籍"}
              </h3>
              <p className={styles.sub}>
                共 {results.total} 筆 · 五家通路 · 取價時間{" "}
                {formatFetchedAt(results.fetchedAt)}
              </p>
            </div>

            <ViewSwitch />
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
            // All three views read the same 作品 the server already narrowed, so
            // 篩選條件 hold whichever one is showing.
            <ResultBody
              view={view}
              works={results.works}
              channels={facets.channels}
              watchedIsbns={watchedIsbns}
            />
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

/** The 作品 drawn in whichever 呈現方式 is active. */
function ResultBody({
  view,
  works,
  channels,
  watchedIsbns,
}: {
  view: ViewValue;
  works: WorkSummary[];
  channels: Facets["channels"];
  watchedIsbns: Set<string>;
}) {
  if (view === "cards") {
    return (
      <div className={styles.cards}>
        {works.map((work) => (
          <ResultCard key={work.isbn} work={work} watched={watchedIsbns.has(work.isbn)} />
        ))}
      </div>
    );
  }

  if (view === "table") {
    return (
      <ResultTable works={works} channels={channels} watchedIsbns={watchedIsbns} />
    );
  }

  return (
    <ol className={styles.list}>
      {works.map((work) => (
        <ResultRow key={work.isbn} work={work} watched={watchedIsbns.has(work.isbn)} />
      ))}
    </ol>
  );
}

/** One 作品 in 卡片 view. */
function ResultCard({ work, watched }: { work: WorkSummary; watched: boolean }) {
  const detailHref = `/works/${work.isbn}`;

  return (
    <Blueprint className={`card ${styles.card}`}>
      {/* Keeps .blueprint for the 1px frame and 定位, but not the 註冊記號. */}
      <div className={`blueprint duotone ${styles.cardCover}`}>
        <span className={styles.coverLabel}>封面</span>
      </div>

      <span className="card-kicker">{work.category}</span>
      <Link href={detailHref} className={`card-title ${styles.cardTitle}`}>
        {work.title}
      </Link>
      <p className="card-body">{byline(work)}</p>

      <div className={styles.cardPrice}>
        <div>
          <p className={styles.cardBestLabel}>最低 · {work.bestPrice?.channel}</p>
          <p className={styles.cardBestPrice}>NT$ {work.bestPrice?.price}</p>
        </div>
        {/* 折扣 is absent when 售價 is at or above 定價. */}
        {work.bestPrice?.discountLabel ? (
          <span className="tag tag-accent">{work.bestPrice.discountLabel}</span>
        ) : null}
      </div>

      <p className="card-meta">
        定價 {work.listPrice} · {work.channelCount} 個通路
      </p>

      <div className={styles.cardActions}>
        <WatchToggle isbn={work.isbn} title={work.title} watched={watched} variant="icon" />
        <Link
          href={detailHref}
          className={`btn btn-primary ${styles.cardCompare}`}
        >
          比價
        </Link>
      </div>
    </Blueprint>
  );
}

/**
 * 表格 view — one column per 通路, so a price can be read down a column as well
 * as across a row, which is the whole point of this view.
 *
 * The columns are every 通路 the 書目 carries, not merely the ones on this page:
 * a column that came and went between pages would make the table unreadable.
 */
function ResultTable({
  works,
  channels,
  watchedIsbns,
}: {
  works: WorkSummary[];
  channels: Facets["channels"];
  watchedIsbns: Set<string>;
}) {
  return (
    // Nine columns do not fit 390px, so the table scrolls inside its own box
    // rather than pushing the whole page sideways.
    <div className={styles.tableScroll}>
      {/* The design system's .table supplies the base; the dark header and the
          numeric alignment come from .tableScroll table in this module. */}
      <table className="table">
        <thead>
          <tr>
            <th scope="col">書名</th>
            <th scope="col">作者／出版</th>
            {channels.map((channel) => (
              <th key={channel.code} scope="col" className={styles.numeric}>
                {channel.name}
              </th>
            ))}
            <th scope="col" className={styles.numeric}>
              最低價
            </th>
            <th scope="col">追蹤</th>
          </tr>
        </thead>

        <tbody>
          {works.map((work) => (
            <tr key={work.isbn}>
              <td className={styles.tableTitle}>
                <Link href={`/works/${work.isbn}`} className={styles.titleLink}>
                  {work.title}
                </Link>
              </td>
              <td className={styles.tableByline}>{byline(work)}</td>

              {channels.map((channel) => {
                const price = priceAt(work, channel.code);
                const best = price !== null && price === work.bestPrice?.price;

                return (
                  <td
                    key={channel.code}
                    className={`${styles.numeric} ${
                      price === null
                        ? styles.noOffer
                        : best
                          ? styles.bestCell
                          : ""
                    }`}
                  >
                    {price === null ? "—" : `NT$ ${price}`}
                  </td>
                );
              })}

              <td className={`${styles.numeric} ${styles.tableBest}`}>
                NT$ {work.bestPrice?.price}
              </td>
              <td>
                <WatchToggle
                  isbn={work.isbn}
                  title={work.title}
                  watched={watchedIsbns.has(work.isbn)}
                  variant="icon"
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * 售價 of one 通路 for one 作品, or null when it carries no 報價 — which is also
 * what a 通路 excluded by the 篩選條件 reads as, since its 報價 no longer counts.
 *
 * One 報價 per 通路 per 作品, so the cell is that single price.
 */
function priceAt(work: WorkSummary, channelCode: string): number | null {
  const prices = work.channelPrices
    .filter((entry) => entry.channelCode === channelCode)
    .map((entry) => entry.price);

  return prices.length === 0 ? null : Math.min(...prices);
}

/** One 作品 in 列表 view. */
function ResultRow({ work, watched }: { work: WorkSummary; watched: boolean }) {
  const pairs = work.channelPrices.slice(0, MAX_CHANNEL_PAIRS);
  const detailHref = `/works/${work.isbn}`;

  // The cheapest of the pairs actually shown, which is not always the 作品
  // 最低價: that one can sit past the fourth column, and the right-hand column
  // states it anyway.
  const cheapestShown = Math.min(...pairs.map((pair) => pair.price));

  return (
    <li className={styles.row}>
      <div className={`blueprint duotone ${styles.cover}`}>
        <span className={styles.coverLabel}>封面</span>
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
            <div key={pair.channel} className={styles.pair}>
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

        <WatchToggle
          isbn={work.isbn}
          title={work.title}
          watched={watched}
          className={styles.watch}
        />

        <Link href={detailHref} className={`btn btn-primary ${styles.detail}`}>
          <span className={styles.detailWide}>看全部報價</span>
          <span className={styles.detailNarrow}>看 {work.channelCount} 個報價</span>
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
    maxPrice?: string;
    query?: string;
    view: ViewValue;
    advanced: Record<string, string | undefined>;
  },
  facets: Facets,
): Chip[] {
  // 搜尋 term and 呈現方式 are not 篩選條件, so every chip link carries them
  // onward; page is always dropped, since removing a filter can shrink the
  // page count.
  const hrefWithout = (key: string, value: string) => {
    const params = new URLSearchParams();
    if (selection.query) {
      params.set("q", selection.query);
    }
    if (selection.view !== DEFAULT_VIEW) {
      params.set("view", selection.view);
    }
    // Removing a 通路 must not quietly widen the 進階搜尋 條件 as well.
    for (const [name, value] of Object.entries(selection.advanced)) {
      if (value) {
        params.set(name, value);
      }
    }
    for (const code of selection.channels) {
      if (!(key === "channel" && code === value)) {
        params.append("channel", code);
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
