# 書籍比價入口網站（Book Price Portal）

聚合**五家**台灣網路書店（五南文化廣場／三民網路書店／金石堂／讀冊生活／墊腳石）單一書籍售價的
繁體中文響應式比價網站。使用者可依書名／作者／出版社／ISBN 搜尋、逐通路比價，登入後可追蹤書籍、
設定目標價。書目裡沒有的書，可以請系統即時到通路找出來收錄。

> **通路名單已與原始規格不同。** 設計稿與本檔早期版本寫的是博客來／誠品線上／金石堂／讀冊生活／
> 樂天Kobo／Readmoo 六家，那六家**現在一家都不在站上**——依 robots.txt、使用條款與頁面渲染方式
> 逐一評估後替換掉了，理由見「通路資料來源」一節。不要依設計稿把舊名單改回來。

設計規格的來源是 `design/README.md`（桌機 1180px + 手機 390px、互動與狀態、design tokens）。
`design/Book Price Portal.dc.html` 是設計參考原型，**不是可複製的產品程式碼**；
`design/support.js` 是原型執行環境，**不得移植**。實作已在數處刻意偏離設計稿，見「刻意偏離設計稿之處」。

## 第一版範圍

24 小時內完成第一版，一人開發。**做**：設計稿的六個路由（桌機 + 手機兩套版型）、五通路比價、
會員註冊登入、追蹤清單與目標價存資料庫、`/admin` 管理後台、真實取價。

第一版的十一張票已全部完成（票單在 `.scratch/book-price-portal/issues/`，該目錄不在版控）。
之後的改動多為使用者直接指示，未必回頭更新票單，**以本檔與程式碼為準**。

**不做**（明確排除，不要自行加回來）：

- **寄送 email**：目標價達成只在畫面上顯示「已達目標價」狀態，不接任何 SMTP 或寄信服務。
  ⚠️ `frontend/app/watch/page.tsx` 目前有三處文案仍寫著「email 通知」，與這條相牴觸。
  那是還沒修掉的假承諾，**不是**可以據此去接寄信服務的依據。
- **作品的自動合併**：不去判斷哪些 ISBN 屬於同一作品，關係直接寫在 seed 資料裡。
- **768px 平板中斷點**：設計稿未定義，只做 1180px 桌機與 390px 手機兩套。
- **價格走勢**：設計稿在單書比價頁畫了「近 90 天價格走勢」sparkline 卡，本專案不做——
  不存價格歷史、不建 PricePoint 之類的資料表。設計稿與原型的 `trend` 陣列仍留著，
  但**不要據此把它加回來**。
- **載體（紙本／電子書）**：整個概念已從資料與介面移除。沒有電子書版本、沒有
  「全部版本／紙本書／電子書」切換器、API 沒有 `format` 參數。`Format` enum 與
  `Edition.format` 保留但恆為 `PAPER`——作品有多個版本仍是 ADR 0002 的模型基礎，
  日後要區分平裝／精裝時會用到。**不要照設計稿把載體切換器加回來。**

## 刻意偏離設計稿之處

`design/README.md` 仍是版面與 design tokens 的參考，但下列各項是**刻意不照它做**的決定。
兩者衝突時，**這一節優先於「以 README 為準」那條規則**。

| 設計稿寫的 | 實際做法 | 為什麼 |
|---|---|---|
| 四個 11×11 註冊記號 | 全站不使用，`BlueprintCorners` 已刪除 | 依使用者要求；`.blueprint` class 本身仍用於書封框線 |
| 真實書封套 `.duotone` 鋼藍濾鏡 | 只有**佔位框**套，真實封面顯示原色 | `mix-blend-mode: color` 會把每本書的封面染成同一個藍 |
| footer 有 收錄通路／意見回饋／資料來源說明 三個連結 | footer 只剩標記文字 | 依使用者要求；三個對應路由也已刪除 |
| 導覽列有「通路一覽」 | 已移除 | 同上 |
| 首頁有「運作方式」卡片 | 已移除 | 同上 |
| 首頁「收錄通路」為六等分格線 | 五等分 | 通路剩五家 |
| 單書比價頁有「價格低→高／依通路」排序 | 已移除（後端 `sort` 參數保留） | 依使用者要求 |
| 搜尋結果有「分類」facet | 已移除（後端 `category` 參數保留） | 依使用者要求 |
| 進階搜尋有 AND／OR／NOT | 只剩 AND／OR（後端 NOT 保留） | 依使用者要求 |
| 搜尋框提示「書名、作者、出版社或 ISBN」 | 只寫「書名」 | 逐欄位查詢由進階搜尋負責；API 仍四欄都比對 |
| 每個路由都有的書封為佔位框 | 有真實封面時顯示封面，沒有才用佔位框 | 見「書封」一節 |

移除畫面入口時的一致作法：**拿掉 UI，不縮減 API 既有能力**。舊連結因此不會壞，
相關後端測試也仍然有效。

## 專案結構

前後端分離，兩個獨立的可部署單元，同一個 repo：

```
/
├── backend/     Spring Boot（Java 21 + Maven Wrapper）
│   └── src/main/java/tw/bookprice/
│       ├── catalogue/   作品 / 版本 / 報價 / 通路，搜尋與最低價
│       ├── pricing/     取價 adapter、平行取價、排程
│       ├── discovery/   依書名或 ISBN 到通路找書並收錄
│       ├── member/      會員身分（與 /admin 完全分離）
│       ├── watchlist/   追蹤清單與目標價
│       ├── admin/       Thymeleaf 管理後台
│       ├── api/         對前端的 JSON API
│       ├── config/      Security、CORS
│       └── seed/        書目 seed 與既有資料庫的遷移
├── frontend/    Next.js 16（App Router + TypeScript）
│   ├── app/     六個路由：/  /search  /works/[isbn]  /watch  /advanced  /login
│   ├── components/
│   └── lib/     後端 API 用戶端、session、設計 token
├── design/      設計稿與設計系統（唯讀參考，不要改；不在版控）
└── docs/        ADR、agent 說明、通路調查（**不在版控**，只在開發者本機）
```

開發時後端跑 `http://localhost:8080`，前端跑 `http://localhost:3000`。

**`design/` 與 `docs/` 都不在版本庫裡**，但本檔仍然引用它們——這是刻意的：指引留在版控，
內容留在本機。clone 這個 repo 的人會看到指向不存在檔案的引用，那不是錯誤。

## 後端規格（Spring Boot）

- **Java 21**（本機已安裝 21.0.12 LTS），**Spring Boot 4.1.x**。
- **建置用 Maven Wrapper `./mvnw`**。本機沒有安裝 Maven 或 Gradle，不要假設 `mvn` 指令存在，
  也不要為了建置去安裝它。專案以 Spring Initializr 產生，wrapper 隨附。
- 基礎套件：`tw.bookprice`。

### 分層架構

四層，**依賴方向單向，不得反向或跨層**：

```
MVC Controller  ─┐
                 ├─→  Service  ─→  Repository  ─→  DB
Web API Controller ─┘
```

| 層 | 標註 | 職責 | 禁止事項 |
|---|---|---|---|
| **Repository** | `@Repository` / Spring Data JPA 介面 | 只負責持久化；回傳 Entity | 不得含商業邏輯；不得被 Controller 直接呼叫 |
| **Service** | `@Service` | 商業邏輯的唯一所在；交易邊界 `@Transactional`；Entity ↔ DTO 轉換 | 不得依賴任何 web 層型別（`HttpServletRequest`、`ResponseEntity` 等） |
| **Web API Controller** | `@RestController` | 對 Next.js 前端的 JSON API | 不得含商業邏輯；不得直接注入 Repository |
| **MVC Controller** | `@Controller` | 回傳 Thymeleaf view 的**內部管理後台**（見下） | 同上 |

- **Controller 一律不得直接注入 Repository**，必須經過 Service。
- **Entity 不得離開 Service 層**：Controller 只收送 DTO。
- 相依注入一律用 **constructor injection**，不用 `@Autowired` 欄位注入。

### Web API 規格

- 路徑前綴一律 `/api`，資源用複數名詞。實際端點：`/api/works`、`/api/works/{isbn}`、
  `/api/channels`、`/api/facets`、`/api/lookups`、`/api/me/watchlist`。
  （資源名用 `works` 而非 `books`：`CONTEXT.md` 的領域語言以「作品(Work)」為搜尋與追蹤的對象，
  並明列避免使用「書／Book」。）
- 請求／回應主體一律為 DTO（`record`），不得直接暴露 Entity。
- 輸入驗證用 `@Valid` + Bean Validation 標註。
- 例外集中處理於 `@RestControllerAdvice`，回傳一致的錯誤主體
  （`{ "error": { "code", "message" } }`），不要在 Controller 裡寫 try/catch 回傳錯誤。
- HTTP 狀態碼要用對：200 / 201 / 400 / 404 / 409 / 500。
- CORS 只在設定類別（`WebMvcConfigurer`）集中開放給 `http://localhost:3000`，
  不要在各 Controller 上散落 `@CrossOrigin`。

### 兩種 Controller 的職責切分

系統是前後端分離的，面向使用者的六個路由**全部由 Next.js 出**，Spring 不回傳任何面向使用者的頁面。
兩種 Controller 因此有各自不重疊的用途：

- **Web API Controller**（`@RestController`，`/api/**`）：Next.js 前端的資料來源。
- **MVC Controller**（`@Controller`，`/admin/**`）：**Thymeleaf 內部管理後台**，前端不做這一塊——
  通路管理、書籍資料維護、手動觸發取價。路由前綴 `/admin`，與 Next.js 的路由完全不重疊。

### 資料庫

**Microsoft SQL Server 2025**（本機已安裝並執行，預設執行個體 `MSSQLSERVER`，Enterprise Developer Edition）。

- 存取一律經 **Spring Data JPA**，不寫原生 SQL（複雜查詢用 `@Query` JPQL；真的需要原生時才 `nativeQuery`）。
- JDBC 驅動 `com.microsoft.sqlserver:mssql-jdbc`。
- 資料庫名稱 `bookprice`，以**專用 SQL 登入帳號**連線（不使用 Windows 整合驗證——JDBC 走整合驗證需要原生 DLL，
  在本專案不值得）。連線設定放 `application.yml`，本機開發用
  `encrypt=false;trustServerCertificate=true`。
- **本機環境前提**：SQL Server 的 TCP/IP 通訊協定預設為關閉，必須啟用並重啟服務，1433 才會有 listener，
  JDBC 才連得上。這一步需要系統管理員權限。
- schema 由 JPA 產生（`ddl-auto: update`），seed 資料以 `CommandLineRunner` 或 `data.sql` 匯入。

### 安全性

**兩套完全獨立的身分系統，不得共用使用者表：**

- **後台 `/admin/**`**：Spring Security **HTTP Basic**，單一組管理帳密寫在 `application.yml`。
  這是給開發者／維運者用的。
- **前台會員**（追蹤清單、目標價）：一般使用者的帳號，走 Next.js + `/api/**`。
  驗證用 **session cookie**（Spring Security form login，`JSESSIONID`，`SameSite=Lax`），**不用 JWT**——
  同源前後端分離下 JWT 沒有好處，只多出 token 保存位置的安全問題。密碼以 BCrypt 雜湊儲存。

### 通路資料來源

通路一律藏在**同一個 adapter 介面**（`ChannelPriceProvider`）後面，每家一個實作，
`bookprice.pricing.<通路>.live` 可逐家切換真實抓取或示意實作，關掉任一家網站仍然可以展示。

**目前五家全部為真實取價**（五南、三民、金石堂、讀冊生活、墊腳石）。

### 為什麼是這五家

原始的六家在逐一評估後全部被換掉。判準只有三個，**缺一不可**：

1. robots.txt 允許以 ISBN 找到商品頁
2. 價格在伺服器端渲染（HTML 裡就有，不需要跑 JavaScript）
3. **那一頁能把價格綁回 ISBN**

第三項最常出局，也最危險：拿得到價格、卻無法確認它屬於哪一本書，就會安靜地把別本書的
價格填進來。金石堂就踩過——紙本與電子書共用同一個 `gtin13`。

被排除的（連同理由，避免有人重問一遍）：

| 通路 | 為什麼不用 |
|---|---|
| 博客來 | robots.txt 對 ClaudeBot 全站 `Disallow: /` |
| Readmoo | 同上 |
| 誠品線上 | `/search` 對所有 user agent 禁止，且商品頁是用戶端渲染的 SPA 空殼 |
| 樂天Kobo | robots.txt 允許，但**使用條款明文禁止**機器人、蜘蛛、資料庫納入 |
| Yahoo 購物 | 無 JSON-LD、無內嵌價格，價格來自 robots 禁止的 `/graphql` |
| PChome | 帶 ISBN 的商品頁被 robots 禁止；允許抓的搜尋頁沒有 ISBN |
| 城邦讀書花園 | 商品頁很好，但唯一的 ISBN 入口 `/search_result` 被 robots 禁止 |
| 紀伊國屋 taiwan | 資料最乾淨（`/bw/{ISBN}` 即商品頁），但 `Crawl-delay: 600` 使一輪取價要一小時 |
| 紀伊國屋 .com.tw | robots 全開且有 `agents.md`，但商品目錄是空的 |

**這些調查的完整記錄在 `docs/research/book-price-channel-data-sources.md`，
而 `docs/` 已移出版控、只存在於開發者本機**——上表是留在版控裡的摘要。

### 抓取實作規則

- 誠實的 User-Agent，同一站台請求最小間隔 1.5 秒（`min-interval-ms`，分主機計算）。
- **單一通路失敗不得影響其他通路**：該筆標記 `fetchFailedAt` 並保留上次價格，其餘照常。
- 通路明確表示沒有這本書時標記 `unavailable`，**不可沿用舊值**——通路替換後留著舊值，
  等於把前一家的售價掛在新一家名下。
- 結果快取 6 小時（`freshness-hours`），窗內不再詢問對方網站。
- **只取價格、供貨等事實性欄位與書封**，不抓書介文字。智慧財產局函釋認為表格化價格資料
  不受著作權保護，但一併抓取的著作會觸及重製與公開傳輸；書封是使用者明確授權的例外，
  **不要把那個例外自行擴大**。
- 每筆真實報價都連回該通路的商品頁，畫面標示來源通路。

### 取價的觸發

- **`PriceRefreshSchedule`**（`@Scheduled`）負責過期報價，履行設計稿的「價格每 6 小時更新一次」。
  用 `fixedDelay` 而非 `fixedRate`，並自行吞例外——Spring 會因為排程方法拋出的例外而
  取消後續排程。測試環境以 `schedule.enabled: false` 關閉。
- **找書**（`BookLookupService`）只呼叫 `refreshIsbns`，**只為剛收錄的書取價**。
  不要改回 `refreshAll`：那會讓一次找書付整個書目的成本，並隨書目成長而變慢。
- **`/admin` 的手動觸發**是唯一會 `force` 的入口。

取價的網路階段是平行的。安全的作法是把「決定要抓什麼／抓／寫回」拆成三段——只有中間那段
走網路，前後都碰 JPA 實體、**必須留在同一個交易執行緒上**。直接對整個迴圈用 `parallelStream`
會把 persistence context 交給多個執行緒，那不是它能承受的。

### 找書（依書名或 ISBN 即時收錄）

搜尋只查本站書目。書目裡沒有的書，由 `discovery` 套件即時到通路找出來、收錄、再取價。

- 依**書名**找只開金石堂（搜尋結果為伺服器端渲染且商品頁有真 ISBN 欄位）。
- 依 **ISBN** 找不需要這一步，ISBN 本身就是驗證錨點。
- **兩道防線不得拿掉**：`TitleRelevance` 擋掉搜尋結果頁上的促銷輪播（它曾把減脂餐寫進書目），
  `Isbn13` 驗 Bookland 前綴與檢查碼（金石堂的商品編號也是 13 位數，只驗長度會蒙混過關）。
- 商品頁宣告的 ISBN 必須與所求相符，否則整筆丟棄。**寧可少一筆，不可填錯一筆。**

## 前端規格（Next.js）

- **Next.js 16.3.4**（已對 npm registry 查證為目前 `latest`），React + **TypeScript**，App Router。
- Node v22.14.0、npm 10.9.2（本機已安裝）。

### 不允許 CDN

**所有相依都必須經 npm 安裝並打包進產物，由自己的 origin 送出。** 具體規則：

- **字型**：用 `next/font/google` 載入 Barlow 與 Barlow Condensed——它在 build 時把字型檔下載下來自我託管，
  符合本規則。**不得**用 `<link>` 或 CSS `@import` 連 `fonts.googleapis.com`。
  移植 `design/_ds/industry-*/styles.css` 時，**必須移除該檔開頭的 Google Fonts `@import`**。
- **圖示**：用 `lucide-react` npm 套件，stroke-width 1.5。不得從 lucide.dev 或任何 CDN 載入。
- 任何函式庫都不得以 `<script src="https://...">` 或 `<link href="https://...">` 引入。

**唯一的例外是書封圖片**，它直連通路的 CDN——見下方「設計實作」。那是刻意的：把別人的圖
抓一份存在自己伺服器上，性質與 CDN 無關，是重製。

### Ant Design

`antd`（v6）＋ `@ant-design/nextjs-registry` 只用來出**通知**（註冊／登入／登出／找書結果），
不用來出版面。版面是從設計稿移植的自有 CSS。

- **不要引入 antd 的 reset CSS**，它會覆蓋掉移植過來的設計系統。
- registry 必須留著：antd 的樣式是 CSS-in-JS，App Router 下要在伺服器端收集並先於 markup 注入。
- notification 的參數用 `title`，不是已棄用的 `message`。

### 設計實作

- 版面、文案、互動、狀態一律以 `design/README.md` 為準，**高保真（hifi）實作**。
- 兩份設計參考的分工：**`design/README.md` 是規格**（尺寸、間距、色彩、狀態邏輯的文字定義，優先採信）；
  **`design/Book Price Portal.dc.html` 是實例**——當 README 描述不夠精確時，回去查這支原型的實際
  markup、class 用法與 `renderVals()` 的行為。兩者衝突時以 README 為準。
- 原型的示意資料（`BOOKS` / `SHOPS` / `SHOP_META` / `FACETS` / `SHOP_TINT`，約在 742–830 行）
  **曾經**是 seed 資料的來源。現在只剩書目那六本的書名、作者、分類、定價還照抄；
  通路名單、售價與 ISBN 都已被真實取價與更正覆蓋。
  ⚠️ 原型的 ISBN **不可信**：`人類大歷史` 的檢查碼是錯的，`正義：一場思辨之旅` 的號碼
  指向另一本書。兩者都已更正（見 `CatalogueSeeder` 的 `IsbnCorrection`）。
  **重算檢查碼不是修法**——那樣得到的是一個合法但屬於別本書的號碼，比原本更糟，
  因為每家通路都查得到它，於是把別人的價格與封面填進來且看起來完全成功。
- design tokens（`design/_ds/industry-*/styles.css` 的 `:root` 變數）**移植成專案自己的 CSS 變數**，
  元件中一律引用變數，不得硬寫 hex 值。
- **書封直連通路 CDN，不自己快取**：取價時順便從商品頁讀 `image`（JSON-LD）或 `og:image`，
  存成 `Edition.coverImageUrl`，前端用 `<img>` 直接連對方的 CDN。**不要改用 `next/image`**——
  它的 optimizer 會把別人的圖抓一份快取在我們自己的伺服器上，那正是這裡刻意不做的事。
  沒有封面時（尚未取價、或該頁沒有圖）退回 `.blueprint.duotone` 佔位框；圖片 404 時
  `BookCover` 會在瀏覽器端退回同一個佔位框。
- **四個 11×11 註冊記號（角落的 `+`）全站不使用**——`BlueprintCorners` 元件已刪除，不要重建。
  `.blueprint` 這個 class 本身仍在用：書封佔位框的框線只由它提供。按鈕與卡片則連 class 一起拿掉，
  因為 `.btn` 與 `.card` 本來就已提供 1px 直角邊框，外觀不變。
- **其餘刻意偏離設計稿之處見上方那一節**，該節優先於這裡的「以 README 為準」。
- 互動狀態（hover / pressed / `:focus-visible` 2px accent 外框）不得留瀏覽器預設值。
- 呼叫後端一律走 `/api` 的 Web API Controller，不得在前端直接連通路網站。

## 本機環境的已知陷阱

這些都是在這個專案上實際踩過的，不是通用建議：

- **`./mvnw compile` 單獨跑不可信。** DTO 加欄位後，沒改的構造點仍會編譯成功。
  要確認就跑 `./mvnw test`（全量），或先清掉 `target/`。
- **NOT NULL 欄位不能加進已有資料的資料表。** `ddl-auto: update` 會靜靜地不建那個欄位，
  之後每個查詢都倒在「無效的資料行名稱」上。新欄位一律 nullable。這個坑踩過三次。
- **`spring-boot-devtools` 會監看 `target/classes`。** 跑 `./mvnw compile` 或 `test` 會觸發
  正在執行的後端自動重啟，失敗時它就靜靜地掉線。
- **`spring-boot:run -Dspring-boot.run.profiles=test` 不會載入 `src/test/resources`。**
  它連的是正式的 SQL Server。要隔離請用 `./mvnw test`（surefire 才有 test classpath）。
- **中文字**：Hibernate 預設把 String 映射成 varchar，SQL Server 會把中文寫成 `?`。
  `use_nationalized_character_data: true` 已設定，但**它救不了已經建好的資料表**——
  那種情況要把 `offer`／`edition`／`work`／`channel` 四張表 drop 掉重建。
- **JPA 的 `orphanRemoval = true`**：從集合移除即排定刪除，之後加進另一個集合**不會撤銷**。
  搬移報價時要用新增，不要用移動。
