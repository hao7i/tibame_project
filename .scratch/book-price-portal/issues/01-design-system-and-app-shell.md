# 01: 設計系統移植與全域外框

**What to build:** 打開網站時，看到的字體、顏色、間距、外框與導覽列已經是設計稿的樣子，桌機與手機兩種寬度都正確。這張票不做任何業務畫面，只把後面每一張 UI 票都要用到的基礎鋪好。

**Blocked by:** None (can start immediately)

**Status:** done (7ac010b, b185444)

- [x] Industry 設計系統的 token 以 CSS 變數形式存在於前端，元件中不出現硬寫的 hex 值
- [x] Barlow 與 Barlow Condensed 由 Next.js 的字型機制自我託管，頁面不含任何指向 Google Fonts 的外部連結
- [x] 圖示改用 npm 安裝的 Lucide 套件，原型的文字符號（≡ ‹ › ＋ ☆ ★ ✕）全部替換，stroke-width 1.5
- [x] 頁面沒有任何從 CDN 載入的樣式表或腳本
- [x] 桌機 header（品牌、導覽連結、登入按鈕）與 footer、手機 header（漢堡、品牌、追蹤／登入）在 1180px 與 390px 下與設計稿一致
- [x] 按鈕、輸入框、tag、分段控制項等基礎元件的樣式可重複使用，方角（radius 0）
- [x] `.blueprint` 外框的四個角落註冊記號存在
- [x] hover、pressed、focus-visible 三種互動狀態皆已定義，沒有留下瀏覽器預設外框
