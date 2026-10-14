package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.common.PercentageMath;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyFinancialFacts;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * SA-04 一般模板／銀行模板共用大項的公式，純函式，不碰 DB／HTTP（比照 valuation 套件既有
 * 慣例）。輸入是 {@link QuarterlyFactsJoiner#join} 產出的、依季度新到舊排序的
 * {@link QuarterlyFinancialFacts} 清單；任何一筆缺某個欄位時，只有用到該欄位的公式會跳過
 * 那一期，不影響其他期或其他公式（見 {@link QuarterlyFinancialFacts} 類別註解）。
 *
 * <p>逐季類細項直接對每一期算一個值；「(年)」細項（{@code epsYear}／{@code marginYear}／
 * {@code operatingIncomeRatioYear}／{@code tenYearProfitable}／
 * {@code payout.totalPayoutRatio}）只在**完整四季**都有資料的曆年才計算，比照 SA-01 既有
 * 「10年統計不含當年度」的精神，結果固定存在該年 Q4 標籤下，維持所有細項都用同一套
 * {@code 'YYYYQ#'} 排序慣例。
 */
public final class HealthCheckFinancialMetricsCalculator {

    private static final BigDecimal HUNDRED_MILLION = BigDecimal.valueOf(100_000_000);
    private static final int SCALE = 4;
    private static final int CAPITAL_EXPANSION_LAG_YEARS = 5;
    private static final int CAPITAL_EXPANSION_DISPLAY_YEARS = 4;
    private static final int MAX_PROFITABLE_YEARS = 10;
    private static final int MAX_LOSS_YEARS_ALLOWED = 1;

    private HealthCheckFinancialMetricsCalculator() {
    }

    // ---- 逐季細項 ----

    /**
     * 盈餘再投資比率＝｜資本支出｜÷稅後淨利（DEC-032）。已對真實 API 驗證（1216 統一，
     * 2025Q4）：資本支出（`TaiwanStockCashFlowsStatement.PropertyAndPlantAndEquipment`，
     * 經 DEC-057 累計轉單季差分後）＝75.9億，稅後淨利＝49.6億，比率≈153%，數字量級合理
     * （SA-04 §4 建議的二次驗證，見 DEC-063）。
     */
    public static List<QuarterlyMetricValue> reinvestmentRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(abs(f.capex()), f.incomeAfterTaxes()));
    }

    public static List<QuarterlyMetricValue> operatingIncomeRatioQuarter(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(f.operatingIncome(), f.preTaxIncome()));
    }

    /**
     * 其他綜合損益(季)／稅後淨利。分母 {@code incomeAfterTaxes}（FinMind
     * {@code TaiwanStockFinancialStatements} 的 {@code IncomeAfterTaxes}，origin_name
     * 「本期淨利（淨損）」）取自公司對 TWSE 申報的財報，**是合併（集團）稅後淨利，不是母公司
     * 單獨稅後淨利**——台灣上市櫃公司對主管機關申報的財報預設就是合併報表（除非公司本身沒有
     * 子公司），FinMind 逐欄位擷取自這份申報資料，因此這裡沿用的就是合併口徑，沒有另外切換
     * 母公司單獨數字的管道（DEC-064，使用者確認此為現況、無需更動）。
     */
    public static List<QuarterlyMetricValue> otherComprehensiveIncomeRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(f.otherComprehensiveIncome(), f.incomeAfterTaxes()));
    }

    /**
     * 營收灌水比例(年)＝(應收帳款淨額＋存貨)÷年營收（DEC-065，取代 DEC-035）。應收/存貨取
     * 該年**年底（Q4）**資產負債表快照（不要求四季齊全，資產負債表本來就是時點存量，跟
     * {@link #roe} 的股東權益用法一致），年營收則是該年完整四季營收加總——使用者確認改回
     * 年度基準，理由是應收/存貨的年底數字本來就等於「該年度的總報表數值」，用年底快照對年
     * 營收，比原本 DEC-035 的「當季應收存貨對當季營收」更貼近「灌水」這個概念原本要抓的
     * 「全年營收灌水程度」，而不是單一季度的短期波動。
     */
    public static List<QuarterlyMetricValue> revenueInflationRatio(List<QuarterlyFinancialFacts> facts) {
        return joinYearRatios(
                yearEndValues(facts, f -> (f.accountsReceivableNet() == null || f.inventories() == null)
                        ? null : f.accountsReceivableNet().add(f.inventories())),
                sumCompleteYears(facts, QuarterlyFinancialFacts::revenue));
    }

    public static List<QuarterlyMetricValue> cashFlowOperating(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> toHundredMillion(f.operatingCashFlow()));
    }

    public static List<QuarterlyMetricValue> cashFlowFree(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> {
            if (f.operatingCashFlow() == null || f.capex() == null) {
                return null;
            }
            return toHundredMillion(f.operatingCashFlow().subtract(abs(f.capex())));
        });
    }

    public static List<QuarterlyMetricValue> totalLiabilitiesRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(f.liabilities(), f.totalAssets()));
    }

    public static List<QuarterlyMetricValue> currentRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(f.currentAssets(), f.currentLiabilities()));
    }

    public static List<QuarterlyMetricValue> quickRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> {
            if (f.currentAssets() == null || f.inventories() == null) {
                return null;
            }
            return percentRatio(f.currentAssets().subtract(f.inventories()), f.currentLiabilities());
        });
    }

    /** EBIT 代理公式＝稅前淨利＋利息費用（DEC-037）；利息保障倍數＝EBIT÷利息費用。 */
    public static List<QuarterlyMetricValue> interestCoverageRatio(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> {
            if (f.preTaxIncome() == null || f.interestExpense() == null) {
                return null;
            }
            BigDecimal ebit = f.preTaxIncome().add(f.interestExpense());
            return divide(ebit, f.interestExpense());
        });
    }

    public static List<QuarterlyMetricValue> operatingIncomeBillion(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> toHundredMillion(f.operatingIncome()));
    }

    public static List<QuarterlyMetricValue> netIncomeBillion(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> toHundredMillion(f.incomeAfterTaxes()));
    }

    public static List<QuarterlyMetricValue> epsQuarter(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, QuarterlyFinancialFacts::epsQuarter);
    }

    public static List<QuarterlyMetricValue> marginQuarter(List<QuarterlyFinancialFacts> facts) {
        return mapEach(facts, f -> percentRatio(f.grossProfit(), f.revenue()));
    }

    /**
     * ROE(年)＝完整曆年稅後淨利加總 ÷ 該年年底（Q4）股東權益（DEC-059）。原本誤實作成逐季
     * （單季稅後淨利÷當季股東權益），跟欄位標示的「(年)」不符、數字也會小到不合理——股東權益
     * 是資產負債表的存量數字，不需要像損益表科目一樣要求四季齊全，只要有 Q4 快照就能算。
     */
    public static List<QuarterlyMetricValue> roe(List<QuarterlyFinancialFacts> facts) {
        return joinYearRatios(
                sumCompleteYears(facts, QuarterlyFinancialFacts::incomeAfterTaxes),
                yearEndValues(facts, QuarterlyFinancialFacts::equity));
    }

    /** 該年 Q4（年底）快照值，供資產負債表存量科目的年度指標使用（不要求四季齊全）。 */
    private static Map<Integer, BigDecimal> yearEndValues(
            List<QuarterlyFinancialFacts> facts, Function<QuarterlyFinancialFacts, BigDecimal> fieldFn) {
        Map<Integer, BigDecimal> result = new TreeMap<>();
        for (QuarterlyFinancialFacts fact : facts) {
            if (!fact.quarter().endsWith("Q4")) {
                continue;
            }
            BigDecimal value = fieldFn.apply(fact);
            if (value != null) {
                result.put(Integer.parseInt(fact.quarter().substring(0, 4)), value);
            }
        }
        return result;
    }

    /**
     * 股本膨脹率(年)＝(本期股本－5年前同季股本)÷5年前同季股本。畫面只顯示5個資料點：
     * 目前有資料的最新一季（不論是不是Q4）＋過去4個完整曆年的Q4（DEC-077，取代原本逐季
     * 輸出全部季度的版本）——例如最新一季是2026Q2，畫面顯示
     * 2026Q2、2025Q4、2024Q4、2023Q4、2022Q4，各自跟自己5年前的同一季比較。這樣「近5年」
     * 的視覺呈現裡永遠包含最新可用資料，而不是像純年度細項（{@link #roe}）那樣只看得到
     * 到去年為止的完整年度數字；已標記 {@code isAnnual=true}，前端對結尾為「Q4」的期間只顯示
     * 年份，最新一季若剛好不是Q4則照樣顯示完整季度標籤。
     */
    public static List<QuarterlyMetricValue> capitalExpansionRatio(List<QuarterlyFinancialFacts> facts) {
        Map<String, QuarterlyFinancialFacts> byQuarter = new HashMap<>();
        for (QuarterlyFinancialFacts fact : facts) {
            byQuarter.put(fact.quarter(), fact);
        }
        String latestQuarter = facts.stream().map(QuarterlyFinancialFacts::quarter).max(String::compareTo).orElse(null);
        if (latestQuarter == null) {
            return List.of();
        }
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (String quarter : capitalExpansionTargetQuarters(latestQuarter)) {
            QuarterlyFinancialFacts current = byQuarter.get(quarter);
            // 本期股本缺資料就沒有比較基準，這一期直接跳過
            if (current == null || current.capitalStock() == null) {
                continue;
            }
            QuarterlyFinancialFacts lagged = byQuarter.get(fiveYearsBeforeLabel(quarter));
            // 5年前同季股本缺資料就無法算出膨脹率的分母，這一期也跳過
            if (lagged == null || lagged.capitalStock() == null) {
                continue;
            }
            // 股本膨脹率＝(本期股本－5年前同季股本)÷5年前同季股本
            BigDecimal ratio = percentRatio(current.capitalStock().subtract(lagged.capitalStock()), lagged.capitalStock());
            if (ratio != null) {
                results.add(new QuarterlyMetricValue(quarter, ratio));
            }
        }
        return results;
    }

    /** 最新一季本身＋過去4個完整曆年的Q4，新到舊排序（DEC-077）。 */
    private static List<String> capitalExpansionTargetQuarters(String latestQuarter) {
        int latestYear = Integer.parseInt(latestQuarter.substring(0, 4));
        List<String> quarters = new ArrayList<>();
        quarters.add(latestQuarter);
        for (int i = 1; i <= CAPITAL_EXPANSION_DISPLAY_YEARS; i++) {
            quarters.add((latestYear - i) + "Q4");
        }
        return quarters;
    }

    private static String fiveYearsBeforeLabel(String quarterLabel) {
        int year = Integer.parseInt(quarterLabel.substring(0, 4));
        String quarterSuffix = quarterLabel.substring(4);
        return (year - CAPITAL_EXPANSION_LAG_YEARS) + quarterSuffix;
    }

    // ---- 年頻細項：只在完整四季都有資料的曆年計算，固定存該年 Q4 ----

    public static List<QuarterlyMetricValue> tenYearProfitable(List<QuarterlyFinancialFacts> facts) {
        Map<Integer, BigDecimal> annualIncome = sumCompleteYears(facts, QuarterlyFinancialFacts::incomeAfterTaxes);
        if (annualIncome.isEmpty()) {
            return List.of();
        }
        List<Integer> years = new ArrayList<>(annualIncome.keySet());
        years.sort(java.util.Collections.reverseOrder());
        List<Integer> recentYears = years.subList(0, Math.min(MAX_PROFITABLE_YEARS, years.size()));
        long lossYears = recentYears.stream().filter(year -> annualIncome.get(year).signum() <= 0).count();
        boolean profitable = lossYears <= MAX_LOSS_YEARS_ALLOWED;
        int latestYear = recentYears.get(0);
        return List.of(new QuarterlyMetricValue(latestYear + "Q4", profitable ? BigDecimal.ONE : BigDecimal.ZERO));
    }

    public static List<QuarterlyMetricValue> operatingIncomeRatioYear(List<QuarterlyFinancialFacts> facts) {
        return joinYearRatios(
                sumCompleteYears(facts, QuarterlyFinancialFacts::operatingIncome),
                sumCompleteYears(facts, QuarterlyFinancialFacts::preTaxIncome));
    }

    public static List<QuarterlyMetricValue> marginYear(List<QuarterlyFinancialFacts> facts) {
        return joinYearRatios(
                sumCompleteYears(facts, QuarterlyFinancialFacts::grossProfit),
                sumCompleteYears(facts, QuarterlyFinancialFacts::revenue));
    }

    public static List<QuarterlyMetricValue> epsYear(List<QuarterlyFinancialFacts> facts) {
        Map<Integer, BigDecimal> annualEps = sumCompleteYears(facts, QuarterlyFinancialFacts::epsQuarter);
        return toDescendingQ4Values(annualEps);
    }

    /**
     * 盈餘總分配率＝(現金股利(元)＋股票股利(元))÷EPS(元)，依股利對應的**獲利年度**
     * （DEC-033／DEC-058）配對同一年度的 EPS(年)——不是股利除息交易日的西元年。台股慣例是
     * 「N年度獲利，N+1年除息發放」（已對真實 API 驗證：2884 除息日 2026-07-01 的股利，`year`
     * 欄位標示「114年」＝ROC 114＝西元 2025，對應的正是 2025 年度獲利，不是 2026 年度），所以
     * 這裡用「除息日西元年－1」當作獲利年度，刻意**不**沿用 `sync.DividendYearResolver`
     * （那是為了目標價殖利率計算設計的「除息日西元年＝股利歸屬年」慣例，兩者語意不同、服務的
     * 指標概念也不同，硬共用會讓兩邊都變得難懂）。使用者確認改成現金＋股票股利合計（原本只算
     * 現金股利，漏了以股票形式發放的部分）。
     *
     * <p>DEC-074：每一期額外附上公式明細筆記（例如「現金股利1.5+股票股利0.5 / EPS2 = 100%」），
     * 供 {@link FinMindHealthCheckSyncService} 在使用者還沒存過筆記時自動帶入，省得使用者自己
     * 回頭查現金股利／股票股利／EPS 分別是多少才能核對這個百分比怎麼算出來的。
     */
    public static List<QuarterlyMetricValue> totalPayoutRatio(
            List<QuarterlyFinancialFacts> facts, List<FinMindDividendDto> dividends) {
        Map<Integer, BigDecimal> annualEps = sumCompleteYears(facts, QuarterlyFinancialFacts::epsQuarter);
        Map<Integer, BigDecimal> cashDividendByYear = new TreeMap<>();
        Map<Integer, BigDecimal> stockDividendByYear = new TreeMap<>();
        // 步驟1：依股利歸屬的獲利年度，分別把現金股利、股票股利各自加總成年度地圖
        for (FinMindDividendDto dividend : dividends) {
            BigDecimal cash = dividend.cashEarningsDistribution();
            BigDecimal stock = dividend.stockEarningsDistribution();
            if (cash == null && stock == null) {
                continue;
            }
            Integer earningsYear = resolveEarningsYear(dividend);
            if (earningsYear == null) {
                continue;
            }
            if (cash != null) {
                cashDividendByYear.merge(earningsYear, cash, BigDecimal::add);
            }
            if (stock != null) {
                stockDividendByYear.merge(earningsYear, stock, BigDecimal::add);
            }
        }
        // 步驟2：聯集現金／股票兩份年度地圖的年份，逐年合計成「現金＋股票」總股利
        Map<Integer, BigDecimal> totalDividendByYear = new TreeMap<>();
        for (Integer year : union(cashDividendByYear.keySet(), stockDividendByYear.keySet())) {
            BigDecimal cash = cashDividendByYear.getOrDefault(year, BigDecimal.ZERO);
            BigDecimal stock = stockDividendByYear.getOrDefault(year, BigDecimal.ZERO);
            totalDividendByYear.put(year, cash.add(stock));
        }

        // 步驟3：總股利對 EPS(年) 逐年配對，算出盈餘總分配率
        List<QuarterlyMetricValue> ratios = joinYearRatios(totalDividendByYear, annualEps);
        // 步驟4：每個年度附上「現金X+股票Y / EPSZ = 比率%」的公式明細筆記，供前端自動帶入
        return ratios.stream()
                .map(ratio -> {
                    int year = Integer.parseInt(ratio.period().substring(0, 4));
                    String note = payoutRatioBreakdownNote(
                            cashDividendByYear.getOrDefault(year, BigDecimal.ZERO),
                            stockDividendByYear.getOrDefault(year, BigDecimal.ZERO),
                            annualEps.get(year),
                            ratio.value());
                    return new QuarterlyMetricValue(ratio.period(), ratio.value(), note);
                })
                .toList();
    }

    private static TreeSet<Integer> union(Set<Integer> a, Set<Integer> b) {
        TreeSet<Integer> result = new TreeSet<>(a);
        result.addAll(b);
        return result;
    }

    /** 例如「現金股利1.5+股票股利0.5 / EPS2 = 100%」。 */
    private static String payoutRatioBreakdownNote(
            BigDecimal cash, BigDecimal stock, BigDecimal eps, BigDecimal ratio) {
        return "現金股利%s+股票股利%s / EPS%s = %s%%".formatted(
                plainString(cash), plainString(stock), plainString(eps), plainString(ratio.setScale(1, RoundingMode.HALF_UP)));
    }

    private static String plainString(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** 股利對應的獲利年度＝除息交易日（缺值時退回公告日）西元年－1。 */
    private static Integer resolveEarningsYear(FinMindDividendDto dividend) {
        if (dividend.cashExDividendTradingDate() != null) {
            return dividend.cashExDividendTradingDate().getYear() - 1;
        }
        if (dividend.announcementDate() != null) {
            return dividend.announcementDate().getYear() - 1;
        }
        return null;
    }

    // ---- helpers ----

    private static List<QuarterlyMetricValue> mapEach(
            List<QuarterlyFinancialFacts> facts, Function<QuarterlyFinancialFacts, BigDecimal> fieldFn) {
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (QuarterlyFinancialFacts fact : facts) {
            BigDecimal value = fieldFn.apply(fact);
            if (value != null) {
                results.add(new QuarterlyMetricValue(fact.quarter(), value));
            }
        }
        return results;
    }

    /** 只保留該曆年四季皆有值的年度，其餘（含當年度尚未結束、或缺季）直接排除。 */
    private static Map<Integer, BigDecimal> sumCompleteYears(
            List<QuarterlyFinancialFacts> facts, Function<QuarterlyFinancialFacts, BigDecimal> fieldFn) {
        Map<Integer, List<BigDecimal>> byYear = new TreeMap<>();
        for (QuarterlyFinancialFacts fact : facts) {
            int year = Integer.parseInt(fact.quarter().substring(0, 4));
            byYear.computeIfAbsent(year, key -> new ArrayList<>()).add(fieldFn.apply(fact));
        }
        Map<Integer, BigDecimal> sums = new TreeMap<>();
        for (Map.Entry<Integer, List<BigDecimal>> entry : byYear.entrySet()) {
            List<BigDecimal> values = entry.getValue();
            // 必須剛好有4個非null的季度值才算完整曆年，否則整年直接排除，不做部分加總
            if (values.size() != 4 || values.stream().anyMatch(Objects::isNull)) {
                continue;
            }
            sums.put(entry.getKey(), values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return sums;
    }

    private static List<QuarterlyMetricValue> joinYearRatios(
            Map<Integer, BigDecimal> numerator, Map<Integer, BigDecimal> denominator) {
        Map<Integer, BigDecimal> ratios = new TreeMap<>();
        for (Map.Entry<Integer, BigDecimal> entry : numerator.entrySet()) {
            BigDecimal ratio = percentRatio(entry.getValue(), denominator.get(entry.getKey()));
            if (ratio != null) {
                ratios.put(entry.getKey(), ratio);
            }
        }
        return toDescendingQ4Values(ratios);
    }

    private static List<QuarterlyMetricValue> toDescendingQ4Values(Map<Integer, BigDecimal> byYear) {
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : byYear.entrySet()) {
            results.add(new QuarterlyMetricValue(entry.getKey() + "Q4", entry.getValue()));
        }
        results.sort((a, b) -> b.period().compareTo(a.period()));
        return results;
    }

    private static BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentRatio(BigDecimal numerator, BigDecimal denominator) {
        return PercentageMath.ratioAsPercent(numerator, denominator, SCALE);
    }

    private static BigDecimal toHundredMillion(BigDecimal value) {
        return value == null ? null : value.divide(HUNDRED_MILLION, SCALE, RoundingMode.HALF_UP);
    }
}
