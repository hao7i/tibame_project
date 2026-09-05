"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { FormEvent } from "react";
import styles from "./SearchForm.module.css";

/**
 * The 搜尋 entry point, in the two shapes the design draws it: the tall row in
 * the 首頁 hero and the compact bar above the 搜尋結果.
 *
 * Still a real GET form pointed at /search, so the URL remains the whole state
 * and 搜尋 keeps working with no client JavaScript at all. What the submit
 * handler adds is that, when JavaScript is there, the browser navigates through
 * the router instead of loading a whole new document.
 *
 * That distinction matters because of 找書: it runs for about a minute inside
 * the page that started it, and a full document load tears that page down — the
 * run is cut off and the notification never arrives. Router navigation keeps the
 * LookupProvider in the layout alive, so the reader can go on searching while a
 * 找書 finishes. Every other way off this screen (熱門搜尋 tags, chips, 分頁,
 * 進階搜尋) already navigates client-side; this form was the one exception.
 */
type SearchFormProps = {
  /** hero = 首頁 band; bar = the row above 搜尋結果. */
  variant: "hero" | "bar";
  query?: string;
  /** Active 篩選條件, carried through a new 搜尋 from the results bar. */
  filters?: {
    channels?: string[];
    maxPrice?: string;
    /** 呈現方式; omitted when it is the default 列表. */
    view?: string;
  };
};

export function SearchForm({ variant, query = "", filters }: SearchFormProps) {
  const router = useRouter();
  const isHero = variant === "hero";

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    // Read from the form itself rather than from props: the hidden 篩選條件 and
    // the typed 書名 are then gathered by exactly the rule the plain GET
    // fallback would use, so the two paths cannot drift apart.
    const params = new URLSearchParams();
    for (const [key, value] of new FormData(event.currentTarget).entries()) {
      if (typeof value === "string" && value.trim()) {
        params.append(key, value.trim());
      }
    }

    const queryString = params.toString();
    router.push(queryString ? `/search?${queryString}` : "/search");
  };

  return (
    <form
      action="/search"
      method="get"
      onSubmit={submit}
      className={isHero ? styles.hero : styles.bar}
      role="search"
    >
      {isHero ? null : (
        <>
          {/* The bar has no 篩選條件 of its own, so it carries them forward.
              Without this a new 搜尋 would silently clear the rail and the
              chips; the design only calls for 分頁 to reset. */}
          {(filters?.channels ?? []).map((code) => (
            <input key={`channel-${code}`} type="hidden" name="channel" value={code} />
          ))}
          {filters?.maxPrice ? (
            <input type="hidden" name="maxPrice" value={filters.maxPrice} />
          ) : null}
          {filters?.view && filters.view !== "list" ? (
            <input type="hidden" name="view" value={filters.view} />
          ) : null}
        </>
      )}

      <div className={styles.row}>
        {/* 書名 only, though the API also matches 作者, 出版社 and ISBN: the box
            advertises what it is for, and 進階搜尋 owns 逐欄位 searching.
            Deliberately narrower than design/README.md, which writes
            「書名、作者、出版社或 ISBN」 here. */}
        <input
          type="search"
          name="q"
          className={`input ${styles.field}`}
          placeholder="書名"
          defaultValue={query}
          aria-label="以書名搜尋"
        />

        {/* No .blueprint here: the 註冊記號 belong to the 書封 佔位框, and on a
            solid accent button the four corner marks read as damage rather than
            as register marks. .btn already supplies the 1px square border. */}
        <button type="submit" className={`btn btn-primary ${styles.submit}`}>
          比價
        </button>

        <Link
          href="/advanced"
          className={`btn ${isHero ? "btn-secondary" : "btn-ghost"} ${styles.advanced}`}
        >
          進階搜尋
        </Link>
      </div>
    </form>
  );
}
