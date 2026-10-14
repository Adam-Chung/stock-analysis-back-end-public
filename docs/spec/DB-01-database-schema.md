# DB-01：資料庫設計（股票目標價畫面 + 未來擴充）

## 1. 用途

記錄股票分析網頁的資料庫結構，涵蓋 [SA-01 股票目標價畫面](./SA-01-stock-target-price.md) 的資料需求，
並預留給未來其他畫面／其他 FinMind 資料來源共用。Decision Log 編號延續 SA-01/SA-02（目前接續 DEC-17）。

## 2. 設計原則

**分層架構**：
- **共用原始資料快取層**（跨頁面、跨功能共用，直接對應 FinMind 各 dataset）：
  `stock`、`stock_price_yearly`、`stock_valuation_yearly`、`stock_financial_statement`、
  `stock_dividend`、`finmind_sync_log`
- **功能專屬設定層**（每個畫面自己的設定資料，目前只有目標價畫面）：`stock_target_price_setting`
- **AI 建議快取**（按鈕觸發、結果需要快取避免重複打 Gemini API）：`stock_ai_suggestion`

未來加新畫面（例如個股體質、財報比較），只要重用同一批共用資料表，不用重新設計/重新抓資料；
要加新的 FinMind 資料集，就新增一張對應的快取表 + 在 `finmind_sync_log` 多一種 dataset 值即可，
不影響既有表。

**計算結果不落地存表**：目標價、1~6檔價位、與目標價差比、X年內買回、賣賺幾年等，每次請求時從
共用原始資料層即時運算，不另外存欄位（DEC-17）。原因：算式本身很輕量，改設定就要重算，存了
反而要處理失效/重算時機的問題，不存更簡單也不會有資料不一致風險。

**股價／估值資料只存「年度」粒度，不存逐日**（DEC-24）：TaiwanStockPrice、TaiwanStockPER 這兩個
逐日資料集，抓回來的當下就在應用程式端聚合成「年度」統計（年度均價/最高/最低、年度均PER/最低PER/
最高PER/均PBR/最低PBR/最高PBR/均殖利率），只把年度聚合結果寫進 DB，逐日原始資料算完就丟掉不存。
10年資料量從每股 2,500+ 列（逐日）降到 10 列（逐年），也完全符合 SA-01 本來就「10年統計不含
當年度」的規則。財報（FinancialStatements）與股利（Dividend）資料量本來就小（一年沒幾筆），
維持原本的細粒度儲存，不受此決策影響。

> ⚠️ **取捨**：未來若要做「股價走勢圖」這類需要逐日 K 線資料的功能，屆時需另外向 FinMind
> 重新抓一次逐日資料——FinMind 本身仍是資料的 source of truth，我們只是快取，重抓不會遺失資料，
> 只是會再花一次額度。目前這支應用完全用不到逐日資料，這個取捨划算。

**稽核欄位**：所有資料表統一加上 `created_at`／`created_by`／`updated_at`／`updated_by`
四個欄位（DEC-25）。系統寫入（FinMind同步、AI建議產生）的 `*_by` 固定填系統識別字串
（例如 `'finmind-sync'`／`'gemini-ai'`）；使用者直接編輯的 `stock_target_price_setting`，
`*_by` 目前固定填佔位值（例如 `'default-user'`，因為目前沒有登入機制，DEC-21）——未來加上
使用者驗證時，只需要把這個佔位值換成真實 user_id，不影響表結構。

因為每張表都有 `updated_at`，原本規劃中幾個意義重複的時間戳欄位已移除：
`finmind_sync_log.last_synced_at`、`stock.info_synced_at`、`stock_ai_suggestion.generated_at`
——這幾張表都是「一有更新就整列被系統重寫」的性質，`updated_at` 本身就等於這些欄位想表達的
意思，統一用 `updated_at` 代表「上次更新時間」，避免同一件事被兩個欄位分別記錄、以後兩者對不上。

**單一使用者**：目前確定不做登入（DEC-21），資料表暫不加 `user_id`；未來要加登入，只需對
`stock_target_price_setting` 加欄位，不影響其他共用快取表。

## 3. ER 圖

```mermaid
erDiagram
    STOCK ||--o{ STOCK_PRICE_YEARLY : has
    STOCK ||--o{ STOCK_VALUATION_YEARLY : has
    STOCK ||--o{ STOCK_FINANCIAL_STATEMENT : has
    STOCK ||--o{ STOCK_DIVIDEND : has
    STOCK ||--o{ FINMIND_SYNC_LOG : has
    STOCK ||--|| STOCK_TARGET_PRICE_SETTING : has
    STOCK ||--o| STOCK_AI_SUGGESTION : has

    STOCK {
        varchar stock_id PK
        varchar stock_name
        varchar industry_category
        varchar market_type
        numeric latest_close_price
        date latest_price_date
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_PRICE_YEARLY {
        varchar stock_id PK_FK
        int year PK
        numeric avg_close_price
        numeric high_price
        numeric low_price
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_VALUATION_YEARLY {
        varchar stock_id PK_FK
        int year PK
        numeric avg_per
        numeric low_per
        numeric high_per
        numeric avg_pbr
        numeric low_pbr
        numeric high_pbr
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_FINANCIAL_STATEMENT {
        bigint id PK
        varchar stock_id FK
        date statement_date
        varchar type
        numeric value
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_DIVIDEND {
        bigint id PK
        varchar stock_id FK
        int year
        date cash_ex_dividend_trading_date
        date cash_dividend_payment_date
        numeric cumulative_cash_dividend
        numeric cumulative_stock_dividend
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    FINMIND_SYNC_LOG {
        bigint id PK
        varchar stock_id FK
        varchar dataset
        date last_data_date
        varchar status
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_TARGET_PRICE_SETTING {
        varchar stock_id PK_FK
        text investment_strategy
        smallint eps_avg_years
        smallint bps_avg_years
        text setting_note
        smallint target_price_level
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
    STOCK_AI_SUGGESTION {
        varchar stock_id PK_FK
        text suggestion_text
        varchar model_name
        timestamptz created_at
        varchar created_by
        timestamptz updated_at
        varchar updated_by
    }
```

## 4. 資料表定義

以下八張表全部都有標準四欄位 `created_at`／`created_by`／`updated_at`／`updated_by`，不重複列出。

### 4.1 stock（← TaiwanStockInfo）
股票基本資料 + 目前成交價快取。

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK | 股票代號 |
| stock_name | VARCHAR(100) | 股票名稱 |
| industry_category | VARCHAR(100) | 產業別 |
| market_type | VARCHAR(20) | 市場別（上市/上櫃/興櫃） |
| latest_close_price | NUMERIC(12,4) | 目前成交價（DEC-18 專用，只存最新一筆） |
| latest_price_date | DATE | 上面那筆價格的交易日期 |

### 4.2 stock_price_yearly（← TaiwanStockPrice，年度聚合，DEC-24）
PK：(stock_id, year)。只存**完整曆年**，不含當年度。

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK | |
| year | INT PK | 曆年 |
| avg_close_price | NUMERIC(12,4) | 該年度收盤價平均（供 PER/PBR(均)、殖利率換算用） |
| high_price | NUMERIC(12,4) | 該年度盤中實際最高價 |
| low_price | NUMERIC(12,4) | 該年度盤中實際最低價 |

### 4.3 stock_valuation_yearly（← TaiwanStockPER，年度聚合，DEC-24）
PK：(stock_id, year)。

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK | |
| year | INT PK | 曆年 |
| avg_per | NUMERIC(10,4) | 年度平均 PER |
| low_per | NUMERIC(10,4) | 年度最低 PER |
| high_per | NUMERIC(10,4) | 年度最高 PER |
| avg_pbr | NUMERIC(10,4) | 年度平均 PBR |
| low_pbr | NUMERIC(10,4) | 年度最低 PBR |
| high_pbr | NUMERIC(10,4) | 年度最高 PBR |

不含殖利率欄位——十年平均殖利率改為請求當下依還原殖利率公式即時計算，不落地存表
（DECISIONS.md DEC-027，取代原本存放 FinMind dividend_yield 的 avg_dividend_yield 欄位）。

### 4.4 stock_financial_statement（← TaiwanStockFinancialStatements，維持逐季）
EAV 結構貼近 FinMind 原始形狀，方便未來擴充 EPS 以外的財報科目。

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGSERIAL PK | |
| stock_id | VARCHAR(10) FK | |
| statement_date | DATE | 該季報所屬日期 |
| type | VARCHAR(50) | 科目，目前只用 'EPS'，未來可擴充 |
| value | NUMERIC(18,4) | 數值（EPS 已確認為單季，年度EPS=四季加總，DEC-15） |
| origin_name | VARCHAR(100) | FinMind 原始科目名稱 |

UNIQUE(stock_id, statement_date, type)

### 4.5 stock_dividend（← TaiwanStockDividend，維持逐筆）

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGSERIAL PK | |
| stock_id | VARCHAR(10) FK | |
| year | INT | **由應用程式自 `cash_ex_dividend_trading_date`（缺值退回 `announcement_date`）計算的西元年**，非 FinMind 原始欄位——FinMind 的 `year` 是民國年+季度文字標籤（如「112年第3季」），格式不穩定不可直接存整數欄位（實際呼叫 API 驗證後發現，決策見 DEC-005） |
| cash_earnings_distribution | NUMERIC(10,4) | |
| cash_statutory_surplus | NUMERIC(10,4) | |
| cash_ex_dividend_trading_date | DATE | 除息交易日（SA-01「股利發日」來源，DEC-12） |
| cash_dividend_payment_date | DATE | 撥款入帳日（目前 SA-01 未使用，保留供未來擴充） |
| stock_earnings_distribution | NUMERIC(10,4) | |
| stock_statutory_surplus | NUMERIC(10,4) | |
| stock_ex_dividend_trading_date | DATE | |
| announcement_date | DATE | |
| cumulative_cash_dividend | NUMERIC(10,4) | 該年度依公告日累加到「這一筆為止」的現金股利合計；同一年度公告日最新一筆＝該年度完整總股利（DEC-027） |
| cumulative_stock_dividend | NUMERIC(10,4) | 同上，股票股利版本 |

UNIQUE(stock_id, year, announcement_date)

### 4.6 finmind_sync_log（同步狀態追蹤，跨資料集/跨功能共用）

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGSERIAL PK | |
| stock_id | VARCHAR(10) FK | |
| dataset | VARCHAR(50) | 'TaiwanStockPrice' / 'TaiwanStockPER' / 'TaiwanStockDividend' / 'TaiwanStockFinancialStatements' / 'TaiwanStockInfo' |
| last_data_date | DATE | 已同步覆蓋到的最後日期；年度粒度的資料集存最後完整年度的 12/31 |
| status | VARCHAR(20) | 'ok' / 'error' |

UNIQUE(stock_id, dataset)。「上次同步時間」＝這張表的 `updated_at`。

### 4.7 stock_target_price_setting（本畫面專屬設定）

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK | |
| investment_strategy | TEXT | 投資策略 |
| eps_avg_years | SMALLINT | 3/5/10 |
| bps_avg_years | SMALLINT | 3/5/10 |
| setting_note | TEXT | 設定備註 |
| target_price_level | SMALLINT | 1~6 |

### 4.8 stock_ai_suggestion（Gemini AI 投資建議快取，按鈕觸發才更新）

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK | |
| suggestion_text | TEXT | AI 建議內容，prompt 端限制200字以內，DB 不額外設硬限制 |
| model_name | VARCHAR(50) | 例如 'gemini-3-flash'，方便未來換模型時追蹤是哪個模型產生的 |

「上次更新時間」＝這張表的 `updated_at`，畫面上顯示在建議文字旁邊（DEC-22）。

## 5. 讀取／更新流程

### 進入頁面（GET）
1. 讀 `stock` + `stock_target_price_setting`（沒有設定資料就建一筆預設值）
2. 若 `finmind_sync_log` 對這支股票完全沒有任何 dataset 紀錄（從沒同步過）→ 觸發一次完整初始化：
   TaiwanStockPrice、TaiwanStockPER 各打一次區間查詢（近10個完整曆年，一次區間、不逐日/逐年迴圈打），
   在應用程式端依年份分組聚合，只把年度統計結果 upsert 進 `stock_price_yearly`／
   `stock_valuation_yearly`；TaiwanStockDividend、TaiwanStockFinancialStatements、
   TaiwanStockInfo 各打一次，直接存
3. 目前成交價檢查：`stock.latest_price_date` 不是今天 → 只打一次 FinMind 抓最新一筆價格，
   更新 `stock.latest_close_price`／`latest_price_date`（DEC-18）
4. 其餘全部欄位（10年高/低、PER/PBR統計、EPS/BPS取X年平均、6檔目標價、差比、X年內買回、
   賣賺幾年、前目標價出現）純粹從步驟1~3已經在庫的資料即時算出，**不再打 FinMind**
5. 「上次更新時間」＝ TaiwanStockPrice / TaiwanStockPER / TaiwanStockDividend /
   TaiwanStockFinancialStatements 四個 dataset 在 `finmind_sync_log` 的 `updated_at` 取
   **MIN**（取最保守的一筆；TaiwanStockInfo 不列入，因為名稱/產業別幾乎不會變動，列入只會讓
   「上次更新時間」看起來莫名很舊）（DEC-19）

### 按下「更新」按鈕
- TaiwanStockPrice、TaiwanStockPER：抓「上次完整年度＋1」到「今天」的區間資料（一次區間查詢），
  在應用程式端聚合成年度統計後 upsert（今年還沒結束，所以不會產生「今年」那一列，維持與
  SA-01「10年統計不含當年度」一致）
- TaiwanStockDividend、TaiwanStockFinancialStatements：資料量小，直接整個股票的全部歷史重抓
  一次，順便涵蓋公司事後更正財報/股利公告的情況
- 全部更新 `finmind_sync_log` 對應列（`updated_at` 會自動反映最新同步時間）
- **不動** `stock_target_price_setting`（DEC-20）

### 按下「AI投資建議」按鈕
- 後端組 prompt：帶入這支股票目前已算好的基本面/技術面資料（目前成交價、目標價、與目標價差比、
  10年高/低、殖利率等——直接重用步驟4已經算好的值，不重新查資料庫），呼叫 Gemini API
  （Flash 或 Flash-Lite，啟用 Google Search grounding 取得時事資訊），要求回覆 200 字以內的
  短中期投資建議
- 結果 upsert 進 `stock_ai_suggestion`（覆蓋舊的一筆），畫面顯示 `suggestion_text` + 旁邊顯示
  `updated_at`（上次更新時間）
- 頁面載入時若 `stock_ai_suggestion` 已有資料就直接顯示舊的，**不會自動重打 Gemini**，只有按鈕
  按下去才會重新產生——避免每次進頁面就消耗 Gemini 免費額度（DEC-22）

## 6. 決策紀錄（延續 SA-01/SA-02 編號）

- **DEC-17**：計算結果（目標價、1~6檔價位、差比、X年內買回、賣賺幾年等）不落地存表，
  每次請求時從共用原始資料層即時運算。
- **DEC-18**：「目前成交價每次進頁面自動更新」= 先查 `stock.latest_price_date` 是不是今天，
  不是才打一次 FinMind 抓最新價格並更新；同一天內重複進頁面不會重複打 API。
- **DEC-19**：「上次更新時間」取本畫面依賴的四個 dataset（Price/PER/Dividend/FinancialStatements）
  在 `finmind_sync_log` 的 `updated_at` 取最小值（MIN）；TaiwanStockInfo 不列入計算。
- **DEC-20**：手動更新按鈕對「逐年聚合型」資料集（Price、PER）採「回補窗口＋delta」抓法（從上次
  完整年度到今天，一次區間查詢）；對「低頻型」資料集（Dividend、FinancialStatements）因資料量小，
  直接整批重抓，避免遺漏公司事後更正的資料。
- **DEC-21**：目前確定不做使用者登入，`stock_target_price_setting` 暫不加 `user_id`，先做單一
  使用者版本；未來要加登入，只需對這張表加欄位，不影響其他共用快取表。
- **DEC-22**：「AI投資建議」結果存進 `stock_ai_suggestion`，每支股票只保留最新一筆（upsert 覆蓋）；
  頁面載入時顯示已存的舊建議＋上次更新時間，不自動重打 Gemini，只有使用者主動按按鈕才會重新
  產生一筆，理由與 FinMind 的手動更新按鈕一致——保護免費額度。
- **DEC-23**：Gemini 呼叫啟用 Google Search grounding 以取得「時事」這塊資訊（模型本身的訓練知識
  不含最新新聞）；基本面/技術面資料直接重用當下已經算好的畫面欄位，不另外重查；回覆長度限制
  200字以內。⚠️ Grounding 每月5,000次免費額度是與 Flash/Flash-Lite 本身的 RPD 額度分開計算的
  兩組配額，待實作時再次確認官方最新條款。
- **DEC-24**：TaiwanStockPrice／TaiwanStockPER 這兩個逐日資料集，不存逐日原始資料，改成應用程式端
  聚合成「年度」統計後只存年度粒度（`stock_price_yearly`／`stock_valuation_yearly`）。取捨：
  未來若要做需要逐日資料的功能（如股價走勢圖），屆時需另外向 FinMind 重新抓逐日資料；
  FinMind 本身仍是 source of truth，重抓不會遺失資料。財報/股利資料量小，不受影響、維持細粒度。
- **DEC-25**：所有資料表統一加上 `created_at`／`created_by`／`updated_at`／`updated_by` 四個
  稽核欄位。因此移除原本規劃中與 `updated_at` 意義重複的欄位：`finmind_sync_log.last_synced_at`、
  `stock.info_synced_at`、`stock_ai_suggestion.generated_at`。系統寫入的 `*_by` 固定填系統
  識別字串；`stock_target_price_setting` 的 `*_by` 目前固定填佔位值（無登入機制，DEC-21）。

## 7. 開放問題

- 財報/股利資料的整批重抓（DEC-20）會不會在股票數量變多後，累積起來也接近額度上限？目前先以
  單股操作為主，暫不做全市場批次排程；如果之後真的要做「多股票同時追蹤」，要重新評估排程策略。
- `stock_health_snapshot`（第 10 節）長期使用下每季都會為每支股票新增一批快照列，需要自動清理
  機制（排程或搭配「更新」按鈕順便清），避免資料表無限增長——已記錄為未來待實作項目，本輪
  SA-03 開發不包含，詳見 DECISIONS.md DEC-042。

## 8. 搜尋股票 + 我的最愛（追加功能）

### 8.1 設計思路

「搜尋出其他支股票」意味著使用者要能找到**還沒被打開過**的股票（`stock` 表原本只有使用者
實際點開過的股票才有資料，DEC-18 的 lazy-fetch 設計）。已查證 FinMind `TaiwanStockInfo`
在**不帶 `data_id`** 時會回傳全市場清單（實測 4,295 檔，單次 API 呼叫，只吃 1 個免費額度）——
所以搜尋不需要每次打 FinMind，改成：先用一次性的「股票目錄同步」把全市場的
`stock_id`／`stock_name`／`industry_category`／`market_type` 灌進 `stock` 表，之後搜尋都是
查本地 DB（LIKE 查詢），跟原本「點開才抓價格/估值資料」的 lazy-fetch 邏輯完全不衝突——目錄
同步只補**身分欄位**，`latest_close_price` 等其他欄位仍然維持「使用者實際打開該股票時才抓」
（DEC-18 不變）。

「我的最愛」則是新增一張 `favorite_stock` 表，記錄使用者收藏了哪些 `stock_id`；因為目前
沒有登入機制（DEC-21），先做成單一使用者的收藏清單，不加 `user_id`。

### 8.2 新增資料表：favorite_stock

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGINT PK | |
| stock_id | VARCHAR(10) FK → stock | |
| created_at/created_by/updated_at/updated_by | 標準稽核欄位 | 收藏時間即 `created_at` |

UNIQUE(stock_id) — 同一支股票只能收藏一次（FK 依賴 8.1 的目錄同步先把身分資料灌進
`stock` 表，所以收藏一支「還沒被打開過」的搜尋結果也不會違反 FK）。

### 8.3 讀取／更新流程（追加）

**股票目錄同步**：搜尋 API 被呼叫時，若 `stock` 表目前資料列數低於一個門檻（例如 <100，
代表目錄從沒同步過），先觸發一次 `TaiwanStockInfo`（不帶 `data_id`）全市場同步，
以 `stock_id` 去重（同一 `stock_id` 若回傳多筆取第一筆，沿用 DEC-7 的邏輯），只 upsert
身分欄位，不影響已經被使用者打開過、已有價格/估值資料的既有列。

**搜尋**：`stock_id` 或 `stock_name` 做 `LIKE %query%` 查詢本地 `stock` 表，不打 FinMind。

**加入/移除最愛**：直接對 `favorite_stock` 做 insert/delete，不涉及 FinMind。

**列出最愛**：查 `favorite_stock` JOIN `stock`，依 `created_at` 排序回傳。

## 9. 決策紀錄（延續）

- **DEC-26**：搜尋功能不即時打 FinMind，改成一次性的「股票目錄同步」（`TaiwanStockInfo`
  不帶 `data_id`，一次抓全市場約4,295檔，只吃1個API額度）把身分欄位灌進 `stock` 表，
  之後搜尋只查本地 DB。同步時機：搜尋 API 首次被呼叫、且 `stock` 表列數低於門檻時觸發，
  不做排程；只補身分欄位，不影響既有列的價格/估值資料，也不改變 DEC-18 的 lazy-fetch 邏輯。
- **DEC-27**：新增 `favorite_stock` 表記錄我的最愛，FK 依賴 `stock` 表（因此收藏前該股票的
  身分資料必須已經存在，由 DEC-26 的目錄同步保證）；目前無登入機制，不加 `user_id`
  （沿用 DEC-21）。

## 10. 企業體質檢視擴充（[SA-03](./SA-03-company-health-check.md)，規格已確認，尚未實作）

> 本節是 SA-03 的資料表設計，對應決策見 SA-03 第 6 節 DEC-028～DEC-031。規格已確認，
> 尚未動工寫程式。

**設計原則**：檢視項目（大項/細項/名稱/門檻/達標公式）比照 SA-01 公式的做法，寫死在後端
程式碼（依產業模板各自一份定義），不做成資料庫可設定的通用規則引擎；資料庫只存**使用者
實際輸入的數值與筆記**，用一張通用的「快照」表承接，新增檢視項目時只需要改程式碼，不需要
新增欄位或搬移資料。

### 10.1 新增資料表：stock_health_profile（公司資訊，不分季度，只存最新一份）

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK | |
| profile | TEXT | 公司簡介 |
| custom_note | TEXT | 自訂備註（整體評語），與細項筆記分開 |
| last_reviewed_quarter | VARCHAR(10) | 例如 `'2024Q4'`，上次檢視對應的季度標籤 |

標準四個稽核欄位；「更新時間」＝這張表的 `updated_at`。

### 10.2 新增資料表：stock_health_snapshot（檢視項目，每季一份快照）

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGINT PK | |
| stock_id | VARCHAR(10) FK → stock | |
| quarter | VARCHAR(10) | 例如 `'2024Q4'`，這筆數值對應的檢視季度；固定寬度格式下字串排序即時間排序，「近五季」查詢直接 `ORDER BY quarter DESC LIMIT 5`，不拆 year/quarter_num 兩欄 |
| metric_key | VARCHAR(50) | 對應後端程式碼定義的細項識別碼（例如 `'debt.currentRatio'`）；合法值由應用層驗證，不做資料庫層 FK/查找表，避免長出第二套「可設定規則」介面 |
| value | NUMERIC(18,4) | 數值，型別統一為數字（含布林類細項以 1/0 表示），比照 4.4 `stock_financial_statement` 的既有慣例（DEC-039）。達標狀態（達標/不達標/趨勢判斷）不落地存，讀取時依 `metric_key` 對應的程式碼門檻即時計算 |
| note | TEXT | 這一項這一季的筆記 |

標準四個稽核欄位；UNIQUE(stock_id, quarter, metric_key)。DEC-034 所列 6 個 FinMind 無法取得的
細項不需要特殊處理——v1 全部細項本來就是使用者手動輸入（DEC-030），這 6 個只是資料來源不是
FinMind，走同一張表、同一套寫入邏輯。

### 10.3 銀行專屬「股價變化」重用既有資料表（DEC-031）

銀行模板的「股價變化（近10年年均價/PBR/PER）」直接查詢既有的
`stock_price_yearly`／`stock_valuation_yearly`（[SA-01](./SA-01-stock-target-price.md)
已有），不新增資料表、不另外同步。

### 10.4 產業模板判斷（DEC-028）

`stock_health_profile` 不需要額外的 `industry_template` 欄位——模板由後端服務層在讀取當下
依 `stock.industry_category` 字串比對決定（含「銀行」「金控」「保險」「證券」等關鍵字歸類
銀行模板，其餘一律一般模板），避免與 `industry_category` 形成兩份可能不一致的產業分類資料。

### 10.5 新增資料表：stock_health_ai_suggestion（AI 投資建議快取，DEC-082，已實作）

| 欄位 | 型別 | 說明 |
|---|---|---|
| stock_id | VARCHAR(10) PK/FK → stock | |
| suggestion_text | TEXT | AI 建議內容，prompt 端限制400字以內（比照 4.8 `stock_ai_suggestion` 但上限拉高），DB 不額外設硬限制 |
| model_name | VARCHAR(50) | 例如 `'groq/compound-mini'` |

標準四個稽核欄位；每支股票只保留最新一筆，「上次更新時間」＝這張表的 `updated_at`——與 4.8
`stock_ai_suggestion`（目標價頁 AI 建議）完全同構但獨立一張表，兩個功能各自的 prompt/內容
不同，不共用同一列。
