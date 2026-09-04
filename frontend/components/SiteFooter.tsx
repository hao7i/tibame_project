import Link from "next/link";
import styles from "./SiteFooter.module.css";

const FOOTER_LINKS = [
  { href: "/channels", label: "收錄通路" },
  { href: "/feedback", label: "意見回饋" },
  { href: "/sources", label: "資料來源說明" },
];

/** Desktop only, per the design: hidden at the mobile rendition. */
export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <span className={styles.note}>
          BOOKPRICE.TW · 價格每 6 小時更新一次
        </span>
        <nav className={styles.links}>
          {FOOTER_LINKS.map(({ href, label }) => (
            <Link key={href} href={href} className={styles.link}>
              {label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
