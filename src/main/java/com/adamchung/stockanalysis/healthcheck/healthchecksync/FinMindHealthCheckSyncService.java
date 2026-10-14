package com.adamchung.stockanalysis.healthcheck.healthchecksync;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankFlows;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRevenue;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Capital;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.CashFlow;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Core;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Debt;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Margin;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.NonOperating;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Operations;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Payout;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Profit;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Reinvest;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.RevenueInflation;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.externaldata.QuarterRanges;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindInstitutionalInvestorsBuySellDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindMonthRevenueDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindShareholdingDto;
import com.adamchung.stockanalysis.healthcheck.calculator.HealthCheckTemplateResolver;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.HealthCheckFinancialMetricsCalculator;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.InstitutionalFlowMetricsCalculator;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.MonthRevenueMetricsCalculator;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.QuarterlyFactsJoiner;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.FinMindHealthCheckSyncResult;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyFinancialFacts;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 把 SA-04 盤點過、但 DEC-046~049（{@code externaldata} 套件）沒有涵蓋的其餘約 24 個體質檢視
 * 細項自動同步進 {@code stock_health_snapshot}，資料來源改用一般的 FinMind
 * dataset（財報三表／股利／月營收／三大法人買賣超／外資持股），不是 TWSE OpenAPI／銀行局
 * 檔案。跟 {@code ExternalHealthDataSyncService} 是兩個獨立的同步來源，各自
 * 呼叫、各自可能失敗，由上層 controller 合併結果（見 DEC-053）。
 *
 * <p>已對真實 API 驗證兩個模板差異：一般模板才有的 {@code core}／{@code nonOperating}／
 * {@code revenueInflation}／{@code debt}／{@code margin} 大項，銀行模板完全不會同步（銀行
 * FinancialStatements／BalanceSheet 沒有對應 type，即使硬同步也永遠是 null）；銀行模板才有的
 * {@code bankFlows}／{@code bankRevenue} 兩個大項，一般模板不會同步。兩模板共用的
 * {@code profit}／{@code reinvest}／{@code cashFlow}／{@code capital}／{@code payout}／
 * {@code operations} 六個大項都會同步，但銀行股的 {@code cashFlow.free}（缺
 * {@code PropertyAndPlantAndEquipment}）與 {@code operations.operatingIncome}（缺
 * {@code OperatingIncome}）這兩個細項因為銀行本身的財報結構沒有對應科目，同步後仍會是
 * 「尚無資料」，這是資料本身的限制，不是同步邏輯的缺陷。
 */
@Service
public class FinMindHealthCheckSyncService {

    private static final int FINANCIAL_STATEMENT_LOOKBACK_YEARS = 11;
    private static final int MONTH_REVENUE_LOOKBACK_YEARS = 4;
    private static final int INSTITUTIONAL_LOOKBACK_YEARS = 2;
    private static final int BANK_QUARTER_COUNT = 5;
    /**
     * DEC-073：股利查詢的 {@code end_date} 刻意比「今天」多留 3 個月緩衝。已對真實 API 驗證
     * （1216 統一）：FinMind {@code TaiwanStockDividend} 的 {@code date} 欄位不是除息日，
     * 常常比除息日（{@code CashExDividendTradingDate}）晚約一週才確定——如果 {@code end_date}
     * 直接傳「今天」，剛除息完沒幾天、{@code date} 欄位還沒追上「今天」的股利記錄會被 FinMind
     * API 自己的日期區間篩選整筆排除，導致
     * {@link com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.HealthCheckFinancialMetricsCalculator#totalPayoutRatio}
     * 少了最新一年度的股利，明明 EPS 已經有完整四季、卻算不出當年度的盈餘總分配率。股利本身
     * 不可能有「未來」資料，多留緩衝不會多抓到不該有的資料，3 個月遠大於已觀察到的落差。
     */
    private static final java.time.Period DIVIDEND_END_DATE_BUFFER = java.time.Period.ofMonths(3);

    private final StockRepository stockRepository;
    private final StockHealthSnapshotRepository snapshotRepository;
    private final FinMindClient finMindClient;

    public FinMindHealthCheckSyncService(
            StockRepository stockRepository,
            StockHealthSnapshotRepository snapshotRepository,
            FinMindClient finMindClient) {
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
        this.finMindClient = finMindClient;
    }

    /**
     * 刻意**不**加方法層級 {@code @Transactional}——理由跟 {@code ExternalHealthDataSyncService}
     * 一致（DEC-020／DEC-056）：銀行模板還會在一般細項同步完之後另外呼叫
     * {@link #syncBankOnlyMetrics}（月營收／籌碼變化，各自要再打幾次 FinMind），如果整個
     * 方法包在同一個 transaction，銀行專屬那段一旦失敗，前面已經成功寫入的一般共用細項
     * （現金流、股本膨脹率、EPS 等）也會被一起回滾。移除方法層級 transaction 後，每個
     * {@link #syncMetric} 呼叫底下的 upsert 各自獨立提交。
     */
    public FinMindHealthCheckSyncResult sync(String stockId) {
        StockEntity stock = requireStock(stockId);
        HealthCheckTemplate template = HealthCheckTemplateResolver.resolve(stock.getIndustryCategory());

        List<String> syncedMetricKeys = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalDate financialStart = now.minusYears(FINANCIAL_STATEMENT_LOOKBACK_YEARS);

        List<FinMindFinancialStatementDto> financialStatements =
                finMindClient.fetchFinancialStatement(stockId, financialStart, now);
        List<FinMindBalanceSheetDto> balanceSheet = finMindClient.fetchBalanceSheet(stockId, financialStart, now);
        List<FinMindCashFlowsStatementDto> cashFlowsStatement =
                finMindClient.fetchCashFlowsStatement(stockId, financialStart, now);
        List<FinMindDividendDto> dividends =
                finMindClient.fetchDividend(
                        stockId, now.minusYears(FINANCIAL_STATEMENT_LOOKBACK_YEARS), now.plus(DIVIDEND_END_DATE_BUFFER));

        List<QuarterlyFinancialFacts> facts =
                QuarterlyFactsJoiner.join(financialStatements, balanceSheet, cashFlowsStatement);

        syncMetric(stockId, Reinvest.REINVESTMENT_RATIO,
                HealthCheckFinancialMetricsCalculator.reinvestmentRatio(facts), syncedMetricKeys);
        syncMetric(stockId, CashFlow.OPERATING,
                HealthCheckFinancialMetricsCalculator.cashFlowOperating(facts), syncedMetricKeys);
        syncMetric(stockId, Capital.EXPANSION_RATIO,
                HealthCheckFinancialMetricsCalculator.capitalExpansionRatio(facts), syncedMetricKeys, true);
        syncMetric(stockId, Operations.NET_INCOME,
                HealthCheckFinancialMetricsCalculator.netIncomeBillion(facts), syncedMetricKeys);
        syncMetric(stockId, Operations.EPS_QUARTER,
                HealthCheckFinancialMetricsCalculator.epsQuarter(facts), syncedMetricKeys);
        syncMetric(stockId, Operations.EPS_YEAR,
                HealthCheckFinancialMetricsCalculator.epsYear(facts), syncedMetricKeys);
        syncMetric(stockId, Operations.ROE,
                HealthCheckFinancialMetricsCalculator.roe(facts), syncedMetricKeys);
        syncMetric(stockId, Profit.TEN_YEAR_PROFITABLE,
                HealthCheckFinancialMetricsCalculator.tenYearProfitable(facts), syncedMetricKeys);
        syncMetric(stockId, Payout.TOTAL_PAYOUT_RATIO,
                HealthCheckFinancialMetricsCalculator.totalPayoutRatio(facts, dividends), syncedMetricKeys);

        if (template == HealthCheckTemplate.GENERAL) {
            // 銀行財報結構沒有 PropertyAndPlantAndEquipment／OperatingIncome，這兩個細項
            // 銀行模板已經從 HealthCheckTemplateDefinitions.bankOperations()/bankCashFlow()
            // 移除，這裡對應不再對銀行股同步（DEC-055）。
            syncMetric(stockId, CashFlow.FREE,
                    HealthCheckFinancialMetricsCalculator.cashFlowFree(facts), syncedMetricKeys);
            syncMetric(stockId, Operations.OPERATING_INCOME,
                    HealthCheckFinancialMetricsCalculator.operatingIncomeBillion(facts), syncedMetricKeys);
            syncMetric(stockId, Core.OPERATING_INCOME_RATIO_QUARTER,
                    HealthCheckFinancialMetricsCalculator.operatingIncomeRatioQuarter(facts), syncedMetricKeys);
            syncMetric(stockId, Core.OPERATING_INCOME_RATIO_YEAR,
                    HealthCheckFinancialMetricsCalculator.operatingIncomeRatioYear(facts), syncedMetricKeys);
            syncMetric(stockId, NonOperating.OTHER_COMPREHENSIVE_INCOME_RATIO,
                    HealthCheckFinancialMetricsCalculator.otherComprehensiveIncomeRatio(facts), syncedMetricKeys);
            syncMetric(stockId, RevenueInflation.RATIO,
                    HealthCheckFinancialMetricsCalculator.revenueInflationRatio(facts), syncedMetricKeys);
            syncMetric(stockId, Debt.TOTAL_LIABILITIES_RATIO,
                    HealthCheckFinancialMetricsCalculator.totalLiabilitiesRatio(facts), syncedMetricKeys);
            syncMetric(stockId, Debt.CURRENT_RATIO,
                    HealthCheckFinancialMetricsCalculator.currentRatio(facts), syncedMetricKeys);
            syncMetric(stockId, Debt.QUICK_RATIO,
                    HealthCheckFinancialMetricsCalculator.quickRatio(facts), syncedMetricKeys);
            syncMetric(stockId, Debt.INTEREST_COVERAGE_RATIO,
                    HealthCheckFinancialMetricsCalculator.interestCoverageRatio(facts), syncedMetricKeys);
            syncMetric(stockId, Margin.QUARTER,
                    HealthCheckFinancialMetricsCalculator.marginQuarter(facts), syncedMetricKeys);
            syncMetric(stockId, Margin.YEAR,
                    HealthCheckFinancialMetricsCalculator.marginYear(facts), syncedMetricKeys);
        } else {
            syncBankOnlyMetrics(stockId, now, syncedMetricKeys);
        }

        String latestDataQuarter = facts.isEmpty() ? null : facts.get(0).quarter();
        return new FinMindHealthCheckSyncResult(stockId, syncedMetricKeys, latestDataQuarter);
    }

    private void syncBankOnlyMetrics(String stockId, LocalDate now, List<String> syncedMetricKeys) {
        List<FinMindMonthRevenueDto> monthRevenue = finMindClient.fetchMonthRevenue(
                stockId, now.minusYears(MONTH_REVENUE_LOOKBACK_YEARS), now);
        syncMetric(stockId, BankRevenue.MONTHLY_REVENUE,
                MonthRevenueMetricsCalculator.monthlyRevenue(monthRevenue), syncedMetricKeys);
        syncMetric(stockId, BankRevenue.MOM_GROWTH,
                MonthRevenueMetricsCalculator.momGrowth(monthRevenue), syncedMetricKeys);
        syncMetric(stockId, BankRevenue.YOY_GROWTH,
                MonthRevenueMetricsCalculator.yoyGrowth(monthRevenue), syncedMetricKeys);

        LocalDate institutionalStart = now.minusYears(INSTITUTIONAL_LOOKBACK_YEARS);
        List<FinMindInstitutionalInvestorsBuySellDto> institutionalFlow =
                finMindClient.fetchInstitutionalInvestorsBuySell(stockId, institutionalStart, now);
        List<FinMindShareholdingDto> shareholding = finMindClient.fetchShareholding(stockId, institutionalStart, now);
        List<QuarterRange> recentQuarters = QuarterRanges.recentQuarterRanges(BANK_QUARTER_COUNT, now);

        syncMetric(stockId, BankFlows.FOREIGN_NET_BUY_SELL_SHARES,
                InstitutionalFlowMetricsCalculator.foreignNetBuySellLots(institutionalFlow, recentQuarters),
                syncedMetricKeys);
        syncMetric(stockId, BankFlows.INSTITUTIONAL_NET_BUY_SELL_SHARES,
                InstitutionalFlowMetricsCalculator.institutionalNetBuySellLots(institutionalFlow, recentQuarters),
                syncedMetricKeys);
        syncMetric(stockId, BankFlows.FOREIGN_SHAREHOLDING_RATIO,
                InstitutionalFlowMetricsCalculator.foreignShareholdingRatio(shareholding, recentQuarters),
                syncedMetricKeys);
    }

    private void syncMetric(
            String stockId, String metricKey, List<QuarterlyMetricValue> values, List<String> syncedMetricKeys) {
        syncMetric(stockId, metricKey, values, syncedMetricKeys, false);
    }

    /**
     * @param replaceHistory true 時，這次算出的期間清單視為該細項的完整權威集合，upsert 完
     *                        之後會刪掉資料庫裡不在這個清單內的舊期間。只有
     *                        {@link Capital#EXPANSION_RATIO} 這種「目標期間會隨最新一季往前
     *                        推進」的細項需要（見
     *                        {@link HealthCheckFinancialMetricsCalculator#capitalExpansionRatio}
     *                        javadoc）：DEC-077 把邏輯從「輸出全部季度」改成「最新一季＋過去4個
     *                        完整曆年Q4」共5點之後，舊版同步留下的逐季資料仍留在資料庫，跟前端
     *                        {@code historyLimit=5}（近5期）的「取最新N筆」讀取邏輯疊在一起，
     *                        會把改版後真正該顯示的年度期間擠出畫面（使用者2026-08-07回報：
     *                        股本膨脹率(年)顯示2026Q1／2025／2025Q3／2025Q2／2025Q1，後三個
     *                        是改版前留下的舊資料）。其餘細項的期間集合是「
     *                        {@value #FINANCIAL_STATEMENT_LOOKBACK_YEARS}年抓取窗口內全部
     *                        可算出的季度」，本來就是每次同步的完整權威集合，不需要清舊資料。
     */
    private void syncMetric(
            String stockId, String metricKey, List<QuarterlyMetricValue> values, List<String> syncedMetricKeys,
            boolean replaceHistory) {
        if (values.isEmpty()) {
            return;
        }
        for (QuarterlyMetricValue value : values) {
            upsert(stockId, value.period(), metricKey, value.value(), value.note());
        }
        if (replaceHistory) {
            List<String> currentPeriods = values.stream().map(QuarterlyMetricValue::period).toList();
            snapshotRepository.deleteByStockIdAndMetricKeyAndQuarterNotIn(stockId, metricKey, currentPeriods);
        }
        syncedMetricKeys.add(metricKey);
    }

    /**
     * DEC-074：{@code suggestedNote} 只有在這期目前資料庫裡的筆記還是空白時才會寫入，一旦
     * 使用者已經存過任何筆記（即使只是把自動帶入的文字原封不動按下儲存），之後每次同步都不會
     * 再覆蓋——跟前端 {@code health-check-category.ts} 的 {@code currentNote()} 自動帶入邏輯
     * 保持一致的「不覆蓋使用者已存內容」承諾，避免使用者在筆記後面補充的判斷依據被同步覆寫掉。
     */
    private void upsert(String stockId, String period, String metricKey, java.math.BigDecimal value, String suggestedNote) {
        StockHealthSnapshotEntity row = snapshotRepository.findByStockIdAndQuarterAndMetricKey(stockId, period, metricKey)
                .orElseGet(() -> {
                    StockHealthSnapshotEntity fresh = new StockHealthSnapshotEntity();
                    fresh.setStockId(stockId);
                    fresh.setQuarter(period);
                    fresh.setMetricKey(metricKey);
                    fresh.setCreatedBy(AuditActors.FINMIND_HEALTHCHECK_SYNC);
                    return fresh;
                });
        row.setValue(value);
        if (suggestedNote != null && (row.getNote() == null || row.getNote().isBlank())) {
            row.setNote(suggestedNote);
        }
        row.setUpdatedBy(AuditActors.FINMIND_HEALTHCHECK_SYNC);
        snapshotRepository.save(row);
    }

    private StockEntity requireStock(String stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Unknown stock_id: " + stockId));
    }
}
