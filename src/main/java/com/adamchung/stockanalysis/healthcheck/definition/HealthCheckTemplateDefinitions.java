package com.adamchung.stockanalysis.healthcheck.definition;

import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankFlows;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRegulatory;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRevenue;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Capital;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.CashFlow;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Core;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Debt;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Insider;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Margin;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.NonOperating;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Operations;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Payout;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Profit;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Reinvest;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.RevenueInflation;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import static com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType.BOOLEAN;
import static com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType.GREATER_THAN;
import static com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType.JUDGMENT;
import static com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType.LESS_THAN;
import java.math.BigDecimal;
import java.util.List;

/**
 * 一般模板／銀行模板的完整大項＋細項定義（SA-04；欄位命名/門檻文字逐字取自使用者提供的
 * Excel「體質 web column」，並沿用 prototype {@code stock-health-prototype.html} 已驗證過的
 * 結構）。銀行模板＝一般模板中「盈餘再投資比率、靠本業賺錢、業外是否虧損、營收是否灌水、
 * 欠錢壓力、毛利率」六個大項整組移除，其餘六個大項（是否賺錢、現金流、股本膨脹率、
 * 內行人持有率、盈餘分配率、營業數據）定義完全相同、只是實際數值不同，另外新增三個銀行
 * 專屬大項（SA-03 §2）。
 *
 * <p>「股價變化（近10年年均價/PBR/PER）」不在這裡定義——直接重用既有的
 * {@code stock_price_yearly}／{@code stock_valuation_yearly}（DEC-031），不是這個套件的
 * EAV 細項。
 */
public final class HealthCheckTemplateDefinitions {

    private HealthCheckTemplateDefinitions() {
    }

    public static List<HealthCheckCategoryDefinition> categoriesFor(HealthCheckTemplate template) {
        return template == HealthCheckTemplate.BANK ? bankCategories() : generalCategories();
    }

    private static List<HealthCheckCategoryDefinition> generalCategories() {
        return List.of(
                profit(), reinvest(), core(), nonOperating(), revenueInflation(), cashFlow(),
                debt(), capital(), insider(), payout(), margin(), operations());
    }

    private static List<HealthCheckCategoryDefinition> bankCategories() {
        return List.of(
                profit(), bankCashFlow(), capital(), insider(), payout(), bankOperations(),
                bankRegulatory(), bankFlows(), bankRevenue());
    }

    private static HealthCheckCategoryDefinition profit() {
        return category("profit", "是否賺錢", "EPS，近10年只容許一年虧損",
                metric(Profit.TEN_YEAR_PROFITABLE, "10年來是否年年賺錢", "", BOOLEAN, null, null, true));
    }

    private static HealthCheckCategoryDefinition reinvest() {
        return category("reinvest", "盈餘再投資比率", "盈餘再投資比率 ＜ 200%",
                metric(Reinvest.REINVESTMENT_RATIO, "盈餘再投資比率", "%", LESS_THAN, bd(200), null));
    }

    private static HealthCheckCategoryDefinition core() {
        return category("core", "靠本業賺錢", "本業收入比 ＞ 80%",
                metric(Core.OPERATING_INCOME_RATIO_QUARTER, "本業收入比(季)", "%", GREATER_THAN, bd(80), null),
                metric(Core.OPERATING_INCOME_RATIO_YEAR, "本業收入比(年)", "%", GREATER_THAN, bd(80), null, true));
    }

    private static HealthCheckCategoryDefinition nonOperating() {
        return category("nonOperating", "業外是否虧損", "業外損失比率 ＞ -20%",
                metric(NonOperating.OTHER_COMPREHENSIVE_INCOME_RATIO, "其他綜合損益(季)／稅後淨利", "%",
                        GREATER_THAN, bd(-20), null));
    }

    private static HealthCheckCategoryDefinition revenueInflation() {
        // DEC-065（取代 DEC-035）：改回原 Excel 標的「(年)」，應收+存貨取年底(Q4)快照，
        // 對年營收（四季加總）；isAnnual=true，前端只顯示年份不顯示「Q4」。
        return category("revenueInflation", "營收是否灌水", "營收灌水比例（應收+存貨/營收）＜ 30%",
                metric(RevenueInflation.RATIO, "營收灌水比例(年)", "%", LESS_THAN, bd(30), null, true));
    }

    private static HealthCheckCategoryDefinition cashFlow() {
        return category("cashFlow", "現金流", "現金 ＞ 0 才能發錢",
                metric(CashFlow.OPERATING, "營業金流(億)(季)", "億", GREATER_THAN, BigDecimal.ZERO, null),
                metric(CashFlow.FREE, "自由金流(億)(季)", "億", GREATER_THAN, BigDecimal.ZERO, null));
    }

    /**
     * 銀行模板專用的現金流大項：不含「自由金流」。FinMind
     * {@code TaiwanStockCashFlowsStatement} 對銀行股完全沒有 {@code PropertyAndPlantAndEquipment}
     * （資本支出）這個 type，銀行財報結構本身沒有這個科目，自由金流永遠算不出來，與其讓畫面
     * 一直顯示「尚無資料」，直接不對銀行模板顯示這個細項（DEC-055）。
     */
    private static HealthCheckCategoryDefinition bankCashFlow() {
        return category("cashFlow", "現金流", "現金 ＞ 0 才能發錢",
                metric(CashFlow.OPERATING, "營業金流(億)(季)", "億", GREATER_THAN, BigDecimal.ZERO, null));
    }

    private static HealthCheckCategoryDefinition debt() {
        return category("debt", "欠錢壓力", "負債總額佔比 ＜ 50%（含應付帳款，可另外檢視）",
                metric(Debt.TOTAL_LIABILITIES_RATIO, "負債總額佔比(季)", "%", LESS_THAN, bd(50), null),
                metric(Debt.CURRENT_RATIO, "流動資產對流動負債(季)", "%", GREATER_THAN, bd(100), "＞ 100%"),
                metric(Debt.QUICK_RATIO, "速動資產對流動負債(季)", "%", GREATER_THAN, bd(100), "＞ 100%"),
                metric(Debt.INTEREST_COVERAGE_RATIO, "利息保障倍數(季)", "倍", GREATER_THAN, bd(5),
                        "＝(稅前淨利＋利息費用)÷利息費用 ＞ 5倍"));
    }

    private static HealthCheckCategoryDefinition capital() {
        // DEC-077：改成只顯示「最新一季＋過去4個完整曆年Q4」共5個資料點，標記 isAnnual=true——
        // 最新一季若不是Q4會照樣顯示完整季度標籤，其餘4個Q4資料點只顯示年份。
        return category("capital", "股本膨脹率", "今股本與5年前股本成長 ＜ 20%",
                metric(Capital.EXPANSION_RATIO, "股本膨脹率(年)", "%", LESS_THAN, bd(20), null, true));
    }

    private static HealthCheckCategoryDefinition insider() {
        // DEC-046：原「董監+法人持股比(%)」（>33% 達標）改成「法人持股比季變化(%)」，
        // 衡量的是「這一季比上一季變化多少百分點」，跟原本的水位型門檻不是同一種東西，
        // 改為 JUDGMENT（純參考、不設門檻），使用者已確認採此做法。
        return category("insider", "內行人持有率", "董監持股比／法人持股比變化／董監質押",
                metric(Insider.DIRECTOR_SHAREHOLDING_RATIO, "董監持股比(%)", "%", JUDGMENT, null, null),
                metric(Insider.INSTITUTIONAL_HOLDING_QUARTERLY_CHANGE, "法人持股比季變化(%)", "%", JUDGMENT, null, null),
                metric(Insider.DIRECTOR_PLEDGE_RATIO, "全體董監質押(%)", "%", LESS_THAN, bd(33), "＜ 33%"));
    }

    private static HealthCheckCategoryDefinition payout() {
        return category("payout", "盈餘分配率", "看是否穩定",
                metric(Payout.TOTAL_PAYOUT_RATIO, "盈餘總分配率", "%", JUDGMENT, null, null, true, true));
    }

    private static HealthCheckCategoryDefinition margin() {
        return category("margin", "毛利率", "看是否穩定or攀升",
                metric(Margin.QUARTER, "毛利率(季)(%)", "%", JUDGMENT, null, null),
                metric(Margin.YEAR, "年毛利率(%)", "%", JUDGMENT, null, null, true));
    }

    private static HealthCheckCategoryDefinition operations() {
        return category("operations", "營業數據", "看是否穩定or攀升",
                metric(Operations.OPERATING_INCOME, "營業利益(億)(季)", "億", JUDGMENT, null, null),
                metric(Operations.NET_INCOME, "淨利(億)(季)", "億", JUDGMENT, null, null),
                metric(Operations.EPS_QUARTER, "EPS(季)", "元", JUDGMENT, null, null),
                metric(Operations.EPS_YEAR, "EPS(年)", "元", JUDGMENT, null, null, true),
                metric(Operations.ROE, "ROE(年)", "%", JUDGMENT, null, null, true));
    }

    /**
     * 銀行模板專用的營業數據大項：不含「營業利益」。銀行 FinancialStatements 完全沒有
     * {@code OperatingIncome} 這個 type——銀行的損益表結構是利息淨收益＋非利息淨收益－
     * 營業費用－呆帳費用，沒有一般產業「營業利益」這個科目，同樣不對銀行模板顯示（DEC-055）。
     */
    private static HealthCheckCategoryDefinition bankOperations() {
        return category("operations", "營業數據", "看是否穩定or攀升",
                metric(Operations.NET_INCOME, "淨利(億)(季)", "億", JUDGMENT, null, null),
                metric(Operations.EPS_QUARTER, "EPS(季)", "元", JUDGMENT, null, null),
                metric(Operations.EPS_YEAR, "EPS(年)", "元", JUDGMENT, null, null, true),
                metric(Operations.ROE, "ROE(年)", "%", JUDGMENT, null, null, true));
    }

    private static HealthCheckCategoryDefinition bankRegulatory() {
        return category("bankRegulatory", "銀行監理指標(季)", "資本適足率／逾期放款比率／備抵呆帳覆蓋率",
                metric(BankRegulatory.CAPITAL_ADEQUACY_RATIO, "資本適足率(%)(季)", "%", GREATER_THAN,
                        bd(10.5), "越高越好（法定下限10.5%）"),
                metric(BankRegulatory.OVERDUE_LOAN_RATIO, "逾期放款比率(%)(季)", "%", LESS_THAN, bd(3),
                        "越低越好（近期以＜2%較佳）"),
                metric(BankRegulatory.BAD_DEBT_COVERAGE_RATIO, "備抵呆帳覆蓋率(%)(季)", "%", GREATER_THAN,
                        bd(100), "越高越好（＞100%）"));
    }

    private static HealthCheckCategoryDefinition bankFlows() {
        return category("bankFlows", "籌碼變化(季)", "外資、三大法人買賣超與持股變化",
                metric(BankFlows.FOREIGN_NET_BUY_SELL_SHARES, "外資買賣超張數(季)", "張", JUDGMENT, null, null),
                metric(BankFlows.FOREIGN_SHAREHOLDING_RATIO, "外資持股(%)(季)", "%", JUDGMENT, null, null),
                metric(BankFlows.INSTITUTIONAL_NET_BUY_SELL_SHARES, "三大法人買賣超張數(季)", "張", JUDGMENT,
                        null, null));
    }

    /**
     * 月頻資料，{@code stock_health_snapshot.quarter} 例外存 {@code 'YYYY-MM'}
     * （見 {@link com.adamchung.stockanalysis.entity.StockHealthSnapshotEntity} 類別註解）；
     * 「近三年」＝36 個月，取代其餘大項預設的「近五季」。
     */
    private static HealthCheckCategoryDefinition bankRevenue() {
        return category("bankRevenue", "營收(月)", "近三年，每月", 36,
                metric(BankRevenue.MONTHLY_REVENUE, "月營收", "億", JUDGMENT, null, null),
                metric(BankRevenue.MOM_GROWTH, "單月營收月增(%)", "%", JUDGMENT, null, null),
                metric(BankRevenue.YOY_GROWTH, "單月營收年增(%)", "%", JUDGMENT, null, null));
    }

    private static final int DEFAULT_HISTORY_LIMIT = 5;

    private static HealthCheckCategoryDefinition category(
            String key, String name, String thresholdDescription, HealthCheckMetricDefinition... metrics) {
        return category(key, name, thresholdDescription, DEFAULT_HISTORY_LIMIT, metrics);
    }

    private static HealthCheckCategoryDefinition category(
            String key, String name, String thresholdDescription, int historyLimit,
            HealthCheckMetricDefinition... metrics) {
        return new HealthCheckCategoryDefinition(key, name, thresholdDescription, List.of(metrics), historyLimit);
    }

    private static HealthCheckMetricDefinition metric(
            String key, String name, String unit, HealthCheckEvalType evalType, BigDecimal threshold,
            String thresholdLabel) {
        return metric(key, name, unit, evalType, threshold, thresholdLabel, false);
    }

    /** {@code isAnnual=true}：這個細項是年頻資料，前端只顯示年份、不顯示「Q4」（DEC-066）。 */
    private static HealthCheckMetricDefinition metric(
            String key, String name, String unit, HealthCheckEvalType evalType, BigDecimal threshold,
            String thresholdLabel, boolean isAnnual) {
        return metric(key, name, unit, evalType, threshold, thresholdLabel, isAnnual, false);
    }

    /** {@code autofillNoteWithValue=true}：筆記欄位預設帶入已算好的數值，見欄位註解。 */
    private static HealthCheckMetricDefinition metric(
            String key, String name, String unit, HealthCheckEvalType evalType, BigDecimal threshold,
            String thresholdLabel, boolean isAnnual, boolean autofillNoteWithValue) {
        return new HealthCheckMetricDefinition(
                key, name, unit, evalType, threshold, thresholdLabel, isAnnual, autofillNoteWithValue);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
