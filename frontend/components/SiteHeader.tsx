"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import styles from "./SiteHeader.module.css";

const NAV_LINKS = [
  { href: "/", label: "首頁" },
  { href: "/advanced", label: "進階搜尋" },
  { href: "/watch", label: "追蹤清單" },
  { href: "/channels", label: "通路一覽" },
];

type SiteHeaderProps = {
  /** Whether a 會員 is signed in. Wired up in the member-auth ticket. */
  loggedIn?: boolean;
  /** Number of 追蹤 items. Wired up in the watch-list ticket. */
  watchCount?: number;
};

export function SiteHeader({
  loggedIn = false,
  watchCount = 0,
}: SiteHeaderProps) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  const labelFor = (href: string, label: string) =>
    href === "/watch" && watchCount > 0 ? `${label} (${watchCount})` : label;

  const authAction = loggedIn ? (
    <button type="button" className={`btn btn-ghost ${styles.signOut}`}>
      登出
    </button>
  ) : (
    <Link href="/login" className={`btn btn-secondary ${styles.signIn}`}>
      登入
    </Link>
  );

  return (
    <header className={styles.bar}>
      <div className={styles.inner}>
        <button
          type="button"
          className={`btn btn-icon ${styles.hamburger}`}
          aria-label="開啟選單"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <Menu size={20} strokeWidth={1.5} />
        </button>

        <Link href="/" className={styles.brand}>
          <span className={styles.brandName}>書價</span>
          <span className={styles.brandDomain}>BOOKPRICE.TW</span>
        </Link>

        <nav className={styles.links}>
          {NAV_LINKS.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              aria-current={pathname === href ? "page" : undefined}
              className={styles.link}
            >
              {labelFor(href, label)}
            </Link>
          ))}
        </nav>

        <div className={styles.actions}>
          {watchCount > 0 && (
            <span className={styles.watchCount}>追蹤 {watchCount}</span>
          )}
          {authAction}
        </div>
      </div>

      {menuOpen && (
        <nav className={styles.mobileMenu}>
          {NAV_LINKS.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              aria-current={pathname === href ? "page" : undefined}
              className={styles.mobileLink}
              onClick={() => setMenuOpen(false)}
            >
              {labelFor(href, label)}
            </Link>
          ))}
        </nav>
      )}
    </header>
  );
}
