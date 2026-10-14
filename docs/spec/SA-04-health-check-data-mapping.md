# SA-04：企業體質檢視資料來源對應表（Company Health Check Data Mapping）

## 1. 用途

記錄 SA-03「企業體質檢視」畫面（一般模板／銀行模板）每一個欄位的**公式定義**與**資料來源**，
作為串接 FinMind、以及後續設計 `stock_health_snapshot` 資料表與同步邏輯的實作依據。內容整理自
使用者提供的權威欄位清單 `StockAnalysis.xlsx`（工作表「體質 web column」，已用 PowerShell OOXML
解析技術逐字擷取全部 92 個儲存格），並逐項查證 FinMind 官方文件
（`https://finmind.github.io/llms-full.txt`）與即時呼叫 `api.finmindtrade.com` 公開端點
（以股票 2330 為樣本）取得的實際 `type` 欄位值。

計算邏輯以本文件為準；SA-03 只負責畫面規格（大項/細項名稱、達標狀態顯示、更新機制），不重複
列出公式與資料來源，避免兩處維護。

> ✅ 已於 TASK-044（DEC-053）實作：本文件盤點的欄位除 DEC-034 那 6 個 FinMind 無法取得的
> 之外，其餘全部由 `com.adamchung.stockanalysis.healthchecksync` 套件自動同步。實作過程中
> 額外發現並處理的一般模板／銀行模板欄位命名差異、銀行財報結構缺少部分科目的已知限制，見
> DEC-053。

## 2. 一般產業欄位對照表（12 大項）

| 大項目 | 網頁欄位 | 說明（原文門檻） | 公式/定義 | 資料來源 |
|---|---|---|---|---|
| （通用） | 最新資料季度 | 確認本次資料的最新季度 | 顯示 `stock_financial_statement` 目前已同步到的最新 `statement_date` 所屬季度 | 衍生自本地已同步資料，非直接對應單一 FinMind 欄位 |
| 是否賺錢 | 10年來是否年年賺錢 | 看EPS，近10年只容許一年虧損 | 近10個完整曆年，逐年判斷稅後淨利是否為正 | `TaiwanStockFinancialStatements`，`type=IncomeAfterTaxes` |
| 盈餘再投資比率 | 盈餘再投資比率 | ＜200%，顯示近五季 | **資本支出 ÷ 稅後淨利** | 資本支出：`TaiwanStockCashFlowsStatement`，`type=PropertyAndPlantAndEquipment`；稅後淨利：`TaiwanStockFinancialStatements`，`type=IncomeAfterTaxes` |
| 靠本業賺錢 | 本業收入比(季)／本業收入比(年) | >80%，(近一季營業利益/近一季稅前淨利)，(近一年營業利益/近一年稅前淨利) | 營業利益 ÷ 稅前淨利 | `TaiwanStockFinancialStatements`，`type=OperatingIncome` ÷ `type=PreTaxIncome` |
| 業外是否虧損 | 其他綜合損益(季) | 其他綜合損益合計 ÷ 稅後淨利，顯示近五季 | 同左 | `TaiwanStockFinancialStatements`，`type=OtherComprehensiveIncome` ÷ `type=IncomeAfterTaxes` |
| 營收是否灌水 | 營收灌水比例(年)（DEC-065，取代 DEC-035 的季度版） | ＜30%，(應收+存貨)/營收，顯示近五年 | (應收帳款淨額+存貨的**年底Q4快照**) ÷ **年營收**（四季加總） | 應收/存貨：`TaiwanStockBalanceSheet`（`AccountsReceivableNet`／`Inventories`，取每年Q4季底存量）；營收：`TaiwanStockFinancialStatements`，`type=Revenue`（單季值加總四季） |
| 現金流 | 營業金流(億)(季) | >0 才能發錢，顯示近五季 | 直接對應 | `TaiwanStockCashFlowsStatement`，`type=NetCashInflowFromOperatingActivities` |
| 現金流 | 自由金流(億)(季) | （Excel 無獨立說明文字） | 營業金流 − 資本支出（推導） | 資本支出：`TaiwanStockCashFlowsStatement`，`type=PropertyAndPlantAndEquipment`；FinMind 無現成「自由現金流」欄位 |
| 欠錢壓力 | 負債總額佔比(季) | ＜50%，應付帳款包含其中，可另外檢視 | 負債總額 ÷ 資產總額 | `TaiwanStockBalanceSheet`，`type=Liabilities` ÷ `type=TotalAssets` |
| 欠錢壓力 | 流動資產對流動負債(季) | >100% | 流動資產 ÷ 流動負債 | `TaiwanStockBalanceSheet`，`type=CurrentAssets` ÷ `type=CurrentLiabilities` |
| 欠錢壓力 | 速動資產對流動負債(季) | >100%，速動資產＝流動資產－存貨 | (流動資產－存貨) ÷ 流動負債 | `TaiwanStockBalanceSheet`（`CurrentAssets`－`Inventories`）÷ `CurrentLiabilities` |
| 欠錢壓力 | 利息保障倍數(季) | >5倍 | EBIT ÷ 利息費用，EBIT 代理公式＝稅前淨利＋利息費用（推導） | 利息費用：`TaiwanStockCashFlowsStatement`，`type=InterestExpense`；稅前淨利：`TaiwanStockFinancialStatements`，`type=PreTaxIncome` |
| 股本膨脹率 | 股本膨脹率(年) | 今年與5年前股本成長＜20%，太多會降持股獲利 | (今年股本－5年前股本) ÷ 5年前股本 | `TaiwanStockBalanceSheet`，`type=CapitalStock` |
| 內行人持有率 | 董監持股比(%) | 顯示近五季 | 董事＋監察人持股加總 ÷ 已發行股數 | FinMind 無法取得；改由 TWSE OpenAPI（`t187ap11_L`＋`t187ap03_L`）取得，僅最新一期，每次同步累積一筆（DEC-046） |
| 內行人持有率 | 法人持股比季變化(%)（原「董監+法人持股比(%)」，見 DEC-049） | JUDGMENT 純參考，顯示近五季 | 外資陸資季變動(精確) ＋ 投信季變動(估算) ＋ 自營商季變動(估算) 三者加總 | FinMind 無法取得；改由 TWSE rwd `MI_QFIIS`（外資陸資持股水位）＋ `TWT47U`（投信/自營商買賣超）取得，近5季（DEC-046） |
| 內行人持有率 | 全體董監質押(%) | ＜33%，顯示近五季 | 董事＋監察人設質股數加總 ÷ 董事＋監察人持股加總 | FinMind 無法取得；改由 TWSE OpenAPI（`t187ap11_L`）取得，僅最新一期，每次同步累積一筆（DEC-046） |
| 盈餘分配率 | 盈餘總分配率 | 為了配息拿公積出來發頻率，看是否穩定 | **現金股利(元) ÷ EPS(元)** | 現金股利：`TaiwanStockDividend`，`CashEarningsDistribution`；EPS：`TaiwanStockFinancialStatements`，`type=EPS` |
| 毛利率 | 毛利率(季)(%)／年毛利率(%) | 高毛利率代表獨佔、穩定，看是否穩定or攀升 | 毛利 ÷ 營收 | `TaiwanStockFinancialStatements`，`type=GrossProfit` ÷ `type=Revenue` |
| 營業數據 | 營業利益(億)(季) | 看是否穩定or攀升 | 直接對應 | `TaiwanStockFinancialStatements`，`type=OperatingIncome` |
| 營業數據 | 淨利(億)(季) | 同上 | 直接對應 | `TaiwanStockFinancialStatements`，`type=IncomeAfterTaxes` |
| 營業數據 | EPS(季)／EPS(年) | 同上 | 直接對應，`type=EPS` 為**單季**數字（與 SA-02 既有假設一致，非累計）；EPS(年) 由四季加總 | `TaiwanStockFinancialStatements`，`type=EPS` |
| 營業數據 | ROE(年) | 同上 | 稅後淨利 ÷ 股東權益（推導，FinMind 無現成 ROE 欄位） | `TaiwanStockFinancialStatements`（`IncomeAfterTaxes`）÷ `TaiwanStockBalanceSheet`（`Equity`） |

## 3. 金融產業欄位對照表（4 大項，取代一般模板中對該產業不適用的大項）

| 大項目 | 細項 | 顯示範圍 | 公式/定義 | 資料來源 |
|---|---|---|---|---|
| 營收 | 月營收 | 近三年 | 直接對應 | `TaiwanStockMonthRevenue`（`revenue`／`revenue_month`／`revenue_year`） |
| 營收 | 單月營收月增(%) | 近三年 | (本月營收－上月營收) ÷ 上月營收（FinMind 只給原始月營收，月增/年增%需自行計算） | `TaiwanStockMonthRevenue` |
| 營收 | 單月營收年增(%) | 近三年 | (本月營收－去年同月營收) ÷ 去年同月營收 | `TaiwanStockMonthRevenue` |
| 股價變化 | 年均價／年均PBR／年均PER | 近十次 | 直接對應既有年度聚合 | 重用既有 `stock_price_yearly`／`stock_valuation_yearly`（DEC-031 已確認，資料源即 `TaiwanStockPrice`／`TaiwanStockPER`，不需新整合） |
| 籌碼變化 | 外資買賣超張數(季) | 近五次 | 季內逐日買賣超張數加總 | `TaiwanStockInstitutionalInvestorsBuySell`（逐日，需依季分組加總） |
| 籌碼變化 | 外資持股(%)(季) | 近五次 | 取季底（或季底前最近交易日）值 | `TaiwanStockShareholding`，`ForeignInvestmentSharesRatio`（逐日） |
| 籌碼變化 | 三大法人買賣超張數(季) | 近五次 | 季內逐日三大法人合計買賣超張數加總 | `TaiwanStockInstitutionalInvestorsBuySell`（或 `...Wide` 版本，逐日，需依季分組加總） |
| 銀行監理指標 | 資本適足率(%)(季) | 近五次 | 直接對應 | FinMind 無法取得；改由銀行局 `bis.xls`（全體銀行，依 `BankIdentityLookup` 名稱對照篩出目標銀行）取得，近5季（DEC-046） |
| 銀行監理指標 | 逾期放款比率(%)(季) | 近五次 | 直接對應 | FinMind 無法取得；改由銀行局「資產品質評估分析統計表」xlsx（依名稱對照篩出目標銀行）取得，近5季（DEC-046） |
| 銀行監理指標 | 備抵呆帳覆蓋率(%)(季) | 近五次 | 直接對應 | FinMind 無法取得；同上（DEC-046） |

## 4. 尚待實作前二次驗證的細節

無明確開放問題。以下三點屬「我方推導公式，Excel 原文未明寫」，已與使用者確認方向，實作時建議
用已知股票數字（例如 1216 統一／2884 玉山金）代入驗算，確保推導結果符合直覺：

- 自由金流＝營業金流－資本支出（DEC-036）
- 利息保障倍數之 EBIT 代理＝稅前淨利＋利息費用（DEC-037）
- 盈餘再投資比率已對真實 1216 資料驗證公式正確（DEC-063）

> 營收灌水比例：DEC-035（改當季比對）已於 DEC-065 反轉回年度比對，見上方表格與第5節。

## 5. 決策紀錄（延續 SA-01～SA-03 編號，DEC-032 起）

- **DEC-032**：盈餘再投資比率＝資本支出 ÷ 稅後淨利。資本支出取 `TaiwanStockCashFlowsStatement`
  的 `PropertyAndPlantAndEquipment` 作為代理欄位，稅後淨利取 `TaiwanStockFinancialStatements`
  的 `IncomeAfterTaxes`。
- **DEC-033**：盈餘總分配率＝現金股利(元) ÷ EPS(元)，取每股基礎的配息率，而非公司總額基礎。
- **DEC-034**：董監持股比(%)、董監+法人持股比(%)、全體董監質押(%)（一般模板）與資本適足率(%)、
  逾期放款比率(%)、備抵呆帳覆蓋率(%)（銀行模板）共 6 個欄位，經查證 FinMind 全部 dataset
  皆無對應資料（含全文搜尋官方文件「董監」「pledge」「director」「資本適足」等關鍵字均無結果）。
  v1 版本先留白，畫面上保留欄位但不自動帶入數值，資料來源另行確認（使用者已表示會自行透過其他
  管道研究，不在本輪代為規劃爬蟲/其他資料源方案）。
- **DEC-035**：營收灌水比例改採**當季**應收帳款淨額+存貨，對**當季**營收比對，不做年度加總；
  欄位由 Excel 原文的「(年)」改為「(季)」，顯示範圍由「近五年」改為「近五季」。理由：資產負債表
  是季底存量資料，若拿它對應「全年加總」的營收，屬於不同資料頻率的混用；改成同一季對比後，
  兩邊資料頻率一致，且不需要額外做四季加總。
- **DEC-036**：自由金流＝營業金流 − 資本支出。FinMind 無現成「自由現金流」欄位，屬推導公式，
  資本支出以 `TaiwanStockCashFlowsStatement` 的 `PropertyAndPlantAndEquipment` 代理。
- **DEC-037**：利息保障倍數的 EBIT 代理公式＝稅前淨利 ＋ 利息費用。FinMind 無現成 EBIT 欄位，
  屬推導公式。
- **DEC-038**：`TaiwanStockFinancialStatements` 的 `EPS` type 確認為**單季**數字，非累計數，
  與 SA-02 對股票目標價畫面的既有假設一致；SA-03「EPS(年)」由四季 EPS 加總取得。
- **DEC-046～DEC-049**：整合 `stock-financial-data-collector` 專案，把上方表格原本標記
  「FinMind 無法取得」的 5 個欄位（董監持股比、法人持股比季變化、全體董監質押、資本適足率、
  逾期放款比率／備抵呆帳覆蓋率）改為由 TWSE OpenAPI／TWSE rwd 日報／銀行局公開檔案自動同步，
  Java 後端重寫（非引入 Node.js 服務）；「董監+法人持股比(%)」同時簡化為「法人持股比季
  變化(%)」、達標邏輯改為純參考。完整決策內容見 `docs/agent/DECISIONS.md` DEC-046～049，
  這裡不重複列出。
- **DEC-063**：盈餘再投資比率（DEC-032）已對真實 1216（統一）2025Q4 資料驗證，公式本身
  正確，不需要改動；補上端到端迴歸測試釘住結果。
- **DEC-064**：業外是否虧損（其他綜合損益(季)／稅後淨利）的分母明文記錄為**合併（集團）**
  稅後淨利，不是母公司單獨數字——FinMind 本身沒有母公司單獨口徑可切換，這是資料本身的限制，
  不是計算錯誤。
- **DEC-065**：營收灌水比例改回**年**為單位，**取代 DEC-035**——應收帳款淨額+存貨取每年
  Q4（年底）季底存量快照，除以年營收（四季加總）；欄位名稱由「(季)」改回「(年)」，顯示範圍
  由「近五季」改回「近五年」。理由：使用者本輪要求改回年度口徑，年底快照更能代表「當年度」
  的灌水程度。完整決策內容見 `docs/agent/DECISIONS.md` DEC-065。
