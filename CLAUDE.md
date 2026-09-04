# tibame_project — 書籍比價入口網站（Book Price Portal）

聚合六家台灣網路書店（博客來／誠品線上／金石堂／讀冊生活／樂天Kobo／Readmoo）單一書籍售價的
繁體中文響應式比價網站。使用者可依書名／作者／出版社／ISBN 搜尋、逐通路比價，登入後可追蹤書籍、
設定目標價並接收降價通知。

設計規格的唯一來源是 `design/README.md`（五個路由、桌機 1180px + 手機 390px、互動與狀態、
design tokens 全在裡面）。`design/Book Price Portal.dc.html` 是設計參考原型，**不是可複製的產品程式碼**；
`design/support.js` 是原型執行環境，**不得移植**。

## 第一版範圍

24 小時內完成第一版，一人開發。**做**：設計稿的五個路由（桌機 + 手機兩套版型）、六通路比價、
會員註冊登入、追蹤清單與目標價存資料庫、`/admin` 管理後台、真實抓取六家中的 1–2 家。

**不做**（明確排除，不要自行加回來）：

- **寄送 email**：目標價達成只在畫面上顯示「已達目標價」狀態，不接任何 SMTP 或寄信服務。
- **作品的自動合併**：不去判斷哪些 ISBN 屬於同一作品，關係直接寫在 seed 資料裡。
- **768px 平板中斷點**：設計稿未定義，只做 1180px 桌機與 390px 手機兩套。
- **真實書封圖片**：一律用設計稿的 `.blueprint.duotone` 佔位框。
- **價格走勢**：設計稿在單書比價頁畫了「近 90 天價格走勢」sparkline 卡，本專案不做——
  不存價格歷史、不建 PricePoint 之類的資料表、右側只有兩張卡。設計稿與原型的 `trend`
  陣列仍留著，但**不要據此把它加回來**。

## 專案結構

前後端分離，兩個獨立的可部署單元，同一個 repo：

```
/
├── backend/          Spring Boot（Java 21 + Maven Wrapper）
├── frontend/         Next.js 16.3.4
└── design/           設計稿與設計系統（唯讀參考，不要改）
```

開發時後端跑 `http://localhost:8080`，前端跑 `http://localhost:3000`。

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

- 路徑前綴一律 `/api`，資源用複數名詞（`/api/books`、`/api/books/{isbn}/offers`）。
- 請求／回應主體一律為 DTO（`record`），不得直接暴露 Entity。
- 輸入驗證用 `@Valid` + Bean Validation 標註。
- 例外集中處理於 `@RestControllerAdvice`，回傳一致的錯誤主體
  （`{ "error": { "code", "message" } }`），不要在 Controller 裡寫 try/catch 回傳錯誤。
- HTTP 狀態碼要用對：200 / 201 / 400 / 404 / 409 / 500。
- CORS 只在設定類別（`WebMvcConfigurer`）集中開放給 `http://localhost:3000`，
  不要在各 Controller 上散落 `@CrossOrigin`。

### 兩種 Controller 的職責切分

系統是前後端分離的，面向使用者的五個路由**全部由 Next.js 出**，Spring 不回傳任何面向使用者的頁面。
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

六家通路一律藏在**同一個 adapter 介面**（`ChannelPriceProvider`）後面，每家一個實作。
抓價方式（示意資料／官方 API／爬蟲）因此可以**逐家替換**，不影響 Service 以上的任何程式碼。

**第一版目標**：六家中**真實抓取 1–2 家**，其餘維持示意實作。

**實作順序（不得顛倒）**：

1. 六家全部先以示意實作（seed 資料）完成，讓整個系統端到端跑得起來、五個路由都能 demo。
2. 再把其中 1–2 家換成真實抓取。

這個順序的用意是：真實抓取可能卡在反爬、編碼或頁面改版上，時間無法預估；先完成第 1 步，
就算第 2 步失敗，手上仍是一個完整可 demo 的系統。

**挑哪 1–2 家**依 `docs/research/book-price-channel-data-sources.md` 的調查結果決定，
優先選「價格為伺服器端渲染」且「robots.txt 與使用條款未禁止自動存取」的通路。

抓取實作規則：設定合理的 User-Agent 與請求間隔、單一通路失敗不得影響其他通路的結果
（per-channel failure tolerated）、結果快取約 6 小時（設計稿的「價格每 6 小時更新一次」）。

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

### 設計實作

- 版面、文案、互動、狀態一律以 `design/README.md` 為準，**高保真（hifi）實作**。
- 兩份設計參考的分工：**`design/README.md` 是規格**（尺寸、間距、色彩、狀態邏輯的文字定義，優先採信）；
  **`design/Book Price Portal.dc.html` 是實例**——當 README 描述不夠精確時，回去查這支原型的實際
  markup、class 用法與 `renderVals()` 的行為。兩者衝突時以 README 為準。
- 原型的示意資料（`BOOKS` / `SHOPS` / `SHOP_META` / `FACETS` / `SHOP_TINT`，約在 742–830 行）
  是後端 seed 資料的來源，照抄數值即可。
- design tokens（`design/_ds/industry-*/styles.css` 的 `:root` 變數）**移植成專案自己的 CSS 變數**，
  元件中一律引用變數，不得硬寫 hex 值。
- `.blueprint` 框的四個 11×11 註冊記號不得省略。
- 互動狀態（hover / pressed / `:focus-visible` 2px accent 外框）不得留瀏覽器預設值。
- 呼叫後端一律走 `/api` 的 Web API Controller，不得在前端直接連通路網站。

## Agent skills

### Issue tracker

Issues live as GitHub issues in `hao7i/tibame_project`, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
