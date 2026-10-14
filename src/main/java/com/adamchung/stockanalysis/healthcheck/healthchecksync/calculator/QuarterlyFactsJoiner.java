package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyFinancialFacts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 把 TaiwanStockFinancialStatements／TaiwanStockBalanceSheet／TaiwanStockCashFlowsStatement
 * 三份逐季 EAV 資料，依 {@code 'YYYYQ#'} 標籤合併成 {@link QuarterlyFinancialFacts} 清單
 * （純函式，不碰 DB／HTTP，比照 valuation 套件既有慣例）。
 *
 * <p>已對真實 API 驗證兩處一般模板／銀行模板欄位命名差異（見 {@link QuarterlyFinancialFacts}
 * 類別註解）：銀行模板的稅後淨利 type 是單數 {@code IncomeAfterTax}，股本欄位是
 * {@code OrdinaryShare}——這裡對這兩個欄位各自嘗試一般模板／銀行模板的 type 名稱，取第一個
 * 有值的來源，讓 {@code profit}／{@code capital} 這類兩種模板共用的大項在銀行股身上也能正確
 * 抓到資料，而不是因為套用了錯的 type 名稱而永遠是 null。
 *
 * <p><b>DEC-057：TaiwanStockCashFlowsStatement 是曆年累計（YTD），每年 Q1 重新歸零，不是
 * 單季數字</b>——跟 {@code TaiwanStockFinancialStatements}（已驗證是單季，SA-01 DEC-15）
 * 完全不同的慣例。已對真實 API 驗證（2884，2025年）：{@code NetCashInflowFromOperatingActivities}
 * 逐季原始值 23.4億→2.97億→21.25億→36.56億 呈現單調遞增的年度累計走勢，真正的單季 Q4
 * 應為 36.56－21.25＝15.32億（換算後與使用者回報的正確值 15.32億／153.2百萬完全吻合）。
 * {@link #decodeCumulativeToQuarterly} 對三個受影響的 type（營業金流／資本支出／利息費用）
 * 做年度內逐季差分，Q1 沿用原值（累計重置＝單季本身）。
 */
public final class QuarterlyFactsJoiner {

    private static final List<String> INCOME_AFTER_TAXES_TYPES = List.of("IncomeAfterTaxes", "IncomeAfterTax");
    private static final List<String> CAPITAL_STOCK_TYPES = List.of("CapitalStock", "OrdinaryShare");
    private static final List<String> CASH_FLOW_CUMULATIVE_TYPES = List.of(
            "NetCashInflowFromOperatingActivities", "PropertyAndPlantAndEquipment", "InterestExpense");

    private QuarterlyFactsJoiner() {
    }

    /** 依日期算出 {@code 'YYYYQ#'} 標籤，跟 {@code externaldata.QuarterRanges} 的邏輯一致。 */
    public static String quarterLabel(LocalDate date) {
        int quarterNumber = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + "Q" + quarterNumber;
    }

    public static List<QuarterlyFinancialFacts> join(
            List<FinMindFinancialStatementDto> financialStatements,
            List<FinMindBalanceSheetDto> balanceSheet,
            List<FinMindCashFlowsStatementDto> cashFlowsStatement) {

        Map<String, Map<String, BigDecimal>> byQuarter = new HashMap<>();
        for (FinMindFinancialStatementDto row : financialStatements) {
            put(byQuarter, row.date(), row.type(), row.value());
        }
        for (FinMindBalanceSheetDto row : balanceSheet) {
            put(byQuarter, row.date(), row.type(), row.value());
        }

        Map<String, Map<String, BigDecimal>> rawCumulativeCashFlow = new HashMap<>();
        for (FinMindCashFlowsStatementDto row : cashFlowsStatement) {
            put(rawCumulativeCashFlow, row.date(), row.type(), row.value());
        }
        for (String type : CASH_FLOW_CUMULATIVE_TYPES) {
            for (Map.Entry<String, BigDecimal> entry : decodeCumulativeToQuarterly(rawCumulativeCashFlow, type).entrySet()) {
                byQuarter.computeIfAbsent(entry.getKey(), key -> new HashMap<>()).put(type, entry.getValue());
            }
        }

        List<QuarterlyFinancialFacts> facts = new ArrayList<>();
        for (String quarter : new TreeSet<>(byQuarter.keySet()).descendingSet()) {
            Map<String, BigDecimal> types = byQuarter.get(quarter);
            facts.add(new QuarterlyFinancialFacts(
                    quarter,
                    types.get("Revenue"),
                    types.get("EPS"),
                    types.get("OperatingIncome"),
                    types.get("PreTaxIncome"),
                    firstNonNull(types, INCOME_AFTER_TAXES_TYPES),
                    types.get("OtherComprehensiveIncome"),
                    types.get("GrossProfit"),
                    types.get("AccountsReceivableNet"),
                    types.get("Inventories"),
                    types.get("Liabilities"),
                    types.get("TotalAssets"),
                    types.get("CurrentAssets"),
                    types.get("CurrentLiabilities"),
                    firstNonNull(types, CAPITAL_STOCK_TYPES),
                    types.get("Equity"),
                    types.get("NetCashInflowFromOperatingActivities"),
                    types.get("PropertyAndPlantAndEquipment"),
                    types.get("InterestExpense")));
        }
        return facts;
    }

    private static void put(Map<String, Map<String, BigDecimal>> byQuarter, LocalDate date, String type, BigDecimal value) {
        if (date == null || type == null || value == null) {
            return;
        }
        byQuarter.computeIfAbsent(quarterLabel(date), key -> new HashMap<>()).put(type, value);
    }

    /**
     * 把單一 type 的年度累計原始值換算成單季差分：依年份分組、年份內依季度排序，Q1 沿用原值
     * （累計重置＝單季本身），其餘季度＝本季累計－上一季累計。上一季缺值時該季無法可靠換算，
     * 直接跳過（不落地、不猜測），比連續內插或用0代替更安全。
     */
    private static Map<String, BigDecimal> decodeCumulativeToQuarterly(
            Map<String, Map<String, BigDecimal>> rawByQuarter, String type) {
        Map<Integer, Map<Integer, BigDecimal>> byYearThenQuarterNum = new TreeMap<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : rawByQuarter.entrySet()) {
            BigDecimal value = entry.getValue().get(type);
            if (value == null) {
                continue;
            }
            String quarterLabel = entry.getKey();
            int year = Integer.parseInt(quarterLabel.substring(0, 4));
            int quarterNum = Integer.parseInt(quarterLabel.substring(5));
            byYearThenQuarterNum.computeIfAbsent(year, key -> new TreeMap<>()).put(quarterNum, value);
        }

        Map<String, BigDecimal> result = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, BigDecimal>> yearEntry : byYearThenQuarterNum.entrySet()) {
            int year = yearEntry.getKey();
            BigDecimal previousCumulative = null;
            int previousQuarterNum = 0;
            for (Map.Entry<Integer, BigDecimal> quarterEntry : yearEntry.getValue().entrySet()) {
                int quarterNum = quarterEntry.getKey();
                BigDecimal cumulative = quarterEntry.getValue();
                if (quarterNum == 1) {
                    // 對應規則「Q1 沿用原值」：年度累計在Q1重置，累計值本身就是單季值
                    result.put(year + "Q1", cumulative);
                } else if (previousQuarterNum == quarterNum - 1) {
                    // 對應規則「Qn＝累計(n)－累計(n-1)」：只有上一季緊接在前才能可靠差分
                    result.put(year + "Q" + quarterNum, cumulative.subtract(previousCumulative));
                }
                // 上一季缺值（previousQuarterNum 不等於 quarterNum-1）時，前面條件都不成立，
                // 直接跳過不落地，對應規則「上一季缺值時該季無法可靠換算」
                previousCumulative = cumulative;
                previousQuarterNum = quarterNum;
            }
        }
        return result;
    }

    private static BigDecimal firstNonNull(Map<String, BigDecimal> types, List<String> candidateTypeNames) {
        for (String candidate : candidateTypeNames) {
            BigDecimal value = types.get(candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
