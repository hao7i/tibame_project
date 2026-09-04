"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { signOut } from "@/lib/auth";
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

  const navLink = (
    { href, label }: (typeof NAV_LINKS)[number],
    className: string,
    onClick?: () => void,
  ) => (
    <Link
      key={href}
      href={href}
      aria-current={pathname === href ? "page" : undefined}
      className={className}
      onClick={onClick}
    >
      {/* The count rides on the 追蹤清單 link, which is where the desktop
          design puts it — not in a separate badge. */}
      {href === "/watch" && watchCount > 0 ? `${label} (${watchCount})` : label}
    </Link>
  );

  return (
    <header className={styles.bar}>
      <div className={styles.inner}>
        <button
          type="button"
          className={`btn btn-secondary btn-icon ${styles.onDark} ${styles.hamburger}`}
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
          {NAV_LINKS.map((link) => navLink(link, styles.link))}
        </nav>

        <div className={styles.actions}>
          {/* Mobile only: the design replaces the auth control with a 追蹤 n
              button once the 會員 is signed in, and it routes to the list. */}
          {loggedIn ? (
            <Link
              href="/watch"
              className={`btn btn-secondary ${styles.onDark} ${styles.watchButton}`}
            >
              追蹤 {watchCount}
            </Link>
          ) : null}

          {loggedIn ? (
            // A Server Action, so 登出 destroys the session on the server
            // rather than merely hiding the button.
            <form action={signOut}>
              <button
                type="submit"
                className={`btn btn-ghost ${styles.onDark} ${styles.signOut}`}
              >
                登出
              </button>
            </form>
          ) : (
            <Link
              href="/login"
              className={`btn btn-secondary ${styles.onDark} ${styles.signIn}`}
            >
              登入
            </Link>
          )}
        </div>
      </div>

      {menuOpen && (
        <nav className={styles.mobileMenu}>
          {NAV_LINKS.map((link) =>
            navLink(link, styles.mobileLink, () => setMenuOpen(false)),
          )}
        </nav>
      )}
    </header>
  );
}
