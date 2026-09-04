import type { Metadata } from "next";
import { Barlow, Barlow_Condensed } from "next/font/google";
import "./globals.css";
import { SiteHeader } from "@/components/SiteHeader";
import { SiteFooter } from "@/components/SiteFooter";

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
  title: "書價 BOOKPRICE.TW｜六大通路書籍比價",
  description:
    "一次比較博客來、誠品線上、金石堂、讀冊生活、樂天Kobo、Readmoo 六家網路書店的售價，並可追蹤作品、設定目標價。",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="zh-Hant-TW"
      className={`${barlow.variable} ${barlowCondensed.variable}`}
    >
      <body>
        <SiteHeader />
        <main>{children}</main>
        <SiteFooter />
      </body>
    </html>
  );
}
