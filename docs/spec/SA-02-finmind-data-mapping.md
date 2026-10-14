# SA-02：FinMind 資料來源對應表（股票目標價畫面）

## 1. 用途
記錄 [SA-01「股票目標價」畫面](./SA-01-stock-target-price.md)各欄位所對應的 FinMind API 資料集/欄位，作為串接 FinMind 時的實作依據。
計算邏輯以 SA-01 為準，本文件只處理「資料從哪個 API 欄位來、怎麼聚合」；實際落地的資料表結構、
儲存粒度（逐日 vs 年度）與讀取/更新流程，見 [DB-01：資料庫設計](./DB-01-database-schema.md)。
本文件下方描述的「逐年聚合」邏輯，在 DB-01 中已改為**只落地存年度聚合結果，不存逐日原始資料**（DEC-24）。

## 2. 資料集總覽（已查證，2026-07-28）

| Dataset | 頻率 | 用途 | 關鍵欄位 |
|---|---|---|---|
| TaiwanStockInfo | 靜態 | 股票基本資料 | stock_id, stock_name, industry_category, type |
| TaiwanStockPrice | 逐日 | 股價 | date, stock_id, open, max, min, close, Trading_Volume, Trading_money, spread |
| TaiwanStockPER | 逐日 | 本益比/淨值比/殖利率 | date, stock_id, PER, PBR, dividend_yield |
| TaiwanStockFinancialStatements | **季報** | 財報科目（含EPS） | date, stock_id, type, value, origin_name |
| TaiwanStockDividend | 每年可能多筆 | 股利政策 | stock_id, year, CashEarningsDistribution, CashStatutorySurplus, CashExDividendTradingDate, CashDividendPaymentDate, StockEarningsDistribution, StockStatutorySurplus, StockExDividendTradingDate, AnnouncementDate |

## 3. 欄位對應表（已修正版）

| SA-01 欄位 | FinMind Dataset | 欄位 | 聚合方式 |
|---|---|---|---|
| 代號 | TaiwanStockInfo | stock_id | 直接對應 |
| 名稱 | TaiwanStockInfo | stock_name | 直接對應 |
| 目前成交價 | TaiwanStockPrice | close | 取最近一個交易日；當日尚未收盤更新時，退回前一交易日 |
| 10年高 | TaiwanStockPrice | max | 近10個完整曆年，逐日 max 取最大值〔DEC-10〕|
| 10年低 | TaiwanStockPrice | min | 近10個完整曆年，逐日 min 取最小值〔DEC-10〕|
| 各年度最低價（供「前目標價出現」判斷） | TaiwanStockPrice | min | 逐年：該年度所有交易日 min 取最小值 |
| 年度平均股價（供PER/PBR(均)、殖利率換算） | TaiwanStockPrice | close | 逐年：該年度所有交易日 close 取平均 |
| 十年平均殖利率 | TaiwanStockPrice + TaiwanStockDividend | close、Cash/StockEarningsDistribution、Cash/StockStatutorySurplus | 不使用 dividend_yield。逐年：以年度均價與該年度累積現金/股票股利，套還原殖利率公式即時算出 → 近10年再取平均（DEC-11 已由 DECISIONS.md DEC-027 取代）|
| 股利發日（除息交易日） | TaiwanStockDividend | CashExDividendTradingDate | 該股票所有已公告 CashExDividendTradingDate 中，取「今天以後最近一筆」；若無未來已公告資料，退回「今天以前最近一筆」〔DEC-12、DEC-14〕|
| 近3/5/10年平均EPS | TaiwanStockFinancialStatements | type=EPS, value | 已確認為**單季**數字：年度EPS = 該年度 Q1+Q2+Q3+Q4 四季加總，再依 EPS取X年平均 設定對近X個完整年度取平均〔DEC-15〕|
| 近3/5/10年平均BPS | TaiwanStockPER + TaiwanStockPrice | PBR、close | BPS = close ÷ PBR，逐日算出後依年度平均 |
| 10年平均最低/年均/最高PER | TaiwanStockPER | PER | 逐年：該年度 PER 取最小/平均/最大 → 近10年再平均 |
| 10年平均最低/年均/最高PBR | TaiwanStockPER | PBR | 同上 |
| 產業別 | TaiwanStockInfo | industry_category | UI 顯示於股票名稱旁的小字（見 SA-01 3.1、DEC-16），非獨立分析欄位 |

原表「近10年平均PER(本益比)落點」「近10年平均PBR(股價淨值比)落點」與年均PER/PBR重複，**已移除**〔DEC-13〕。

原表「當年/近10年 現金股利、股票股利」四列，當初移除的理由（殖利率已改用官方 dividend_yield、不做除權息還原）
已不成立——DEC-027 之後十年平均殖利率改為自行依還原殖利率公式計算，需要用到這4個原始股利金額，
已在 `stock_dividend` 新增 `cumulative_cash_dividend`／`cumulative_stock_dividend` 欄位承接（DECISIONS.md DEC-027）。

## 4. 重大實作風險（務必先驗證再寫程式）

- ~~**EPS 季報是「單季」還是「累計」？**~~ **已確認並解除**：TaiwanStockFinancialStatements 的 EPS 為
  **單季**數字，年度EPS = 該年度 Q1+Q2+Q3+Q4 四季加總（決策見 DEC-15）。原本擔心誤把累計數字當單季加總、
  導致年度EPS灌水近2倍的風險已排除，保留此紀錄供之後開發時確認實作方式與此一致。
- **BPS 除以零風險**：close ÷ PBR，若 PBR 為 0 或 null（虧損股淨值為負、或資料缺漏），需要防呆——跳過該筆、
  往前用最近一筆有效值，或該年度平均時排除異常值。
- **FinMind 免費額度**：TaiwanStockPER/TaiwanStockPrice/TaiwanStockDividend/TaiwanStockFinancialStatements
  都要逐檔、逐年抓，若同時對多檔股票做10年批次回補，需留意 300~600次/小時的額度上限，建議排程分批執行，
  不要在使用者即時查詢時才觸發大量回補。

## 5. 決策紀錄（延續 SA-01 編號）

- **DEC-10**：10年高/10年低、及「前目標價出現」用的年度最低價，改用 TaiwanStockPrice 的 max/min（盤中
  實際最高/最低價），取代原 DEC-4 的「一律收盤價」；PER/PBR(均)與年度殖利率換算仍使用收盤價。
- **DEC-11**：十年平均殖利率改採 TaiwanStockPER.dividend_yield 官方欄位（先年度平均、再10年平均），
  不再自行用現金股利÷均價手算。**已由 DECISIONS.md DEC-027 取代**：改回自行計算，但改用還原殖利率
  公式（現金+股票股利、考慮配股稀釋），不是原本「現金股利÷均價」的簡化版。
- **DEC-12**：「股利發日」實際對應 TaiwanStockDividend 的 CashExDividendTradingDate（除息交易日），
  並非撥款入帳日；SA-01 對此欄位定義修正為「除息交易日」。
- **DEC-13**：移除映射表中重複的「10年平均PER/PBR落點」列。
- **DEC-14**：「股利發日」改為優先取最近的**未來**除息日；若無已公告的未來除息日，才退回最近的**過去**除息日（詳見 SA-01 DEC-14）。
- **DEC-15**：已確認 TaiwanStockFinancialStatements 的 EPS 為**單季**數字，年度EPS = 該年度四季加總；原「重大實作風險」解除。
- **DEC-16**：「產業別」確定顯示於 SA-01 畫面的股票名稱旁小字，非獨立分析欄位（詳見 SA-01 DEC-16）。

## 6. 開放問題

- 「當年/近10年 現金股利、股票股利」4個原始股利金額欄位是否要保留供未來股利明細畫面使用？
  暫定不保留，待有需求再加回。
