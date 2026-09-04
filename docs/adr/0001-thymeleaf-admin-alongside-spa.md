# 前後端分離下仍保留 Thymeleaf，用途限定為 /admin 管理後台

本系統採前後端分離：面向使用者的五個路由全部由 Next.js 提供，Spring 不回傳任何面向使用者的頁面。
即便如此仍保留 Spring MVC + Thymeleaf，用途**限定於 `/admin` 的內部管理後台**
（通路管理、書籍維護、手動取價），路由前綴與 Next.js 完全不重疊。

## Considered Options

- **只做 Web API Controller**：最單純，但放棄了展示 MVC Controller 與 Web API Controller 的職責差異。
- **MVC Controller 只做 health check 之類的門面**：形式上兩種都有，但那是為了湊數而存在的空殼，
  沒有真實職責。
- **（採用）限定為管理後台**：前端本來就不做管理介面，這塊職責無人認領，交給 MVC 剛好，
  且與 Next.js 不會搶路由。

## Consequences

Repo 裡會同時出現 Thymeleaf 樣板與一個 Next.js 應用。**這是刻意的，不是尚未清除的殘骸**——
`src/main/resources/templates/` 底下的樣板只服務 `/admin/**`，不要因為「這是 SPA 專案」而刪除它們。
