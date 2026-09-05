# 書價 BOOKPRICE.TW — 書籍比價入口網站

聚合五家台灣網路書店對同一本書開出的售價，讓使用者比較後前往購買，登入後還能追蹤書籍、
設定目標價。繁體中文、響應式（桌機 1180px 與手機 390px 兩套版型）。

前後端分離，同一個 repo 兩個獨立的可部署單元：Spring Boot 後端跑在 `8080`，
Next.js 前端跑在 `3000`。

---

## 功能

### 搜尋與比價

- **搜尋**：以書名、作者、出版社或 ISBN 查詢，結果可依通路與價格上限篩選，已選條件以
  可點擊移除的標籤呈現。
- **三種呈現方式**：列表、卡片、表格。表格為每個通路一欄，方便直向比較；選擇存在網址裡，
  分享出去的連結會開在同一個檢視。
- **單書比價**：一本書在各通路的完整報價——通路、庫存到貨、折扣、售價，最低價那一列以
  底色與標籤標示，可直接點擊前往該通路的商品頁購買。
- **進階搜尋**：兩列欄位條件（書名／作者／出版社／譯者／ISBN／系列）以 AND 或 OR 串接，
  另可設定價格區間、出版年與限定通路。
- **找書**：搜尋不到時，可請系統即時到通路找這本書，找到就收錄進書目並取回各通路價格。

### 會員

- 註冊、登入、登出，密碼以 BCrypt 雜湊儲存，驗證走 session cookie。
- **追蹤清單**：追蹤的對象是「作品」而非某一個 ISBN。可為每本書設定目標價，清單會顯示
  目前最低價與距離目標價還差多少；達成時該列會標示「已達目標價」。
  *（本版不寄送 email，只在畫面上顯示狀態。）*

### 取價

五家通路全部為**真實取價**——金石堂、三民網路書店、五南文化廣場、墊腳石、讀冊生活。
價格取自各通路的商品頁，每筆報價都連回來源頁面。

- 只取售價與供貨狀態等事實性欄位。
- 結果快取 6 小時，窗內不會重複詢問對方網站；同一站台兩次請求最少間隔 1.5 秒。
- **單一通路失敗不影響其他家**：該通路顯示「取價失敗」並保留上次取得的價格，其餘照常。

### 管理後台

`/admin`（Thymeleaf，HTTP Basic 驗證，與前台會員是兩套完全獨立的身分系統）：
通路管理、書目與報價維護、移除作品、手動觸發一次取價。

---

## 環境需求

| 項目 | 版本 | 備註 |
|---|---|---|
| JDK | 21 | 本專案以 21.0.12 LTS 開發 |
| Node.js | 22 | 搭配 npm 10 |
| Microsoft SQL Server | 2022 / 2025 | 需要一個名為 `bookprice` 的資料庫 |

**不需要安裝 Maven**——後端用專案自帶的 Maven Wrapper (`mvnw`)。

---

## 啟動步驟

### 一、準備資料庫（只需做一次）

**1. 確認 SQL Server 的 TCP/IP 通訊協定已啟用。**

這是最常見的卡關點：SQL Server 預設**關閉** TCP/IP，1433 埠不會有 listener，JDBC 連不上。

開啟「SQL Server 組態管理員」→ *SQL Server 網路組態* → *MSSQLSERVER 的通訊協定* →
把 **TCP/IP** 設為「已啟用」→ 重新啟動 SQL Server 服務。這一步需要系統管理員權限。

確認 1433 已在聆聽：

```powershell
Test-NetConnection -ComputerName localhost -Port 1433
```

**2. 建立資料庫與專用登入帳號。**

以 `sa` 或其他系統管理員身分執行：

```sql
CREATE DATABASE bookprice;
GO

CREATE LOGIN bookprice_app WITH PASSWORD = 'Bookprice!2026dev';
GO

USE bookprice;
CREATE USER bookprice_app FOR LOGIN bookprice_app;
ALTER ROLE db_owner ADD MEMBER bookprice_app;
GO
```

> 資料表由 JPA 自動建立（`ddl-auto: update`），不需要手動建 schema。
> 第一次啟動後端時，六本示範書籍會自動匯入。

**3. 驗證帳號連得上：**

```powershell
sqlcmd -S localhost -U bookprice_app -P 'Bookprice!2026dev' -d bookprice -C -Q "SELECT DB_NAME()"
```

### 二、啟動後端

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell 用 `.\mvnw.cmd spring-boot:run`。

第一次執行會下載相依套件，需要幾分鐘。看到 `Started BackendApplication` 就是好了。

**確認後端正常：**

```bash
curl http://localhost:8080/api/works
```

應該回傳 JSON，`total` 為書目中的作品數。

**若要換連線設定**，不必改程式碼，用環境變數覆蓋即可：

```bash
DB_URL="jdbc:sqlserver://localhost:1433;databaseName=bookprice;encrypt=false;trustServerCertificate=true"
DB_USERNAME=bookprice_app
DB_PASSWORD=Bookprice!2026dev
ADMIN_USERNAME=admin        # /admin 後台帳號
ADMIN_PASSWORD=admin1234    # /admin 後台密碼
```

### 三、啟動前端

**另開一個終端機**（後端要一直跑著）：

```bash
cd frontend
npm install          # 第一次才需要
npm run dev
```

打開 <http://localhost:3000>。

前端預設連 `http://localhost:8080`；後端若在別的位址，設定 `BACKEND_URL` 環境變數。

### 四、確認整套跑起來了

1. 開 <http://localhost:3000>，首頁應該看到五個通路的橫幅。
2. 搜尋「原子習慣」，應該出現比價結果。
3. 點書名進入單書比價頁，看得到各通路報價與「前往購買」。
4. 開 <http://localhost:8080/admin>，用 `admin` / `admin1234` 登入。

---

## 常用指令

```bash
# 後端測試
cd backend && ./mvnw test

# 前端型別檢查、Lint、正式建置
cd frontend && npx tsc --noEmit && npm run lint && npm run build

# 前端正式模式（先 build 再 start；不會熱更新）
cd frontend && npm run start
```

---

## 疑難排解

| 症狀 | 原因與處理 |
|---|---|
| 後端起不來，`Connection refused` / `TCP/IP connection failed` | SQL Server 的 TCP/IP 沒啟用，或服務沒重啟。見「準備資料庫」第 1 步。 |
| 後端起不來，`Port 8080 was already in use` | 已經有一個後端在跑。先關掉舊的行程。 |
| 網頁顯示「目前拿不到比價資料」 | 後端沒在跑，或不在 `8080`。先確認 `curl http://localhost:8080/api/works`。 |
| 書名顯示成 `?????` | 資料表是在啟用 nvarchar 之前建立的。把 `offer`、`edition`、`work`、`channel` 四張表 drop 掉，重啟後端會重建並重新匯入。 |
| 搜尋不到某本書 | 搜尋查的是本站書目，不是即時查五家通路。用搜尋結果頁的「到各通路找找看」把它收錄進來。 |
| `npm run dev` 說埠被占用 | 已經有前端在跑。關掉舊的，或讓它改用其他埠。 |

---

## 專案結構

```
/
├── backend/     Spring Boot（Java 21 + Maven Wrapper）
│   └── src/main/java/tw/bookprice/
│       ├── catalogue/   作品 / 版本 / 報價 / 通路，以及搜尋與最低價
│       ├── pricing/     各通路取價的 adapter 與排程
│       ├── discovery/   依書名或 ISBN 到通路找書並收錄
│       ├── member/      會員身分
│       ├── watchlist/   追蹤清單與目標價
│       ├── admin/       Thymeleaf 管理後台
│       └── api/         對前端的 JSON API
└── frontend/    Next.js 16（App Router + TypeScript）
    ├── app/     六個路由：首頁、搜尋結果、單書比價、追蹤清單、進階搜尋、登入
    ├── components/
    └── lib/     後端 API 用戶端、session、設計 token
```

開發規範與設計決定見 `CLAUDE.md`。
