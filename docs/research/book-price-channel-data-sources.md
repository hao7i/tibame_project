# 書籍比價入口網站 — Channel Data Acquisition Research

**Scope.** How the Book Price Portal (`design/README.md`) can legally and technically obtain per-book
selling prices from 博客來 / 誠品線上 / 金石堂 / 讀冊生活 TAAZE / 樂天Kobo / Readmoo 讀墨.

**Research date: 2026-09-04.** Every robots.txt, terms page and product page cited below was fetched
live on that date. Robots files and SPA bundle hashes change; re-verify before relying on any of this
in production. **This is engineering research, not legal advice** — the ToS and statute readings below
identify where a lawyer's judgement is required, they do not substitute for it.

---

## Summary — the five findings that change the build

1. **No channel offers a price API. Not one.** All the affiliate programmes that exist
   (博客來 AP, 金石堂 分紅大聯盟, TAAZE 行銷分紅夥伴, Kobo via Rakuten Advertising, Readmoo AP 推書夥伴)
   are **link-generation and commission tracking only**. The one network that does publish a
   price-bearing product catalogue — Rakuten Advertising — has **no Kobo advertiser with a TW/TWD/zh
   feed**; the advertiser the Taiwan storefront signs you up to is literally named "Rakuten Kobo US".
   Joining every programme buys you monetisation and a better legal posture, but **zero price data**.

2. **The technical difficulty splits the six into tiers, and not the tiers you would guess.**
   金石堂 and TAAZE are *easy* — both emit `schema.org` JSON-LD with `offers.price` **and** `isbn` in
   the server-rendered HTML, so a plain HTTP GET plus a JSON parse yields everything. 誠品線上 is a
   client-rendered Vue SPA that ships no price at all in its HTML. Kobo has the price server-side but
   actively bot-challenges. 博客來 and Readmoo block AI agents outright in robots.txt.

3. **Two channels name AI crawlers in robots.txt and block them site-wide.** 博客來 (Cloudflare-managed,
   `ClaudeBot Disallow: /`) and Readmoo (hand-written, `2026-08 新增`, blocks `ClaudeBot`/`anthropic-ai`).
   This did not stop *research* — robots.txt itself is always readable — but it means the development
   process must not fetch those product pages with an AI agent, and it is a loud signal about how those
   two operators feel about automated collection generally.

4. **The legal exposure is not the price — it is everything next to the price.** TIPO's own 函釋 on web
   scraping ([電子郵件1070814](https://www.tipo.gov.tw/tw/copyright/692-15747.html)) says listing prices
   and transaction data in table form is **not** copyrightable subject matter, but warns that if what
   the crawler picks up **includes 著作**, republishing it engages 重製 and 公開傳輸. For a book
   comparison site that means **cover images and 內容簡介 are the risk, not the number**. Design
   implication: store price/ISBN/availability; link out for covers and blurbs rather than mirroring them.

5. **Every retailer's ToS is broader than its robots.txt.** Five of six terms pages assert IP over
   「資訊、資料」 and require **事前書面同意** for 重製/轉載 — even though only Kobo has an explicit
   anti-scraping clause. Robots.txt permission does not override contract terms. The affiliate
   programmes are the lever here: 金石堂's AP FAQ contains an **explicit written carve-out** permitting
   use of its 圖文 for promotion that links back, which is the single most useful permission found in
   this whole exercise.

---

## Comparison table

| Channel | Official API / affiliate | robots.txt posture | ToS posture | Price rendering | ISBN lookup |
|---|---|---|---|---|---|
| **博客來** books.com.tw | AP 策略聯盟 host live at `ap.books.com.tw`; **content Not established** (site blocks AI agents). Listed iChannels advertiser, but iChannels' publisher API is orders/creatives/links only — **no price feed** | 🔴 `ClaudeBot Disallow: /` + `Content-Signal: ai-train=no`. Generic `*` group **allows** product pages | **Not established** — could not fetch terms without violating robots | **Not established** — did not fetch | Partial: `search.books.com.tw/search/query/` is **robots-Allowed**; query parameter **Not established** |
| **誠品線上** eslite.com | ❌ No first-party programme. Not on either TW network. Revenue-share only via ShopBack / 美安 | 🟡 No AI rules. `Disallow: /member*`, **`/search`, `/search?*`** — product pages allowed, **search is not** | 🔴 [網站使用條款](https://www.eslite.com/docs/service-rules) §六: 「資訊、資料」 IP, 「不得以任何方式使用」 without written consent. **No** anti-bot clause | 🔴 **Client-side.** Vue SPA shell, 13,761 bytes, generic title, no JSON-LD, no price | `/search?keyword={ISBN}` — **robots-Disallowed for everyone** |
| **金石堂** kingstone.com.tw | ✅ [分紅大聯盟](https://www.kingstone.com.tw/partner/ap/) — no application, 4% on books, 24h cookie. **Link generation only, no feed.** Also an iChannels advertiser | 🟢 **Explicitly `Allow: /` for ClaudeBot**, GPTBot, CCBot, Perplexity. Disallows `/api/`, `/cart/`, `/bms/`, `/dks/` | 🟡 [會員服務條款](https://www.kingstone.com.tw/ksmember/info/service/) bars 複製 of 「圖像、資料」; **no anti-bot clause**. ⭐ AP FAQ Q10 grants a promotional carve-out | 🟢 **Server-rendered.** JSON-LD `offers.price` + `isbn` + `gtin13` | 🟢 `/search/key/{ISBN}` — verified |
| **TAAZE 讀冊** taaze.tw | ✅ [行銷分紅夥伴](https://www.taaze.tw/affiliateprogram/index.jsp) — email approval, 1% books / 2% used & digital. **Link generation only, no feed** | 🟢 Single `*` group, no AI rules. Product + search allowed; three XHR endpoints disallowed | 🔴 [會員服務使用條款](https://www.taaze.tw/static_act/member/index.htm) (updated **2025/10/16**): 「**任何人**不得逕自使用、修改、重製…轉載…必須先取得…事前書面同意」. No anti-bot clause | 🟢 **Server-rendered.** Three JSON-LD blocks: `price`, `isbn`, `mpn`, `itemCondition` | 🟢 `/rwd_searchResult.html?keyType[]=0&keyword[]={ISBN}` — verified |
| **樂天Kobo** kobo.com | ✅ Rakuten Advertising, mid **37217**. 5% ebooks, 14-day cookie. Product Catalog exists on the network **but no Kobo TWD/zh feed**. No public developer API | 🟢 `/tw/zh` allowed; ⭐ **`Allow: */search?query=9*`** explicitly permits ISBN search. But **live bot-challenge** in front of product pages | 🔴 [Terms of Use](https://authorize.kobo.com/terms/termsofuse) (updated **2026年5月**) — the only explicit anti-scraping clause of the six: bans 「機器人」「蜘蛛」「數據挖掘」 and 「納入任何其他資料庫」 | 🟡 **Server-rendered but guarded.** `og:price` + `priceDetails` JSON in HTML; visible `.price` span is an empty Knockout binding | 🟢 `/tw/zh/search?query={ISBN}` → **301 straight to the product page** — verified |
| **Readmoo 讀墨** readmoo.com | ✅ [AP 推書夥伴](https://help.readmoo.com/zh-TW/articles/15700650-什麼是ap推書夥伴) — free, instant, 3%, 7-day cookie. **Link generation only, no feed.** Internal `/api/*` exists but is robots-Disallowed | 🔴 `ClaudeBot`/`anthropic-ai` `Disallow: /` (added **2026-08**). `Disallow: /search/` **for everyone**. `Crawl-delay: 10` | 🟡 [服務條款](https://readmoo.com/terms/service) (dated **2015/10/21**): no anti-bot clause, but §15 requires 事前書面同意 to 轉載 and §18 bans 商業目的之使用 | 🟡 Readmoo's own robots.txt says 「書頁 /book/…為 SSR」 — **but they hedge it themselves** | 🔴 **Not established.** ISBN is not a documented search input; `/search/` Disallowed |

Legend: 🟢 workable · 🟡 workable with caveats · 🔴 blocked or hostile

---

## 1. 博客來 books.com.tw

**Headline: the least establishable channel of the six, and the one with the largest market share.**

### Official API / affiliate

`ap.books.com.tw` **resolves and serves HTTP 200**, consistent with the AP 策略聯盟 programme living
there. Its content is **Not established** — see the robots constraint below. Note that
[ap.books.com.tw/robots.txt](https://ap.books.com.tw/robots.txt) carries the same Cloudflare-managed
AI-crawler blocks as the main site.

**博客來 IS a listed advertiser on iChannels 通路王** — verified in the raw HTML of
[ichannels.com.tw](https://www.ichannels.com.tw/), where the 廣告主 grid carries
`alt="博客來" title="博客來"` alongside 金石堂, momo, Yahoo購物中心 and HyRead.

**But that route cannot supply price data.** From iChannels' own publisher page,
[user-registerpre.php](https://www.ichannels.com.tw/user-registerpre.php), under 「方便好用的API」:

> 提共多種功能API：包含訂單API、廣告素材API、產生推廣連結API、與訂單Server Postback等，讓您方便串接，自動化節省人力。

*Gloss: orders API, ad-creative API, deeplink-generation API, order server postback.* Orders,
creatives, links, postback — **no product catalogue, no price**. The same page explicitly courts
購物比價 sites, but offers them monetisation plumbing rather than data.

**聯盟網 affiliates.one:** 博客來 not found on the public
[advertisers page](https://www.affiliates.one/zh-tw/advertisers), which publishes only sample logos
rather than a merchant directory. The real list is behind publisher login — so this is
"not publicly listed", **not** proven absent.

### robots.txt

[https://www.books.com.tw/robots.txt](https://www.books.com.tw/robots.txt) is Cloudflare-managed and
has **two `User-agent: *` groups**. The first:

```
User-agent: *
Content-Signal: search=yes,ai-train=no,use=reference
Allow: /
```

then named AI-crawler groups, each `Disallow: /`:

```
User-agent: ClaudeBot
Disallow: /
```

(same for `Amazonbot`, `Applebot-Extended`, `Bytespider`, `CCBot`, `CloudflareBrowserRenderingCrawler`,
`Google-Extended`, `GPTBot`, `meta-externalagent`). Then the site's own group:

```
User-Agent: *
Disallow: /exep/ap.php
Disallow: /exep/prod/smallimg.php
Disallow: /exep/lib/captcha_image.php
Disallow: /exec/
Disallow: /exep/shopping/
Disallow: /cgi-bin/
Disallow:/product_show/getBrowseItemsAjax/*/M201502_031_view
Disallow:/product_show/getBrowseItemsAjax/*/M201101_060_view
```

**Plainly: a generic, non-AI-labelled crawler hitting 博客來 product pages is WITHIN these directives.**
None of the Disallow paths covers the product namespace. An AI-labelled crawler is **outside** them.
There is **no `Sitemap:` directive** on the main host.

The `Content-Signal` line is Cloudflare's machine-readable rights reservation. The file defines the
signals itself:

> search: building a search index and providing search results (e.g., returning hyperlinks and short
> excerpts from your website's contents). Search does not include providing AI-generated search summaries.
> ai-train: training or fine-tuning AI models.

and asserts: `ANY RESTRICTIONS EXPRESSED VIA CONTENT SIGNALS ARE EXPRESS RESERVATIONS OF RIGHTS UNDER
ARTICLE 4 OF THE EUROPEAN UNION DIRECTIVE 2019/790`. A price-comparison index arguably sits under
`search=yes`; it is emphatically not `ai-train`. Note this is an EU-directive reservation asserted by a
Taiwanese site — its effect in ROC law is a lawyer question.

### Terms of service

**Not established — blocked by robots.txt for AI agents.** Neither
`www.books.com.tw/web/fhelp_c_terms/` nor `/web/sys_serviceterms/` nor any footer link was fetched,
because `ClaudeBot Disallow: /` governs this research. **A human must open these and read them.**

### Price rendering

**Not established.** No 博客來 product page was fetched, for the same reason.

### ISBN lookup

Partially established, **from 博客來's own robots.txt alone**.
[search.books.com.tw/robots.txt](https://search.books.com.tw/robots.txt) ends:

```
User-agent: *
Disallow: /
Allow: /search/query/
Allow: /search/query
Allow: /sitemap.xml
Sitemap: https://search.books.com.tw/sitemap.xml
```

So the public search endpoint is **`https://search.books.com.tw/search/query/`** and 博客來
affirmatively invites generic crawlers into it while excluding everything else on that host. The
**query parameter name for an ISBN search is Not established** — verifying it would have required
fetching a search page under the ClaudeBot block. *Not guessed.* A human can establish it in one
browser visit. There is also a **sitemap** on the search host, which is the sanctioned discovery route.

---

## 2. 誠品線上 eslite.com

### Official API / affiliate

**No first-party affiliate programme exists.** The complete commercial-partnership surface in eslite's
own footer is two links: [合作業務範圍](https://www.eslite.com/docs/cooperation) — which is
**mall-operations consulting** (商場規劃與品牌招商, 行銷企劃與活動策展, 工程與設施維護保養…), not
affiliate marketing — and [團購業務](https://www.eslite.com/docs/group-buying-description). The strings
`affiliate` and `聯盟` do not appear in eslite's main application bundle. 誠品 is **not** listed on
iChannels or on affiliates.one's public page.

Revenue share runs instead through two 導購 partners hard-coded in eslite's own checkout code
(`displayName: "ShopBack"` / `description: "ShopBack 導購廠商"`, and `displayName: "美安"`).
Corroborated on ShopBack's own merchant page,
[shopback.com.tw/eslite](https://www.shopback.com.tw/eslite), whose T&C names 「誠品分潤計畫」.

**No product/price feed is offered.** However — materially — eslite operates a backend API host whose
robots.txt explicitly permits API access. [athena.eslite.com/robots.txt](https://athena.eslite.com/robots.txt)
reads in full:

```
# See https://www.robotstxt.org/robotstxt.html for documentation on how to use the robots.txt file
User-agent: *
Allow: /api/
```

*(Verified independently. Note this host sits behind Cloudflare and returned a 403 challenge on one of
two attempts.)* This is an **undocumented internal endpoint that happens to be robots-permitted** — not
an official, supported or contractually blessed API. No docs, no terms, no versioning guarantee.
**Do not build on it without legal review**, and treat it as able to disappear without notice.

### robots.txt

[https://www.eslite.com/robots.txt](https://www.eslite.com/robots.txt) in full:

```
User-agent: PetalBot
User-agent: Baiduspider
Disallow: /

User-agent: *
Disallow: /member*
Disallow: /search
Disallow: /search?*
Sitemap: http://www.eslite.com/sitemap.xml.gz
```

**Plainly: a crawler hitting eslite PRODUCT pages (`/product/{id}`) is WITHIN these directives. A
crawler hitting eslite SEARCH is OUTSIDE them** — and that applies to *every* user agent, not just AI
ones. Only two named bots (PetalBot, Baiduspider) are excluded site-wide; there are no AI-crawler rules.

The sitemap is live and current: the [sitemap index](https://www.eslite.com/sitemap.xml.gz) fetched
today lists 15+ child sitemaps with `lastmod` of **2026-09-04**. This is the sanctioned discovery
route for product URLs and it obviates search entirely — `sitemap1.xml.gz` alone yields product URLs of
the form `https://www.eslite.com/product/1001183471000198`.

### Terms of service

**[https://www.eslite.com/docs/service-rules](https://www.eslite.com/docs/service-rules)** — titled
網站使用條款, heading 網路服務約定事項保護聲明. **No 最後更新 date is shown anywhere on the page** —
flag as stale-unknown. Its only change-control language is 一般條款 二:

> 會員同意本公司及關係企業得隨時調整、變更、修改或終止網路服務或網路服務約定事項，並得於修改及變更前60日，於本公司及關係企業之網站公告後生效，不再另行個別通知。

*Gloss: Eslite may change or terminate the terms at any time; changes take effect after being posted 60
days in advance, with no individual notice.*

The operative clause, **六.智慧財產權的保護**:

> 本公司及關係企業所使用軟體或程式、網站上所有內容，包含但不限於著作、圖片、檔案、資訊、資料、網站架構、網站畫面的安排、網頁設計等智慧財產權，屬於誠品所有，或已取得權利人之同意及授權而使用。任何人包含會員本人，未事前取得本公司及關係企業或權利人書面同意及授權，均不得以任何方式使用。

*Gloss: All content on the website — **including but not limited to works, images, files, information,
data**, site architecture, screen arrangement and web design — is Eslite's IP. **No one, members
included, may use it in any manner whatsoever** without prior written consent.*

This is the clause that bites: 「資訊、資料」 is expressly enumerated and the prohibition is
「不得以任何方式使用」 — *any* manner of use, not merely reproduction. No factual-data carve-out, no
quantitative threshold.

**There is NO clause addressing automated access.** A search of the full document for
爬蟲 / 機器人 / 網路蜘蛛 / spider / crawler / robot / 抓取 / 擷取 / 自動化 returns **zero** anti-bot
matches. The only extraction language is scoped to ebook *content*, not catalogue data:

> 除了電子書服務所提供之功能外，在未獲得誠品生活書面授權或書面同意前，會員不得使用任何軟體或工具，試圖側錄或擷取電子書服務商品全部或部分內容，進行公開散佈或存取內容。

*Gloss: Members may not use any software or tool to record or extract ebook content for public
distribution.* Note eslite writes explicit 個人非商業用途 limits when it wants them — and did **not** do
so for site data generally. **So: eslite restricts you by copyright, not by an anti-automation clause.**

### Price rendering

🔴 **Client-side. High confidence — verified directly.** A plain GET of
[https://www.eslite.com/product/1001183471000198](https://www.eslite.com/product/1001183471000198)
returns **13,761 bytes** containing:

- `<title>誠品線上 - 把生活變成喜歡的樣子</title>` — the *generic site title*, not the product's
- `<div id="app">` — an empty Vue mount point
- **zero** `application/ld+json` blocks
- **zero** occurrences of `price`, `lowPrice`, `priceCurrency` or any price token
- **zero** `og:` or `description` meta tags

Script tags confirm the stack: `/cdn/vue.runtime.global.prod.js`, `/cdn/axios.min.js`,
`/cdn/pinia.iife.min.js`. **The price is fetched by XHR after hydration and is not in the HTML at all.**

Two operational consequences: (a) a plain HTTP scraper gets nothing — you would need a headless browser
*or* the athena API; (b) the same shell also loads
`https://static.queue-it.net/script/queueclient.min.js` — **eslite runs Queue-it**, a virtual
waiting-room / traffic-gating service. Expect to be queued or throttled under load.

### ISBN lookup

Pattern: **`https://www.eslite.com/search?keyword={ISBN}`** (parameter name established from eslite's own
search component and route table). 🔴 **This path is `Disallow`ed in robots.txt for all agents.**

Product URL pattern: **`https://www.eslite.com/product/{id}`** — a numeric eslite ID, not the ISBN.

---

## 3. 金石堂 kingstone.com.tw

**Headline: the friendliest channel of the six, on every axis.**

### Official API / affiliate — 分紅大聯盟

[https://www.kingstone.com.tw/partner/ap/](https://www.kingstone.com.tw/partner/ap/)

**No signup step at all:**

> 您無須作任何加入的動作，只要您是金石堂會員，就已經具備推廣分紅的身分，可直接進入分紅大聯盟網站產生個人專屬推薦連結

*Gloss: No joining action required — any 金石堂 member already qualifies.* The
[FAQ](https://www.kingstone.com.tw/partner/ap/faq) repeats it: 「新版分紅大聯盟已無需申請」.
Taiwan eligibility is implicit — payout is into a 金石堂會員現金帳戶 in TWD, and corporate members must
issue a 發票 with 5% 營業稅.

Commission: **中文書 / 英文書 / 電子書 / 親子館 = 4%**; 文具、玩具 3%; 動漫/3C/家電 2%; 雜誌、回頭書、
遊戲點數卡 excluded. **24-hour cookie window.** Payout threshold NT$500. Clause 9 matters for a
comparison site: 「本計畫並無與相關策略聯盟合作(包含美安、SHOPBACK、樂天、蝦皮…等)不適用」 — no
double-dipping with other networks.

**No product/price feed or API.** The mechanism is explicitly manual link conversion — from
[新手上路](https://www.kingstone.com.tw/partner/ap/newuser): 「步驟一：貼上您要分享的商品網址，並按下
轉換網址鍵」. The generator at `/partner/aplink` 302-redirects to login. Searches across `/partner/ap/`,
`/partner/ap/faq`, `/partner/ap/newuser`, [`/service/supplier/`](https://www.kingstone.com.tw/service/supplier/)
and [`/qa/index/`](https://www.kingstone.com.tw/qa/index/) for API / feed / 資料庫 / 商品資料 / 串接 / 介接
returned **zero** relevant hits. [partner.kingstone.com.tw](https://partner.kingstone.com.tw/) is a
login-gated supplier portal, not a data API.

金石堂 is also a **listed iChannels advertiser** (verified: `alt="金石堂"` in the advertiser grid on
[ichannels.com.tw](https://www.ichannels.com.tw/)) — but see §1: that network has no price feed either.

### robots.txt

[https://www.kingstone.com.tw/robots.txt](https://www.kingstone.com.tw/robots.txt):

```
User-agent: *
Disallow: /api/
Disallow: /bms/
Disallow: /dks/
Disallow: /cart/
Disallow: /Refund/
Disallow: /einvoice/
Disallow: /wp-admin/
```

followed by ~20 named groups each with `Allow: /` — including **`ClaudeBot`, `Claude-User`,
`Claude-SearchBot`, `GPTBot`, `OAI-SearchBot`, `CCBot`, `PerplexityBot`, `Google-Extended`,
`Meta-ExternalAgent`**. Plus a list of `Sitemap:` directives on `static.kingstone.com.tw`.

**Plainly: a crawler — AI-labelled or not — hitting 金石堂 product and search pages is WITHIN these
directives.** This is the only one of the six that affirmatively welcomes AI crawlers. The one hard
boundary is **`/api/`** — off-limits by robots even though the rest of the site is open, so whatever
internal JSON API exists must not be called.

⚠️ **Staleness flag:** the newest `Sitemap:` entry is `sitemap20240106…`, i.e. **January 2024** — over
two and a half years old. Do not rely on these sitemaps for discovering recent titles; use search instead.

### Terms of service

**[https://www.kingstone.com.tw/ksmember/info/service/](https://www.kingstone.com.tw/ksmember/info/service/)**
— 會員服務條款. No 最後更新 date; the page carries a version stamp **「版本Kingstone.2021.11製」**
(November 2021 — nearly five years old; flag as stale).

**There is NO clause mentioning 爬蟲 / 機器人 / 自動化 / 非人工方式.** The load-bearing clause is
general-purpose, under 其他條款:

> 本站所有圖像、資料非經本站同意不得進行複製、移轉、租用、出售。

*Gloss: All images and **data** on this site may not be copied, transferred, rented or sold without the
site's consent.* Note 「資料」 (data) is named explicitly alongside images.

Under 本網站會員應遵守之法律義務及其承諾:

> 不得行使未經金石堂網路書店同意或授權之商業行為。

*Gloss: No commercial activity that Kingstone has not consented to or authorised.*

> 不干擾或混亂網路服務亦不得傳輸或散佈電腦病毒。

*Gloss: Do not interfere with or disrupt the network service.* — the nearest thing to a rate-limit
clause; it bars **disruption**, not automation as such. Design to stay well under any plausible
disruption threshold.

> 遵守所有使用網路服務的網路協定、規定、程式和慣例。

*Gloss: Comply with all network protocols, rules, procedures and conventions.* — arguably imports
robots.txt compliance by reference, though it does not say so.

The [privacy policy](https://www.kingstone.com.tw/ksmember/info/privacy/) (also 版本Kingstone.2021.11製)
adds under 智慧財產權: 「…網站上所有內容，包括但不限於著作、圖片、檔案、資訊、資料…請務必於取得
金石網絡及其他權利人**書面同意**後，才能使用前述資料。」

⭐ **The carve-out — the most useful permission found in this research.**
[分紅大聯盟 FAQ Q10](https://www.kingstone.com.tw/partner/ap/faq):

> 推薦商品時，使用金石堂網站的圖文，會有侵權問題嗎？ 若您正常使用於您的部落客或經由您的社群推廣使用圖文或連結連回金石堂網路書店做推薦，無侵權上的問題，但非此用途或其他不相關網站者一經發現獲檢舉，將會發信通知勸導改善調整，若還未在3天內進行調整將會停權通知會員直至調整完畢後再行通知。

*Gloss: Using Kingstone's images and text **for affiliate promotion that links back to Kingstone** is
not infringement. Use outside that purpose triggers a warning letter and account suspension if not
corrected within 3 days.*

This conditions the licence on (i) **promotional purpose** and (ii) **linking back**. A comparison site
that is a registered 分紅大聯盟 participant and links every listing back to 金石堂 plausibly sits inside
it. One that does not, does not. **This is exactly the shape the app should be built in.**

### Price rendering

🟢 **Server-rendered. High confidence — independently verified twice.** A plain curl of
[https://www.kingstone.com.tw/basic/2018612496928/](https://www.kingstone.com.tw/basic/2018612496928/)
(99,981 bytes) contains the price digits in visible markup —

```html
<span><b class="b1">79</b>折</span>
<span><b class="sty2 txtSize2">332</b>元</span>
<div class="basicfield"><s><b class="sty00">420</b></s>元</div>
```

— **and** in a `<script type="application/ld+json">` block with `@type: ["Product","Book"]`, from which
I independently extracted:

```
"sku":"2018612496928"   "gtin13":"9789573342793"   "isbn":"9789573342793"
"priceCurrency":"TWD"   "price":"332"   "availability":"https://schema.org/InStock"
```

**One JSON-LD parse yields ISBN, SKU, price, currency and stock status.** This is the recommended
extraction target — machine-intended, stable, immune to CSS churn.

⚠️ **One caveat.** There *is* a client-side price element, but it is secondary — the post-coupon 券後價:
`<strong id="promotional-price-value">` is **empty** and its container is `hidden`, carrying an
encrypted `data-promotional-price-context` token that an XHR trades for the value. **The 優惠價 (332) is
server-rendered and reliable; the 券後價 is not available without JavaScript.** For a comparison app the
優惠價 is the right number, and it matches JSON-LD exactly.

### ISBN lookup

🟢 **`https://www.kingstone.com.tw/search/key/{ISBN}`** — verified. `/search/key/9789573342793` returns
HTTP 200 with two server-rendered results linking to `/basic/2018612496928/` and `/basic/2800000140598/`
(print and ebook editions).

Product URL: **`/basic/{13-digit Kingstone SKU}/`** — the path segment is Kingstone's **own SKU, not the
ISBN**, so the flow is always search → SKU → product.

⚠️ **Zero-result searches return HTTP 200**, with no error status and no visible 「找不到」 string — the
page fills with 72 `search_cannotfind` fallback recommendation links instead. Your detector must key on
the presence of real result links (`actid=WISE`), **not** on status code. Three test ISBNs
(`9789861371788`, `9789861373898`, `9789573317249`) returned genuine no-match pages — 金石堂 drops
out-of-print titles, so expect real catalogue gaps rather than lookup failures.

---

## 4. 讀冊生活 TAAZE taaze.tw

### Official API / affiliate — 行銷分紅夥伴計畫

[https://www.taaze.tw/affiliateprogram/index.jsp](https://www.taaze.tw/affiliateprogram/index.jsp);
the text version is at the customer-service Q&A, [/qa/view/k.html](https://www.taaze.tw/qa/view/k.html).

Unlike Kingstone, TAAZE has an **approval gate**:

> step1──【加入】加入TAAZE會員，並開通TAAZE行銷分紅合作權限，並通過審核
> 只要通過簡單的審核兩步驟：一、填妥審核表格中的兩項資料，並寄至 myap_apply@taaze.tw。二、收到來自taaze的確認信！

Commission (from the same Q&A): 一般圖書 **1%**, 二手商品 **2%**, 數位商品（電子書、有聲書）**2%**,
其他（雜誌、文具、影音）0.5%. Settlement: monthly, paid on the 20th of the following month into the
讀冊帳戶. Operator: 學思行數位行銷股份有限公司, 統一編號 24342999.

**No product/price feed or API.** The described mechanism is per-product 專屬連結 only. Searches across
the affiliate page, `/qa/view/k.html`, [`/rwd_qa.html`](https://www.taaze.tw/rwd_qa.html),
[合作計劃](https://activity.taaze.tw/static_act/partner/index.htm),
[異業合作](https://www.taaze.tw/static_act/cooperation/index.htm) and the member terms for
API / feed / 資料庫 / 商品資料 / 開放資料 / 串接 / 介接 / 比價 returned **zero hits**.

**On the findbook data-partnership question: Not established.** No TAAZE-authored page describes a data
partnership, open-data offering, or comparison feed. What TAAZE *does* publish is a general pitch
channel — [異業合作](https://www.taaze.tw/static_act/cooperation/index.htm): 「如果你有任何想法，和我們
聯絡吧！…**affiliate@taaze.tw**」. That is the correct primary-source address to ask.

⚠️ **One unread primary document.** The affiliate contract at
[itemAp.html?typeFlg=JD](https://www.taaze.tw/itemAp.html?typeFlg=JD) (linked as 「看行銷分紅夥伴計畫
相關合約」) **redirects to a login wall**. It is the single document most likely to address
price-comparison publishers explicitly. **Read it after opening an account, before building.**

### robots.txt

[https://www.taaze.tw/robots.txt](https://www.taaze.tw/robots.txt) — a single `User-Agent: *` group with
**no AI-crawler rules at all**. The Disallow list is `/beta/`, `/ca3_index.html`, `/rwd_checkref.html`,
`/rwd_cleancookie.html`, a long tail of archived `/static_act/` campaign directories (2010–2017), and
three XHR endpoints:

```
Disallow:/single_getUsedList.html
Disallow:/zekea_storeDataAgent.html
Disallow:/single_getProdComments.html

Sitemap: https://www.taaze.tw/static_act/sitemap/sitemap.xml
```

**Plainly: a crawler hitting TAAZE product pages (`/products/*.html`) and search
(`/rwd_searchResult.html`) is WITHIN these directives.** The boundary to respect is those three XHR
endpoints — note `single_getUsedList.html` is the **used-copy list** endpoint, so used-book listings
must not be fetched that way.

### Terms of service

**[https://www.taaze.tw/static_act/member/index.htm](https://www.taaze.tw/static_act/member/index.htm)**
— 會員服務使用條款. **「最近一次更新日期: 2025/10/16」** — by far the freshest terms of the six.

**No clause mentions 爬蟲 / 機器人 / 自動化 / 非人工方式.** But the IP clause is the strongest of any
channel, under 智慧財產權的保護:

> 學思行所使用的軟體或程式、網站上所有的內容，包括但不限於著作、圖片、檔案、資訊、資料、網站架構、網站方面的安排、網頁設計，均由本公司或其他權利人依法擁有其智慧財產權…**任何人不得逕自使用、修改、重製、公開播送、改作、散布、發行、公開發表、進行還原工程、解編或反向組譯。若您引用或轉載前述軟體、程式或網站內容，必須先取得本公司或其他權利人的事前書面同意。**尊重智慧財產權是您應盡的義務，如有違反，除您應對學思行負損害賠償責任外，學思行並得撤銷您的會員資格及永久禁止您使用本服務。

*Gloss: All site content — including **information and data** — is TAAZE's or rightsholders' IP.
**No one may, on their own initiative, use, modify, reproduce, publicly broadcast, adapt, distribute,
publish… it. To quote or republish (轉載) it you must first obtain prior WRITTEN consent.** Breach =
damages plus permanent ban.*

Two features make this the sharpest clause in this research: it names **「任何人」 (anyone)** — TAAZE
asserts it against non-account-holders too — and it expressly covers **重製 and 轉載**.

Also, under 使用者責任義務: 「不得行使未經學思行同意或授權之商業行為。」 (word-for-word parallel to
Kingstone's). TAAZE's prohibited-conduct list is *narrower* than Kingstone's on interference — there is
no "do not disrupt the service" wording at all, only 「傳輸或散佈電腦病毒」.

**A disclaimer worth copying into your own UI**, under 電子商務交易:

> 學思行盡力維護相關資料的正確性，但不保證所有出現在網頁上、或相關訊息上的資料均為完整、正確、即時的資訊…如果網頁上、或相關訊息所標示的價格有誤：若標示價格比正確價格高，將收取較低的正確價格。若標示價格比正確價格低，將保留拒絕或事後取消訂單的權利。

*Gloss: TAAZE endeavours to keep data accurate but **does not warrant that any data on its pages is
complete, correct or current**; wrong prices may lead to order cancellation.* If the source itself
disclaims price correctness, the comparison site must too — see 公平交易法第21條 in the Legal section.

### Price rendering

🟢 **Server-rendered. High confidence — independently verified twice.** A plain curl of
[https://www.taaze.tw/products/11100479747.html](https://www.taaze.tw/products/11100479747.html)
(297,574 bytes) shows the price in visible markup —

```html
<span>定價：<small>NT$</small> <span style='text-decoration:line-through;'>300</span></span>
<span>優惠價：<span style="color:#e2007e;"><strong>88</strong></span> <small>折</small>，
<small>NT$</small> <span style="color:#e2007e;"><strong>264</strong></span></span>
```

— and **TAAZE emits three JSON-LD blocks per product page**, from which I independently extracted:

```
"sku": "11100479747"   "mpn": "9789862294048"   "isbn": "9789862294048"
"priceCurrency": "TWD" "price": "264.0"         "itemCondition": "https://schema.org/NewCondition"
```

Block 1 is `@type: Product` (sku, **mpn = the ISBN**, brand, offers), block 2 is `@type: Book`
(**isbn** explicitly), block 3 is `BreadcrumbList` (category path). No XHR needed.

**Three TAAZE-specific parsing caveats:**

- **`itemCondition` distinguishes new from used.** TAAZE is Taiwan's main used-book channel and returns
  *both* a new and a used listing for one ISBN — e.g. the used listing of the same title carries
  `"price": "94.0"`, `"itemCondition": ".../UsedCondition"`. **Model TAAZE as potentially multiple
  offers per ISBN**, not one. This is a genuine product advantage over every other channel.
- **Out-of-print titles omit 優惠價 entirely** — only 定價 plus 「本商品已絕版」, with an
  `outOfPrint="Y"` flag in `<head>`. Parse this as a distinct state; **do not fall back to 定價 as if it
  were the selling price.**
- The related-products grid lower down the page also contains 定價/優惠價 markup **for other books**.
  Anchor extraction to JSON-LD, not to a naive 優惠價 regex.

### ISBN lookup

🟢 **`https://www.taaze.tw/rwd_searchResult.html?keyType[]=0&keyword[]={ISBN}`** — verified
(URL-encoded: `?keyType%5B%5D=0&keyword%5B%5D=`). `keyType` options are `0`=全文, `1`=書名, `2`=作者,
`3`=出版商, `4`=標籤, `5`=冊格子. **There is no dedicated ISBN option — use `keyType[]=0` (全文), which
does match ISBNs.** The form declares `method="post"` but **GET works**, which is what makes this usable.

Verified: `?keyType[]=0&keyword[]=9789861371788` → HTTP 200, `<title>9789861371788 - TAAZE讀冊生活</title>`,
two results (`/products/11100677417.html` new, `/products/11310582654.html` used).

Product URL: **`https://www.taaze.tw/products/{prodId}.html`**.
⚠️ Same as Kingstone: **zero results still returns HTTP 200**; detect on the presence of `/products/`
links, not on status code. The `<title>` always echoes the query.

---

## 5. 樂天Kobo www.kobo.com (/tw/zh)

### Official API / affiliate

The programme link is in the TW storefront footer under 機會 → 關係企業:
[`https://kobo.com/p/affiliate`](https://kobo.com/p/affiliate), which geo-resolves from a Taiwan IP to
[https://www.kobo.com/tw/zh/p/affiliate](https://www.kobo.com/tw/zh/p/affiliate), fully localised to
zh-TW. (`/affiliates` returns 403; `/tw/zh/affiliates` returns 404.)

**Operated by Rakuten Advertising** (ex-LinkShare). The only external signup link on the page is
`https://signup.linkshare.com/publishers/registration/landing?ls-locale=us&host=linkshare&mid=37217`,
which resolves to `auth.rakutenmarketing.com/auth/realms/rakuten-advertising/…`. Not Impact, not CJ,
not Awin. Contact: `kobo-affiliates@rakuten.com`.

Terms on the TW page: 「透過電子書、有聲書與 eReaders 賺取高達 5% 佣金」, 「善加利用 14 天 Cookie 追蹤
功能」, 「成為聯盟行銷合作夥伴是完全免費的」, approval in 1–2 weeks. **The page says nothing about a
product catalogue, data feed, or price data.**

🔴 **Is the Taiwan storefront covered? The evidence says no, for data purposes.** The `mid` parameter
identifies the Rakuten Advertising advertiser, and the TW storefront's own footer signs you up to
**mid 37217**, shared with Hong Kong. Rakuten Advertising publishes a first-party spreadsheet of every
advertiser offering multi-language/multi-currency product feeds
([Advertisers With Global Product Feeds and Currencies](https://pubhelp.rakutenadvertising.com/hc/en-us/articles/4415249148813-Advertisers-With-Global-Product-Feeds-and-Currencies),
page states `Last edited: September 02, 2026`). Two decisive facts from it:

- **mid 37217 — the advertiser the Taiwan storefront links to — is literally named "Rakuten Kobo US"**,
  and its feed rows are EN/AU/AUD, EN/CA/CAD, EN/GB/GBP, EN/US/USD, ES/ES/EUR, FR/CA/CAD, FR/FR/EUR.
- **No Kobo advertiser (37217, 37219, 37589, 38131, 39331) has a single TW / zh / TWD feed row.**
  TWD feeds exist on the network generally (Nike APAC, Taobao, Lululemon APAC) — Kobo just has none.

The catalogue product itself is explicitly aimed at your use case. From
[Product Catalog Overview](https://pubhelp.rakutenadvertising.com/hc/en-us/articles/4412243602189-Product-Catalog-Overview)
(`Last edited: July 22, 2026`):

> Sites that are a good fit for Product Catalog are generally large, technically complex sites that add
> value to the shopping experience... **Price comparison sites**: Sites that compare the same or similar
> products from different advertisers by price and show the best deals...

and it does carry price —
[Appendix A – File Field Definitions](https://pubhelp.rakutenadvertising.com/hc/en-us/articles/8191594256013-Product-Catalog-Appendix-A-File-Field-Definitions):
`Field 13: Sale Price — This price reflects any discounts.` / `Field 14: Retail Price` /
`Field 26: Currency — …Use "USD"… "CAD"… "GBP"… "JPY"… "AUD"… or "EUR". USD is default.` — the currency
enumeration **does not even list TWD**.

Whether mid 37217 offers *any* Product Catalog is visible only inside a logged-in Publisher Dashboard —
**Not established** without an account. Given the naming and feed set, **do not plan on TWD prices from
this channel.**

**Public Kobo developer API: Not established (negative).** `developer.kobo.com`, `developers.kobo.com`
and `partners.kobo.com` all **fail DNS**; `api.kobo.com` returns 404; `/p/partners` 404s.
[Kobo Writing Life](https://www.kobo.com/tw/zh/p/writinglife) is a self-publishing UI with no API, and
a search of its help centre for "API" returns **`No results for "API"`**. No endpoint is reported
because none was found.

### robots.txt

[https://www.kobo.com/robots.txt](https://www.kobo.com/robots.txt) is long and locale-partitioned. The
two parts that govern this app:

```
Disallow: /tw/*
Allow: /tw/zh
```

and the final block:

```
Disallow: /*/search?*
Disallow: *search?Q*
Disallow: *search/query?q
Allow: */search?query=978*&utm_source=koboblog*
Allow: */search?query=9*
Allow: /*/search?Q*
```

**Plainly: `/tw/zh/ebook/...` product pages are WITHIN the directives** (`Allow: /tw/zh` is a longer,
more specific match than `Disallow: /tw/*`). ⭐ And **`Allow: */search?query=9*` explicitly permits
ISBN-prefixed searches** — a longer, more specific match than `Disallow: /*/search?*`. Kobo has, in
effect, carved out exactly the query shape a book price-comparison site needs. There are **no
AI-crawler-specific groups** anywhere in the file.

⚠️ **But robots permission is not access.** Fetching a `/tw/zh/ebook/…` product page with plain curl
returned **HTTP 403 with `<title>Challenged | Kobo.com</title>`** on both of my attempts (84,510 and
21,741 bytes), including with full browser headers. Kobo runs live bot mitigation in front of product
pages. A separate fetch succeeded earlier in this research, so the challenge is **intermittent** —
treat Kobo as unreliable for unattended polling regardless of what robots.txt permits.

### Terms of service

[https://www.kobo.com/tw/zh/p/termsofuse](https://www.kobo.com/tw/zh/p/termsofuse) **301-redirects to**
[https://authorize.kobo.com/terms/termsofuse](https://authorize.kobo.com/terms/termsofuse), which serves
`<html lang="zh-tw">` under `Accept-Language: zh-TW`. **「上次更新日期： 2026年5月」 — the most current
terms of the six, four months old.**

🔴 **This is the only channel with an explicit anti-scraping clause.** §5 使用限制:

> 使用任何「深層連結」、「抓取網頁」、「機器人」、「蜘蛛」或其他自動裝置、程式、演算法或方法，或任何類似或等效的手動程序，來存取、獲取、複製或監視本服務或任何網站內容的任何部分，或以任何方式重製或規避本服務或任何網站內容的導航結構或呈現方式，以取得或企圖取得任何非本網站故意提供之素材、文件或資訊；

*Gloss: Prohibits any "deep link", "web scraping", "robot", "spider" or other automated device,
program, algorithm or method — or equivalent manual process — to access, acquire, copy or **monitor**
any part of the Service or Site Content.* Note **「監視」 (monitor)** — price tracking is monitoring.
The section closes: 「系統或網路安全性的違規行為可能導致民事或刑事的法律責任。」

And, in the same section, the database clause:

> …未經 Kobo 事前書面許可，不得以任何形式或方法對於本網站內容的全部或一部進行修改、複製、散布、設計、重製、再次發行、下載、展示、發佈、傳輸或出售。…您將取得存取本服務和網站內容的有限授權…**以供您個人、非商業的使用**。**您不得將本網站內容上傳或再次發行至任何網際網路、內部網路或外部網路網站，或將資訊納入任何其他資料庫或加以蒐集整理**，並嚴格禁止就本網站內容進行其他的使用。…且**禁止使用任何數據挖掘、機器人或類似的資料蒐集或擷取方法**，進行使用。

*Gloss: Your licence is limited to **personal, non-commercial use**. You may **not** republish Site
Content to any website, **nor incorporate the information into any other database or compilation**, and
**any data mining, robots or similar data gathering/extraction methods are prohibited**.*

Also: 「您不得使用本服務來刊登廣告或進行任何商業宣傳」.

**Assessment.** These terms are about as adverse to a scraped price-comparison product as terms get —
「納入任何其他資料庫」 describes a comparison database precisely, and they are **current (2026年5月)**.
Note the direct tension with Kobo's own robots.txt, which affirmatively `Allow`s `*/search?query=9*`.
**Robots.txt permission does not override the ToU.** This one needs legal sign-off before any build.

### Price rendering

🟡 **Server-rendered, but guarded.** When a fetch gets through the challenge, the price is present twice:

```html
<meta notranslate property="og:price" content="464.000">
<meta notranslate property="og:currency_code" content="TWD">
<meta notranslate property="og:availability" content="instock">
```

and a full price object inside a `data-kobo-gizmo-config` attribute in the initial server response:

```
"priceDetails":{"listPrice":"NT$464","displayPrice":"NT$464","displayCurrency":"TWD",
"currency":"TWD","amountSaved":"NT$0","discountPercentage":"0","koboLovePrice":"NT$464",
"wasPrice":null,"basePrice":"NT$442","taxtotal":"NT$22","total":"NT$464"}
```

**There is NO `Product` JSON-LD** — the three `ld+json` blocks are two `BreadcrumbList` and one
`Organization`. Do not build on JSON-LD here.

⚠️ **The visible `<span class="price" data-bind="text: priceDetails.displayPrice">` is EMPTY** — it is a
Knockout.js binding. A naive CSS-selector scrape returns an empty string even on a successful fetch.
The data it binds to is already in the server response, so no XHR is needed — but extraction must
target `og:price` first, then the `data-kobo-gizmo-config` JSON, and never `.price` text content.
(A client-side refresh endpoint `"getLatestPriceDetailsUrl":"/product/getlatestpricedetails"` is
visible in that blob; it was **not** called and its shape is not characterised here.)

### ISBN lookup

🟢 **`https://www.kobo.com/tw/zh/search?query={ISBN13}`** — verified independently.
`?query=9786263149908` returned **HTTP 301** with
`Location: https://www.kobo.com/tw/zh/ebook/0DI0Jij8eTGocn3s-vXulg?sId=…`. **On a single exact ISBN
match, search redirects straight to the product page** — one request gets you there. On a miss it
returns HTTP 200 with `顯示 "…" 的結果` and `0 個結果`, so hit and miss are cleanly distinguishable.
Product URL: `/tw/zh/ebook/{opaque-id}`.

---

## 6. Readmoo 讀墨 readmoo.com

### Official API / affiliate — AP 推書夥伴

Documented on Readmoo's **unblocked** first-party help host, all articles dated **2026年6月30日**:
[什麼是AP推書夥伴?](https://help.readmoo.com/zh-TW/articles/15700650-什麼是ap推書夥伴)

> 推書夥伴就是所謂的 AP (Affiliate program）合作模式…同意參與的夥伴會獲得專屬的商品推薦網址，只要有消費者透過該夥伴的推薦、推廣並經由該推薦網址完成交易，該夥伴即可獲得該連結商品之 **3％ 分潤**。
> ※須注意：犢幣、訂閱、預購、預付及集資型商品不適用於本回饋機制

[Free and instant](https://help.readmoo.com/zh-TW/articles/15700848-參與推書夥伴計畫會有費用嗎-如何參與推書夥伴計畫):
「參與推書夥伴計畫**完全免費**，只需要註冊 Readmoo 帳號，並啟用「推書夥伴」功能即可。」 — **no approval
gate**, materially easier than Kobo's 1–2 week review.
[No residency restriction](https://help.readmoo.com/zh-TW/articles/15700868-我不是臺灣人-或我不住在臺灣-也可以參與嗎):
non-TW partners are paid via PayPal or convert to 犢幣.

⚠️ [Attribution is fragile](https://help.readmoo.com/zh-TW/articles/15700889-推薦連結沒有生效-可能有哪些原因):
a **7-day** window (vs Kobo's 14), and attribution silently fails if the book was already on the buyer's
待購清單, on in-app→external-browser handoff, or under ad-blockers / iCloud Private Relay.

🔴 **Across all first-party AP articles there is no mention of any product feed, catalogue, API, or price
data.** Every mechanism described is per-product link generation via `ap.readmoo.com/share/create`.
**A price-comparison app cannot source Readmoo prices through 分潤計畫.** (The 合作條款 at
`ap.readmoo.com/faq#terms` is on an AI-blocked host and rendered client-side — **Not established**.)

**Public API: Not established.** The only evidence any API exists is Readmoo's own robots.txt, which
lists `/api/me/`, `/api/product/overview`, `/api/profile/`, `/api/highlights/`, `/api/reviews`,
`/api/special_offer` — **all `Disallow`ed**, characterised in their own comment as
「動態 / 需登入 / 不需被索引」 endpoints. Undocumented, unauthorised, and robots-excluded. **Not called.**

### robots.txt

[https://readmoo.com/robots.txt](https://readmoo.com/robots.txt) is hand-written with Chinese comments —
unusually informative, and worth reading in full before building. (Note `www.readmoo.com/robots.txt`
returns an HTML page; the canonical file is on the apex domain.) The governing groups:

```
# === 2026-08 新增:AI / 非搜尋用途爬蟲整站封鎖 ===
# 這些「不是」Google/Bing 的搜尋索引爬蟲,封鎖它們「不影響 SEO」…
User-agent: GoogleOther
User-agent: Google-Extended
User-agent: GPTBot
User-agent: CCBot
User-agent: ClaudeBot
User-agent: anthropic-ai
User-agent: Bytespider
User-agent: Amazonbot
User-agent: PetalBot
User-agent: meta-externalagent
Disallow: /
```

```
User-agent: *
Crawl-delay: 10
Disallow: /third-party-license/
Disallow: /checkout/
Disallow: /oauth2/
Disallow: /oauth/
Disallow: /login/
Disallow: /next/login
Disallow: /search/
Disallow: /book/social_dashboard/
Disallow: /api/me/
Disallow: /api/product/overview
Disallow: /api/profile/
Disallow: /api/highlights/
Disallow: /api/reviews
Disallow: /api/special_offer
```

(duplicated verbatim for `AdsBot-Google` / `AdsBot-Google-Mobile`, with their own comment explaining
that AdsBot ignores the `*` group.)

**Plainly: a generic crawler hitting `/book/` product pages is WITHIN these directives, subject to
`Crawl-delay: 10`. `/search/` is OUTSIDE them for every agent. An AI-labelled crawler is outside them
entirely.** The **2026-08** date makes this the freshest robots.txt of the six, and the block was added
in response to a load incident their comments describe.

### Terms of service

**[https://readmoo.com/terms/service](https://readmoo.com/terms/service)** (note: `/page/terms` 404s).
**「更新日期：2015/10/21」 — over a decade old**, and eleven years older than the robots.txt AI block.
⚠️ Flag as stale: the terms and the operator's actual current posture have visibly diverged.

**There is NO clause on 自動化程式 / 機器人 / 爬蟲.** All 16 sub-items of §10 使用者的守法義務及承諾 were
checked. The nearest analogue is item 12:

> 干擾或中斷本服務或伺服器或連結本服務之網路，或**不遵守連結至本服務之相關需求、程序、政策或規則等**。

*Gloss: Do not interfere with the Service, **or fail to comply with the requirements, procedures,
policies or rules for connecting to the Service.*** That trailing clause plausibly **incorporates
robots.txt by reference** as a "policy or rule for connecting to the Service" — which matters a great
deal here, because Readmoo's robots.txt is where the actual prohibition lives. §10's opening adds:
「您承諾…遵守中華民國相關法規及一切使用網際網路之國際慣例。」

§15 智慧財產權的保護:

> 任何人不得逕自使用、修改、重製、公開播送、公開傳輸、公開演出、改作、散布、發行、公開發表、進行還原工程、解編或反向組譯。
> 若您欲引用或轉載前述軟體、程式或網站內容，除明確為法律所許可者外，必須依法取得本服務或其他權利人的事前書面同意。

*Gloss: No one may of their own accord use, modify, reproduce, publicly transmit, adapt, distribute…
To **quote or republish** site content — **except where clearly permitted by law** — you must obtain
**prior written consent**.* (The 「除明確為法律所許可者外」 carve-out is worth noting: it is the one
clause among the six that expressly leaves room for what the law permits.)

§18 不得為商業利用, in full:

> 您同意不對本服務任何部分或本服務（包括：會員內容、廣告、軟體及帳號等）之使用或存取，進行重製、拷貝、出售、交易、轉售或作任何商業目的之使用。

*Gloss: You agree not to reproduce, copy, sell, trade, resell **or use for any commercial purpose** any
part of the Service, or your use of or **access to** the Service.*

Operator: 群傳媒股份有限公司, 統編 53739652.

### Price rendering

🟡 **Readmoo says `/book/` is SSR — but hedges it themselves. Medium confidence.** No Readmoo product
page was fetched (AI agents are `Disallow: /`). The evidence is Readmoo's own comment inside
[readmoo.com/robots.txt](https://readmoo.com/robots.txt):

> 下列路徑為「動態 / 需登入 / 不需被索引」的端點,爬它們只會空耗 php-fpm 與 DB,
> 且對 SEO 無益(**書頁 /book/、分類 /category/ 為 SSR,仍保持可索引**)。

*Gloss: "…book pages /book/ and category pages /category/ are SSR, and remain indexable."* This also
establishes the product URL namespace: **`/book/`**.

But the very next comment reveals it is an unverified assumption:

> **若前端確認書頁/分類頁皆為 SSR(顯示內容不依賴前端呼叫 /api/)**,
> 可將上面數條 /api 規則整併為一條 `Disallow: /api/`,降載效果更大。

*Gloss: "**If** the front-end team confirms that book and category pages are all SSR (i.e. displayed
content does not depend on calling /api/), the several /api rules could be merged into one…"*

They kept `/api/product/overview` and `/api/special_offer` individually Disallowed rather than blanket-
blocking `/api/` **precisely because they were not sure the book page renders without them**. Given
`/api/special_offer` is named separately, the plausible reading is that base metadata is SSR but
**promotional/discount pricing may still arrive by XHR** — which is exactly the distinction a price-
comparison app cares about. **Treat "SSR includes the final selling price" as unverified.**

### ISBN lookup

🔴 **Not established.** A `/search/` path space exists and is `Disallow`ed **for every agent**, so it was
not fetched and the parameter format is unknown. More importantly, Readmoo's own help article
[有哪些方式可以在 Readmoo 上快速找到書籍？](https://help.readmoo.com/zh-TW/articles/11388146-有哪些方式可以在-readmoo-上快速找到書籍)
(2025年5月21日) enumerates every supported search input:

> **搜尋**：…輸入想找的**書名、作者、或是出版社**。
> **進階搜尋**：…搭配使用**書籍價格、書檔格式**來縮小搜尋的範圍…

**ISBN is not listed as a supported search input anywhere in Readmoo's own documentation.** Do not plan
an ISBN→Readmoo lookup on `/search/`.

---

## Legal context (Taiwan)

> **Not legal advice.** Statute text below was fetched from 全國法規資料庫 (`law.moj.gov.tw`) and 函釋
> from 智慧財產局 (`tipo.gov.tw`) on 2026-09-04; the 著作權法 and 公平交易法 quotes marked ✔ were
> independently re-verified against the raw HTML for this document. Both TIPO and the statutes stress
> that 著作權係屬私權 and that whether any specific item is protected is for a court to decide on the
> facts. Points needing a Taiwanese lawyer's judgement are flagged.
>
> All `law.moj.gov.tw` pages carry 法規整編資料截止日：民國 115 年 08 月 28 日 (2026-08-28).

### 著作權法 — factual price data

**The price number itself is almost certainly not protected.** Three articles do the work.

**第 10-1 條** — the idea/expression dichotomy ✔
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0070017&flno=10-1)):

> 依本法取得之著作權，其保護僅及於該著作之表達，而不及於其所表達之思想、程序、製程、系統、操作方法、概念、原理、發現。

*Gloss: Copyright protects only the **expression**, not the ideas, procedures, systems, methods,
concepts, principles or **discoveries** expressed.*

**第 9 條** — excluded subject matter ✔
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0070017&flno=9)):

> 標語及通用之符號、名詞、公式、**數表、表格**、簿冊或時曆。
> 單純為傳達事實之新聞報導所作成之語文著作。

*Gloss: Slogans, common symbols, terms, formulas, **numerical tables, forms**, ledgers and calendars are
not the subject matter of copyright; nor are news reports that merely convey facts.*

**第 7 條** — compilation works ✔
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0070017&flno=7)):

> 就資料之選擇及編排具有創作性者為編輯著作，以獨立之著作保護之。

*Gloss: A compilation is protected as an independent work **only where the selection and arrangement of
the data are creative**.*

**第 65 條** — 合理使用, the four factors
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0070017&flno=65)) — 一、利用之目的及
性質，包括係為商業目的或非營利教育目的；二、著作之性質；三、所利用之質量及其在整個著作所占之比例；
四、利用結果對著作潛在市場與現在價值之影響. **Both factor 1 (commercial purpose) and factor 4 (market
effect) cut against a commercial aggregator** — do not plan to rest on fair use.

**No sui generis database right exists in Taiwan.** A full extraction of the
[著作權法 article list](https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=J0070017) (158 headings,
第1條–第117條 plus every 之N article) and a full-text scan of the statute body for 「資料庫」 returns
**zero hits** — the only occurrences on the page are the website's own name. Protection for a data
collection runs through 第7條 編輯著作 or not at all. TIPO says so itself in
[（九０）智著字第0900008111號](https://www.tipo.gov.tw/tw/copyright/692-11827.html) (090-09-10) —
written, notably, **to the 公平交易委員會**:

> 本法對於「資料庫」並無定義…至於就資料之選擇及編排未具有創作性者，因其非本法第七條第一項所定之「編輯著作」，**無從依本法受保護**。
> 基於國際間對於不具創作性之資料庫之保護尚無明確之共識，是本局目前對於不具創作性之資料庫**暫不特別立法保護之**。

⚠️ **Currency caveat:** that is a **2001** statement and no post-2001 restatement was established. The
statutory scan is current; have counsel confirm the 函釋 still reflects TIPO's position.

**TIPO has said directly that price data is unprotected.** The most relevant 函釋 for this app is
[電子郵件1070814](https://www.tipo.gov.tw/tw/copyright/692-15747.html) (令函日期 107-08-14) — TIPO
answering a question about **scraping price data with a 網路爬蟲**:

> 一、…所詢「金融股票指數基金報價資訊」之資訊若欠缺原創性、創作性，或**僅係將股票價格、交易資訊等以表格方式予以羅列，則該內容非屬著作權法保護之標的**。
> 二、**惟如您以網路爬蟲所取得的資訊包括著作權法所稱之「著作」**…**則複製該等著作並於網路上提供，可能涉及「重製」及「公開傳輸」之行為**，除有著作權法第44條至第65條所規定之合理使用情形外，應經著作財產權人同意或授權…
> 三、前開行為如係透過設於美國之主機所為…（智慧財產法院106年度刑智上易字第5號判決參照），依據刑法第4條規定，我國有審判權…

⭐ **This is the single most useful document for the build.** It draws the line exactly where this app
sits: **¶1 the price is free; ¶2 what the crawler picks up *alongside* the price is not; ¶3 hosting
offshore is no jurisdictional shield.** For a book comparison site the 著作 next to the price are the
**封面圖片 (攝影/美術著作), 內容簡介, 作者簡介 and 目錄**.

Supporting 函釋: [電子郵件1000502](https://www.tipo.gov.tw/tw/copyright/692-13878.html) (100-05-02) —
「**單純之事實資料(如電話、地址、菜單) 非屬著作權法…所保護之標的**…提供者亦無法依本法規定限制他人利用之」
(note the last clause: *the supplier cannot use the Act to restrict others' reuse*);
[電子郵件1140326](https://www.tipo.gov.tw/tw/copyright/692-33512.html) (114-03-26, the most recent) —
expressly includes 「交通業者公告之時刻表、**票價表**」, the closest official analogue to a shop's price
table; [電子郵件940415](https://www.tipo.gov.tw/tw/copyright/692-12251.html) —
「**如僅係蒐集大量資料而未就資料加以選擇編排之電子資料庫，則不屬於著作權法保護之標的**」.

**This cuts both ways.** [電子郵件1010419](https://www.tipo.gov.tw/tw/copyright/692-14161.html)
(101-04-19): 「如僅係蒐集大量資料重新登打、整理，但其選擇或編排不具創作性者，則不屬於編輯著作。」 —
*a bookstore's listing is a 編輯著作 only if its selection and arrangement are creative*, and by the
same test **your** aggregated comparison table owns no 編輯著作 either unless you invest creativity in
it. Either way the individual prices never become yours.

### 公平交易法 — the real exposure for a comparison site

Source: [公平交易法 (pcode=J0150002)](https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=J0150002),
修正日期 民國 106 年 06 月 14 日, fully in force.

**第 21 條** — false or misleading representations, **price expressly named** ✔
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0150002&flno=21)):

> 事業不得在商品或廣告上，或以其他使公眾得知之方法，對於與商品相關而足以影響交易決定之事項，為虛偽不實或引人錯誤之表示或表徵。
> 前項所定…事項，包括商品之**價格**、數量、品質、內容…
> …**廣告媒體業在明知或可得而知其所傳播或刊載之廣告有引人錯誤之虞，仍予傳播或刊載，亦與廣告主負連帶損害賠償責任。**

*Gloss: No false or misleading representation about matters affecting a transaction decision —
**expressly including price**. Ad **media** that knowingly or negligently carry a misleading ad are
jointly and severally liable with the advertiser.* ⚠️ That last limb is what a lawyer should examine
hardest for a site that republishes someone else's price.

**第 24 條** — 營業誹謗
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0150002&flno=24)):

> 事業不得為競爭之目的，而陳述或散布足以損害他人營業信譽之不實情事。

*Gloss: No stating or disseminating false information, for competitive purposes, capable of damaging
another's business reputation.* — engaged if the site displays a wrong (e.g. inflated) price against a
named bookstore.

**第 25 條** — the catch-all
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=J0150002&flno=25)):

> 除本法另有規定者外，事業亦不得為其他足以影響交易秩序之欺罔或顯失公平之行為。

Penalties under **第 42 條**: administrative fines NT$50,000–25,000,000, rising to
NT$100,000–50,000,000 per repetition; plus 第29–31條 injunctive relief, damages, and up to **treble
damages** for intentional conduct.

⭐ **The provision that most directly targets a scraping-based aggregator.**
公平交易委員會對於公平交易法第二十五條案件之處理原則,
[ftc.gov.tw docid=266](https://www.ftc.gov.tw/internet/main/doc/docDetail.aspx?uid=167&docid=266),
**第七點 (二) 榨取他人努力成果, item 4** — verified verbatim ✔:

> 4.抄襲他人投入相當努力建置之網站資料，混充為自身網站或資料庫之內容，藉以增加自身交易機會。

*Gloss: **Copying website data that another party invested considerable effort in building, passing it
off as the content of one's own website or database, so as to increase one's own transaction
opportunities.***

Currency: last amended 105.12.28 第1312次委員會議修正全文, promulgated 106.1.13 公法字第10615600201號令;
it appears on the FTC's live
[處理原則 listing](https://www.ftc.gov.tw/internet/main/doc/docList.aspx?uid=167&mid=37) and is **not**
on the 廢止／停止適用 register.

**Read this together with TIPO's 智著字第0900008111號 above** — 公平會 asked TIPO how to remedy
unauthorised extraction from a non-creative database, and TIPO answered *not under copyright*. The
picture is consistent: **in Taiwan, database misappropriation is a Fair Trade Act question, not a
copyright question.** Two elements mitigate, and both are design levers:

- **「混充為自身…內容」 (passing off as one's own).** A site that **attributes every price to its source
  bookstore, shows the shop name beside each offer, and links out to buy** is a materially different
  case from one that presents the data as its own. The design in `design/README.md` already does this —
  the 各通路報價 table names each 通路 and carries a 前往購買 button per row. **Keep it that way.**
- **The 第二點 threshold 「足以影響交易秩序」** — the FTC only takes a case under this article where the
  conduct affects market trading order.

**The stale-price rule.** 公平交易委員會對於網路廣告案件之處理原則,
[law.ftc.gov.tw GL000222](https://law.ftc.gov.tw/law/LawContent.aspx?id=GL000222), 修正日期 民國
112-02-21, 第六點:

> 事業刊播網路廣告後應隨時注意廣告刊播內容是否與實際相符。如其廣告內容錯誤、變更或已停止銷售該商品或服務，**應即時更正**。

*Gloss: After publishing, a business must continuously monitor whether the content still matches reality
and **correct it immediately** if it is wrong, changed or discontinued.* And the 第21條處理原則 附表二
item 十 names the closest case-type: 「表示或表徵訂價長期與實際售價不符且差距過大者。」 — *a displayed
price persistently and materially diverging from the actual selling price.*
⚠️ **This is a direct constraint on the design's stated "價格每 6 小時更新一次" cadence** — see the
Recommendation.

**比較廣告.** 公平交易委員會對於比較廣告案件之處理原則,
[law.ftc.gov.tw GL000217](https://law.ftc.gov.tw/law/LawContent.aspx?id=GL000217), 修正日期 105-11-14,
第五點 prohibits 「(三)對相同商品之比較採不同基準或條件」 and 「(六)…於比較項目僅彰顯自身較優項目，而
故意忽略他事業較優項目，致整體印象上造成不公平之比較結果」. A price table most easily trips these by
**comparing a list price against a member price, or failing to normalise shipping, coupons and
membership discounts** across channels.

⚠️ **Definitional caveat, and the first question for counsel.** Both 網路廣告處理原則 (第二點：
「事業為銷售**其**商品或服務…」) and 比較廣告處理原則 (第二點：「就**所提供**商品或服務…」) define their
subject by reference to an enterprise selling **its own** goods. A pure comparison site sells nothing.
**Whether a non-selling aggregator is a 廣告主, a 廣告媒體業 under 第21條第5項, or falls outside 第21條
entirely and into 第25條, is exactly the question to put to a lawyer.**

**On 比價網站 specifically: Not established.** Exhaustive searching of `ftc.gov.tw` found **no**
處理原則, **no** 解釋令 and **no** 處分書 addressing a 比價網站 or 比價平台. The only occurrences of the
string on the site describe **foreign** cases (EU Google Shopping) in the FTC's
[數位經濟競爭政策白皮書](https://www.ftc.gov.tw/upload/95fc5364-48a3-4f64-9821-2eeae4bbdaa5.pdf). The
FTC's [處分案件 database](https://www.ftc.gov.tw/internet/main/decision/decisionlist.aspx) (5,841
records) uses a POST-only form that could not be keyword-queried from this environment. The closest
verified shopping-platform decision,
[公處字第114106號 (富邦媒體/momo)](https://www.ftc.gov.tw/uploadDecision/34305cca-c582-48cf-8c7f-a35903e1acd8.pdf),
concerns a **quality** claim, not price — but it does confirm that **both the platform operator and the
supplier were treated as 廣告主** and each fined NT$50,000.

### 個人資料保護法 — not implicated by price data; implicated by your users

Source: [個人資料保護法 (pcode=I0050021)](https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=I0050021).

⚠️ **Version warning.** The LawAll page shows 修正日期 民國 114 年 11 月 11 日, but the page also states
「※本法規部分或全部條文尚未生效，最後生效日期：未定」 — the 114-11-11 amendment (which **deletes 第27條**
and replaces it with 第20-1條) has 施行日期由行政院定之 and **is not yet in force**. The currently
operative text is the 民國 112 年 05 月 31 日 version at
[LawOldVer](https://law.moj.gov.tw/LawClass/LawOldVer.aspx?pcode=I0050021). 第2、8、19、20條 are
textually identical in both versions; only 第27條 changes.

**第 2 條第 1 款** — definition of 個人資料
([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=I0050021&flno=2)):

> 一、個人資料：指**自然人**之姓名、出生年月日、國民身分證統一編號、護照號碼、特徵、指紋、婚姻、家庭、教育、職業、病歷、醫療、基因、性生活、健康檢查、犯罪前科、**聯絡方式**、財務情況、社會活動及其他得以**直接或間接**方式識別該個人之資料。

Sharpened by the [施行細則 (pcode=I0050022)](https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=I0050022):
第2條 「本法所稱個人，指**現生存之自然人**」; 第3條 defines 間接識別 as requiring cross-reference with
other data the holder possesses.

**Plain statement: book price data is not 個人資料, and 個資法 is not implicated by the price-aggregation
function.** Reasoning from the statutory text only: a record of {book title, ISBN, shop, selling price}
**has no data subject**. The 第2條第1款 enumeration lists attributes **of a 自然人**, confined by
施行細則第2條 to a living natural person. A selling price is an attribute of a commercial offer by a
legal entity, not of an individual, and under 施行細則第3條 there is no other data set to combine it
with to reach one. ⚠️ One schema-dependent exception worth watching: **if you ever store an individual
second-hand seller's identity** from a TAAZE used listing, the analysis changes.

**But the app's own user accounts DO fall under 個資法.** An email collected for price-drop
notification is squarely 「聯絡方式」, and the app is a **非公務機關** under 第2條第8款.

- **第 8 條 告知義務** ([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=I0050021&flno=8))
  — six mandatory disclosures at the point of collection: 「一、…名稱。二、蒐集之目的。三、個人資料之
  類別。四、個人資料利用之期間、地區、對象及方式。五、當事人依第三條規定得行使之權利及方式。六、當事人
  得自由選擇提供個人資料時，不提供將對其權益之影響。」 **This is the required content of the signup
  form's privacy notice.**
- **第 19 條** ([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=I0050021&flno=19)) —
  collection requires a **特定目的 plus** one listed basis. For a sign-up-for-alerts flow the candidates
  are 第五款 「經當事人同意」 and 第二款 「與當事人有契約或類似契約之關係，**且已採取適當之安全措施**」.
- **第 20 條** ([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=I0050021&flno=20)) —
  use must stay within the collection purpose; and 「非公務機關依前項規定利用個人資料**行銷**者，當事人
  表示拒絕接受行銷時，**應即停止**」, with an opt-out mechanism offered **at first marketing contact, at
  the sender's cost**. Whether a price-drop alert the user asked for counts as 行銷 is a lawyer's call —
  **ship an unsubscribe link either way.**
- **第 27 條 安全維護** (currently in force, 112-05-31 text): 「非公務機關保有個人資料檔案者，應採行
  適當之安全措施，防止個人資料被竊取、竄改、毀損、滅失或洩漏。」

Competent authority: 第1-1條 names the 個人資料保護委員會; the full commission does not yet exist and the
[籌備處](https://www.pdpc.gov.tw/) is live. **Not established:** no 籌備處 guidance specific to
comparison sites.

### 刑法 妨害電腦使用罪 — does fetching a public page engage it?

Source: [中華民國刑法 (pcode=C0000001)](https://law.moj.gov.tw/LawClass/LawAll.aspx?pcode=C0000001),
修正日期 民國 115 年 07 月 22 日. Chapter 36 was inserted in 2003.

**第 358 條** ([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=C0000001&flno=358)):

> 無故輸入他人帳號密碼、破解使用電腦之保護措施或利用電腦系統之漏洞，而**入侵**他人之電腦或其相關設備者，處三年以下有期徒刑、拘役或科或併科三十萬元以下罰金。

**第 359 條** ([permalink](https://law.moj.gov.tw/LawClass/LawSingle.aspx?pcode=C0000001&flno=359)):

> **無故**取得、刪除或變更他人電腦或其相關設備之電磁紀錄，**致生損害於公眾或他人**者，處五年以下有期徒刑、拘役或科或併科六十萬元以下罰金。

Also relevant: **第 360 條** 「無故以電腦程式或其他電磁方式**干擾**他人電腦或其相關設備，致生損害於公眾
或他人者…」, and **第 363 條** — 「第三百五十八條至第三百六十條之罪，須**告訴乃論**」 (prosecutable only
on complaint).

**Reasoning from the statutory words — not a legal conclusion, and not legal advice:**

- **第358條** is built around three enumerated **means** (輸入他人帳號密碼 / 破解使用電腦之保護措施 /
  利用電腦系統之漏洞) plus a result, **入侵**. A plain unauthenticated GET of a page the operator
  publishes to the open web involves **none of the three enumerated means** on the face of the text, and
  「入侵」 connotes entering somewhere one is shut out of. **On the statutory text that fact pattern does
  not appear to match 第358條's elements.** ⚠️ **Conversely, defeating a login, a rate limiter, a CAPTCHA
  or bot management could put 「破解使用電腦之保護措施」 squarely in play — and Kobo, 誠品's athena host
  and 博客來 all sit behind exactly such measures today** (all three returned 403 challenge pages during
  this research). **Do not build anything that works around a bot challenge.**
- **第359條** is broader — no intrusion required, only 「無故取得…電磁紀錄」 plus 「致生損害」. Two
  contested elements: **「無故」** is undefined by statute, and a site published openly with a permissive
  robots.txt is a materially different case from one whose robots.txt or ToS forbids automated
  collection or which has sent a cease-and-desist — **what weight robots.txt and ToS carry in the 無故
  assessment is precisely the question for counsel**; and **「致生損害於公眾或他人」**, which a low-rate,
  cached read that removes nothing fits poorly and a service-degrading crawl fits well.
- **第360條** is what a badly-behaved high-frequency crawler most plausibly engages. Rate limiting,
  caching, honouring `Crawl-delay`, and **identifying your bot honestly in the User-Agent** are the
  practical mitigations.

### Existing Taiwanese book price-comparison services

Exactly **two** operating services do book-specific cross-bookstore comparison today. Every live
operator publishes a disclaimer saying the source site's price governs, and the two that describe their
method **describe automated collection openly in their public terms**. None cites a licensing
arrangement with the bookstores.

**✅ FindBook 找書網 — [findbook.com.tw](https://findbook.com.tw/)** (operator: 環通資訊股份有限公司).
⚠️ **The service is NOT at `findbook.tw`** — that domain returns HTTP 200 but is a **parked page**
("This domain may be for sale"), dead as a service. `findbook.com.tw` returns **HTTP 403** with a
Cloudflare JS interstitial to scripted requests while its `/robots.txt` returns 200 and is *permissive*
— so the 403 is **bot management, not a robots directive**. Live ISBN-keyed pages exist
(`findbook.com.tw/{ISBN}`) and Internet Archive captures run through 2026-06-14 showing a working
comparison UI (~4 sources per title, 金石堂 / 博客來 attributions). Its first-party 服務條款
(`findbook.com.tw/tos.aspx`) states its sourcing:

> 本服務係**主動或被動由網路**或圖書商品、服務供應商或個人使用者取得圖書商品資訊…並予公佈及發送，本服務針對前述提供之內容並不做實質之審查或修改…亦不保證所收集之資訊其合適性、可依賴性、即時性、有效性、正確性及完整性…

*Gloss: "This service **actively or passively obtains** book product information **from the internet** or
from suppliers or individual users…"* — plus a stale-price clause directing users to the source site:
「**務必建議您以該等網站資訊內容為準**」. *(Terms text retrieved from an Internet Archive capture dated
2026-03-25; corroborated word-for-word against the operator's **live** sibling page
[findprice.com.tw/tos.aspx](https://www.findprice.com.tw/tos.aspx), HTTP 200.)*

**✅ 買書網 — [buybook.tw](https://www.buybook.tw/)** (HTTP 200, © 2026). Aggregates **金石堂 / 博客來 /
TAAZE / 樂天Kobo** — four of your six. From its own
[關於我們](https://www.buybook.tw/html/about_us.htm): 「在2013年，「買書網」希望提供給網友快速找書、
買書、比價的服務」, with feature bullets including 「**定期價格監控**」. **資料來源: Not established** —
neither the About page nor the [隱私權政策](https://www.buybook.tw/html/policy.htm) says where price
data comes from. Note the irony worth recording: its own robots.txt reads
`User-agent: * / Disallow: /isbn` — **it blocks others from crawling the very comparison pages it builds
by aggregating bookstores.**

**Live but not book-capable:** [BigGo 比個夠](https://biggo.com.tw/) (樂方股份有限公司) — general
merchandise; an ISBN search returns 「搜尋不到符合…的相關結果」, it does not index by ISBN. Its
[免責與隱私](https://biggo.com.tw/official/disclaimers?tag=privacy) (最近更新 2026/01/09) is the clearest
first-party sourcing statement of any TW comparison site: 「**所有資料都是由網路上透過 Datafeed 、 API
以及其他技術收集而來**」 — note the mixed-method admission. It also already ships
「**歷史價格＆價格變動通知**」, i.e. the exact feature this app plans.
[Feebee 飛比價格](https://feebee.com.tw/) (第一網站股份有限公司) — general merchandise, carries books;
its [服務條款](https://feebee.com.tw/terms/) is the most explicit admission of scraping found anywhere:

> 飛比價格…**透過程式自動蒐集、彙整、分析**較熱門之電子商務網站各項商品或服務資訊…
> 飛比價格因透過程式自動蒐集…可能會因為所蒐集對象之電子商務網站**資料格式之變動或阻斷存取**而產生無法正確呈現或資料無法順利更新之情形。

*The last clause openly contemplates target sites changing format or **blocking access** — the operator
treats being blocked as an ordinary operational risk.*

**Dead (DNS resolution failure, curl exit 6, checked 2026-09-04):** `biprice.com.tw`, `bigprice.com.tw`,
`ezprice.com.tw`. **Not a comparison site:** TAAZE itself (「比價」 appears zero times in its homepage
HTML — it shows its own new/used inventory side by side, not rivals'); usedbook.tw; 書寶's 找書小幫手
(searches its own inventory only).

---

## Recommendation

### The defensible strategy: affiliate-first, tiered acquisition, price-only storage

**1. Join every affiliate programme before writing a scraper.** This is the highest-leverage legal move
available and it costs nothing.

| Channel | Action | Effort | What it buys |
|---|---|---|---|
| 金石堂 | Register a member account, activate 分紅大聯盟 | **Zero** — no application | ⭐ Activates the **FAQ Q10 carve-out** — explicit written permission to use 圖文 for promotion that links back. 4% on books. |
| Readmoo | Register, enable 推書夥伴 | **Zero** — no approval gate | Standing as a partner. 3%, 7-day cookie. |
| TAAZE | Email `myap_apply@taaze.tw` with the review form | Low — email approval | Partner standing; **and read the login-gated contract at `itemAp.html?typeFlg=JD`** |
| Kobo | Rakuten Advertising signup, mid 37217 | 1–2 week review | 5%, 14-day cookie. **Also: ask whether mid 37217 can supply a TWD Product Catalog** — currently it cannot |
| 博客來 | Human opens `ap.books.com.tw` and reads the terms | Manual | Unknown until read |
| 誠品 | No programme to join; ShopBack/美安 only | — | Nothing |

Then **email `affiliate@taaze.tw`** — the address TAAZE itself publishes — asking for (a) written
consent for price-listing 轉載 and (b) whether any feed exists. TAAZE's IP clause demands 事前書面同意
from **任何人**; this one email most reduces risk on that channel.

**2. Acquire data in three tiers, matched to what each channel actually permits.**

- **Tier A — build now (金石堂, TAAZE).** Both are robots-permitted for product *and* search, both
  server-render `schema.org` JSON-LD carrying `offers.price` **and** `isbn`. **Parse JSON-LD, never CSS
  selectors.** 金石堂 explicitly welcomes AI crawlers; TAAZE has no AI rules. Neither has an anti-bot
  ToS clause. These two channels alone give a working product — and TAAZE uniquely supplies **used-book
  offers** via `itemCondition`, which no competitor surfaces well.
- **Tier B — build after a human check (博客來, 誠品).** For **博客來**, a human must read the 使用條款 and
  confirm the `search/query` parameter; note the generic `*` robots group *does* permit product pages
  and `search.books.com.tw` affirmatively `Allow`s `/search/query/`. For **誠品**, the price is not in
  the HTML at all — the only options are a headless browser (slow, and Queue-it sits in front) or the
  robots-permitted-but-undocumented `athena.eslite.com/api/`; **the sitemap, not search, is the
  sanctioned discovery route** since `/search` is Disallowed for everyone. Both need legal sign-off.
- **Tier C — do not scrape (樂天Kobo, Readmoo).** **Kobo's May-2026 ToU explicitly bans 機器人, 蜘蛛,
  數據挖掘 and 「納入任何其他資料庫」** — a comparison database is exactly what it names — and Kobo
  bot-challenges in practice. **Readmoo blocks AI agents site-wide in robots.txt** and its §18 bans
  「商業目的之使用」. Robots.txt permission (Kobo's `Allow: */search?query=9*`) does **not** override the
  ToU.

**3. Fallbacks for Tier C — in order of preference.**

- **(a) Ask.** Kobo: ask Rakuten Advertising whether mid 37217 can supply a TWD Product Catalog — the
  network's own docs name **price comparison sites** as the intended audience, so the ask is
  well-founded even though no TWD feed exists today. Readmoo: ask via the AP programme.
- **(b) Deep-link without a stored price.** Show the channel with an affiliate link and **「查看
  Readmoo/Kobo 價格」** instead of a number. The design already tolerates this — `design/README.md`
  lists "a per-channel fetch-failure row" as a state to build. **A row that says "價格請至通路查看" is
  legally clean, honest, and still monetises through the affiliate link.**
- **(c) User-submitted prices**, clearly labelled as such with a timestamp and submitter attribution.
  Weak data quality; use only as a supplement.
- **(d) Drop the channel.** Honest and cheap. If Kobo and Readmoo both fall out, the product is a
  **four-channel** comparison — still the strongest book comparison in Taiwan, since 買書網 covers the
  same four and FindBook appears to cover about four sources per title.

**4. Store the number, link the rest — the TIPO rule made operational.** Per
[電子郵件1070814](https://www.tipo.gov.tw/tw/copyright/692-15747.html):

- ✅ **Store:** ISBN, price, list price, discount %, availability, channel, URL, timestamp. These are
  the 事實資料 TIPO says are outside copyright.
- ❌ **Do not mirror:** cover images, 內容簡介, 作者簡介, 目錄, 書評. These are 著作 and copying them
  engages 重製 and 公開傳輸. **The design already helps here** — `design/README.md` specifies
  `.blueprint.duotone` **placeholder** covers and supplies no book covers. **Keep the placeholders, or
  source covers from a licensed metadata provider, or hotlink only where a channel's affiliate terms
  permit it** (金石堂's FAQ Q10 does, for links back).
- Hosting abroad is not a shield (¶3 of the same 函釋).

**5. Attribute everything, and never present the data as your own.** This is the direct answer to
公平交易法第25條處理原則 七(二)4 「混充為自身網站或資料庫之內容」. The prototype design already does the
right thing — per-channel名稱, per-row 前往購買 button, 通路 swatches. **Formalise it:** every price
displays its 通路 name, every offer links out, and the 資料來源說明 footer link (already in the design)
becomes a real page naming each source.

**6. Fix the refresh cadence — the design's "價格每 6 小時更新一次" is a compliance surface.**
網路廣告處理原則第六點 requires 「**應即時更正**」, and 第21條處理原則 附表二 item 十 targets
「訂價長期與實際售價不符且差距過大」. Concretely:
- Show the **取價時間** on every price (the design already specifies this on the results header and the
  detail disclaimer — **extend it to every row**).
- Keep the disclaimer copy, but change it from the prototype's 「價格為示意資料」 to a real one:
  **「價格擷取自各通路網站，僅供參考，實際售價以各通路結帳頁面為準。」** Every live TW comparison site
  publishes an equivalent — FindBook's 「務必建議您以該等網站資訊內容為準」 and Feebee's 「應自行連結至
  各商品或服務頁面確認」 are the models.
- **Expire rather than display stale data.** If a channel's price is older than a threshold, show
  「價格更新中」 rather than a number you no longer stand behind.
- **Normalise the comparison basis** (per 比較廣告處理原則 第五點(三)(六)): compare like with like —
  do not put a member price beside a list price, and disclose where shipping or coupons are excluded.

**7. Crawl politely, and never defeat a protection measure.** This is the mitigation for 刑法第359條
「無故」/「致生損害」 and 第360條 「干擾」, and for 金石堂's 「不干擾或混亂網路服務」 clause:
identify the bot honestly in the User-Agent with a contact URL; honour `robots.txt` per-agent and
Readmoo's `Crawl-delay: 10`; cache aggressively (the 6-hour cadence is your friend here); back off on
429/5xx; **never work around Kobo's challenge, 誠品's Queue-it, or 博客來's Cloudflare rules**; and stay
off the paths each site names — `kingstone.com.tw/api/`, TAAZE's three XHR endpoints, `eslite.com/search`,
`readmoo.com/search/` and its `/api/*`.

**8. Your own users are the one clear 個資法 obligation.** The watch-list email is 「聯絡方式」. Ship a
第8條-compliant privacy notice at signup (all six disclosures), rely on 第19條第五款 consent, put an
unsubscribe link in every notification email (第20條), and take 第27條 security measures. **Price data
itself raises no 個資法 issue.**

### What this means for the build in `design/README.md`

- The 六大通路 promise is achievable at **four channels reliably** (金石堂, TAAZE, plus 博客來 and 誠品
  after human/legal checks) and **two channels as deep-links without stored prices** (Kobo, Readmoo).
- Per-channel failure is not an edge case, it is the **normal steady state** — the design's
  "per-channel fetch-failure row" is load-bearing, not optional. Add a distinct **「不提供價格」** state
  alongside it for Tier C channels.
- The 電子書 format filter matters more than it looks: Kobo and Readmoo are **ebook-only** channels, so
  if they become deep-link-only, the 電子書 comparison mode leans on 金石堂電子書, TAAZE 數位商品 and
  誠品電子書.
- Keep the cover placeholders. They are a legal feature, not a design gap.

---

## Open questions

**Blocked by robots.txt for an AI agent — a human must resolve these:**

1. **博客來 使用條款.** URL and full text unread. Candidates `www.books.com.tw/web/fhelp_c_terms/` and
   `/web/sys_serviceterms/`. Does it contain an anti-automation clause? This is the biggest single gap.
2. **博客來 price rendering.** Server-rendered or XHR? Is there JSON-LD? Unknown.
3. **博客來 ISBN search parameter.** The path `search.books.com.tw/search/query/` is established from
   robots.txt; the query parameter name is not. **Deliberately not guessed.**
4. **博客來 AP 策略聯盟 terms.** Signup URL, eligibility, commission, and whether any data feed exists.
   `ap.books.com.tw` is live but its own robots.txt asks all crawlers to stay out.
5. **Readmoo price rendering.** Readmoo's own robots.txt asserts `/book/` is SSR but hedges it in the
   next comment; `/api/special_offer` being separately Disallowed suggests **discount pricing may still
   arrive by XHR**. Unverified.
6. **Readmoo ISBN search.** Not established that ISBN is even a supported input — Readmoo's own docs
   list only 書名/作者/出版社. `/search/` is Disallowed for every agent.

**Behind a login or an account:**

7. **TAAZE affiliate contract** at `taaze.tw/itemAp.html?typeFlg=JD` — login-gated, and the document
   most likely to address price-comparison publishers explicitly.
8. **Readmoo AP 合作條款** at `ap.readmoo.com/faq#terms` — AI-blocked host, client-rendered SPA.
9. **Whether Kobo mid 37217 offers any Product Catalog at all.** Visible only inside a logged-in Rakuten
   Advertising Publisher Dashboard. What *is* established: **no Kobo advertiser has a TWD/zh feed.**
10. **Whether 金石堂 or TAAZE is an affiliates.one 聯盟網 advertiser.** The public pages publish no
    merchant directory; the list is behind `pub.affiliates.one` login. Notably affiliates.one *does*
    advertise a 「商品API」 to its publishers — if either merchant is on it, that changes the picture.
11. **iChannels commission rates for 博客來 and 金石堂.** Both are confirmed advertisers, but rates need
    a publisher account. Established regardless: **iChannels' publisher API has no price feed.**

**Legal questions for a Taiwanese lawyer — the three that matter most:**

12. Is a **non-selling aggregator** a 廣告主 under 公平交易法第21條 / 網路廣告處理原則 (both of which
    define their subject as an enterprise selling **its own** goods), or a 廣告媒體業 under 第21條第5項,
    or outside 第21條 and into 第25條?
13. Does systematic collection of bookstore listings engage **第25條處理原則 七(二)4**
    「抄襲他人投入相當努力建置之網站資料」 — weighing the mitigating 「混充為自身」 element (this app
    attributes and links out) against the 「足以影響交易秩序」 threshold?
14. What does **「無故」 in 刑法第359條** mean for a public page fetched against an operator's stated
    objection (a robots.txt AI block, a ToS clause, or a Cloudflare challenge)?

**Currency / staleness flags:**

15. **TIPO's no-sui-generis-database statement is from 2001** (智著字第0900008111號). The statutory scan
    confirming no database article is current, but no post-2001 TIPO restatement was found.
16. **Readmoo's ToS is dated 2015/10/21** while its robots.txt was rewritten **2026-08** — the document
    and the operator's actual posture have visibly diverged.
17. **金石堂's terms are stamped 版本Kingstone.2021.11** and its newest sitemap is **January 2024**.
18. **誠品's terms show no date at all.**
19. **PDPA 第27條 is being deleted** by the 114-11-11 amendment (replaced by 第20-1條), 施行日期由行政院
    定之 — **not yet in force**. Re-check before the app launches.
20. **`athena.eslite.com/robots.txt`** returned 403 on one of two attempts and `Allow: /api/` on the
    other — treat that permission as unstable and re-verify.

**Could not be reached at all:**

21. TIPO's 「資料庫之保護」 study PDF — `topic.tipo.gov.tw` is DNS-unreachable; only the
    [landing page](https://www.tipo.gov.tw/tw/copyright/736-20706.html) is live.
22. The FTC 處分案件 database could not be keyword-searched (POST-only form), so **no 處分書 turning on a
    wrong or stale online price was established** — absence of evidence, not evidence of absence.
23. **買書網's data sourcing** — the closest direct competitor, covering four of the same six channels,
    publishes nothing about where its prices come from.
