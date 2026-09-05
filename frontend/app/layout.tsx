import type { Metadata } from "next";
import { Suspense } from "react";
import { Barlow, Barlow_Condensed } from "next/font/google";
import "./globals.css";
import { SiteHeader } from "@/components/SiteHeader";
import { currentMember } from "@/lib/session";
import { watchCount } from "@/lib/watchlist";
import { SiteFooter } from "@/components/SiteFooter";
import { AuthNotice } from "@/components/AuthNotice";
import { LookupProvider } from "@/components/LookupNotice";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import { App as AntdApp } from "antd";

/**
 * next/font downloads these at build time and serves them from our own
 * origin, so no CDN request is made from the browser (CLAUDE.md forbids CDNs).
 * Neither family is a variable font on Google Fonts, so the weights the
 * design system uses have to be listed explicitly.
 */
const barlow = Barlow({
  variable: "--font-barlow",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  display: "swap",
});

const barlowCondensed = Barlow_Condensed({
  variable: "--font-barlow-condensed",
  subsets: ["latin"],
  weight: ["400", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "書價 BOOKPRICE.TW｜五大通路書籍比價",
  description:
    "一次比較五南文化廣場、三民網路書店、金石堂、讀冊生活、墊腳石五家網路書店的售價，並可追蹤作品、設定目標價。",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // Read once, here, so every route renders the 導覽列 in the state the reader
  // is actually in rather than each page deciding for itself.
  const member = await currentMember();
  const tracked = member ? await watchCount() : 0;

  return (
    <html
      lang="zh-Hant-TW"
      className={`${barlow.variable} ${barlowCondensed.variable}`}
    >
      <body>
        {/* antd keeps its styles in CSS-in-JS, so they have to be collected
            during the server render and injected ahead of the markup that uses
            them; without this a page using antd flashes unstyled. It emits
            nothing until an antd component actually renders, so the ported
            設計系統 is untouched until then. */}
        <AntdRegistry>
          {/* Supplies the notification context App.useApp() reads.
              component={false} so it wraps the tree without adding a div of its
              own to the 版面. */}
          <AntdApp component={false}>
            {/* Wraps the whole tree because 找書 outlives the screen that
                starts it: it runs for over a minute, and the reader is free to
                navigate away while it does. The result finds them wherever
                they went. */}
            <LookupProvider>
              <SiteHeader loggedIn={member !== null} watchCount={tracked} />
              <main>{children}</main>
              <SiteFooter />
            </LookupProvider>

            {/* Here rather than on a page: 註冊 / 登入 / 登出 all succeed by
                redirecting, so the confirmation has to be able to appear
                wherever they land. Suspense because it reads the query string. */}
            <Suspense fallback={null}>
              <AuthNotice />
            </Suspense>
          </AntdApp>
        </AntdRegistry>
      </body>
    </html>
  );
}
