# Stock Analysis Backend — Public Portfolio Mirror

這是台股分析網站後端的去營運化 Public mirror，展示 Spring Boot 分層架構、估值計算、資料快取、外部資料整合與測試設計。Private repository 才是唯一開發與營運來源；此 repository 不接受自動反向同步，也不含 Private Git history、production deployment、runtime state 或真實憑證。

## 功能與架構

- Java 21、Spring Boot 4.1、Spring Data JPA、Flyway、PostgreSQL。
- 依歷史 PER／PBR、EPS／BPS 與殖利率資料計算多檔目標價。
- 企業體質檢視、股票搜尋、收藏與按鈕觸發的 AI 建議。
- Controller／Application Service／Domain Calculation／Repository 分層。
- 對外部 API 採快取、額度保護、有限 fallback 與明確錯誤回傳。

詳細欄位、公式與資料表設計位於 `docs/spec/`。

## 本機執行

需求：Java 21、Maven、PostgreSQL。複製 `.env.example` 的變數到自己的安全環境設定後執行：

```powershell
mvn spring-boot:run
```

測試：

```powershell
mvn test
```

## 安全與部署限制

- 這份 mirror 僅供程式碼審閱與學習，未提供 production deployment workflow。
- 目前的 simple auth 是單一使用者原型：密碼與 session token 的設計不符合 production 安全要求，不應直接部署或暴露於公網。
- `.env.example` 僅有假值；所有資料庫密碼、API key 與 token 都必須由安全的 secret storage 注入。
- Public mirror 不包含曾用於特定 hosting 網路路由的代理程式或 production URL。

## 第三方資料責任

本專案可連接 FinMind、TWSE／公開資訊觀測站與金融監督管理委員會相關公開資料，以及 AI provider。使用者在啟用任何來源前，必須自行確認目前的 Terms of Service、API 或資料 License、robots.txt、rate limit、attribution、隱私、AI processing 與再散布限制。公開可存取或技術上能連線，不代表已取得合法授權；若授權不明，應維持停用。

## No License

目前未授予本 repository 內容的使用、修改或散布授權。Public visibility 不等於 Open Source，也不授予商業或非商業重用權。正式 License 仍由 repository owner 決定。
