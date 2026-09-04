import Link from "next/link";
import { BlueprintCorners } from "@/components/Blueprint";
import styles from "./SearchForm.module.css";

/**
 * The 搜尋 entry point, in the two shapes the design draws it: the tall row in
 * the 首頁 hero and the compact bar above the 搜尋結果.
 *
 * It is a plain GET form, so 搜尋 works with no client JavaScript at all and the
 * resulting URL is the whole state — which is what the results screen reads.
 */
const FORMAT_OPTIONS = [
  { label: "全部版本", value: "" },
  { label: "紙本書", value: "PAPER" },
  { label: "電子書", value: "EBOOK" },
];

type SearchFormProps = {
  /** hero = 首頁 band with the 載體 switcher; bar = the row above 搜尋結果. */
  variant: "hero" | "bar";
  query?: string;
  format?: string;
};

export function SearchForm({ variant, query = "", format = "" }: SearchFormProps) {
  const isHero = variant === "hero";

  return (
    <form
      action="/search"
      method="get"
      className={isHero ? styles.hero : styles.bar}
      role="search"
    >
      {isHero ? (
        <div className={`seg ${styles.formats}`}>
          {FORMAT_OPTIONS.map((option) => (
            <label key={option.label} className="seg-opt">
              <input
                type="radio"
                name="format"
                value={option.value}
                defaultChecked={option.value === format}
              />
              {option.label}
            </label>
          ))}
        </div>
      ) : (
        // The bar has no 載體 switcher, so it carries the current one forward
        // rather than silently widening the search back to 全部版本.
        <input type="hidden" name="format" value={format} />
      )}

      <div className={styles.row}>
        <input
          type="search"
          name="q"
          className={`input ${styles.field}`}
          placeholder="書名、作者、出版社或 ISBN"
          defaultValue={query}
          aria-label="搜尋書名、作者、出版社或 ISBN"
        />

        <button type="submit" className={`btn btn-primary blueprint ${styles.submit}`}>
          比價
          <BlueprintCorners />
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
