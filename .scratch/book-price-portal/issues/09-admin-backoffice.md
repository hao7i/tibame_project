# 09: /admin 管理後台

**What to build:** 開發者以管理帳號登入 `/admin`，在伺服器端渲染的頁面上管理通路、維護書目資料、手動觸發一次取價。這是 MVC Controller 在本專案的唯一用途（見 ADR 0001）。

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `/admin` 底下所有頁面需通過 HTTP Basic 驗證才能存取
- [ ] 前台會員帳號無法登入 `/admin`
- [ ] 可瀏覽與管理通路清單
- [ ] 可瀏覽與維護作品、版本與報價資料
- [ ] 可手動觸發一次取價，並看到結果
- [ ] MVC Controller 不直接注入 Repository，一律經過 Service
- [ ] `/admin` 的路由與前端的路由沒有任何重疊
