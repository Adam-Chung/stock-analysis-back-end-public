package com.adamchung.stockanalysis.common;

/**
 * 企業體質檢視細項的 {@code metric_key} 常數，對應
 * {@code stock_health_snapshot.metric_key} 與
 * {@link com.adamchung.stockanalysis.healthcheck.definition.HealthCheckTemplateDefinitions}
 * 裡各細項的 {@code key}。格式為 {@code 大項.細項}，必須在各自模板內全域唯一（DEC-039）。
 */
public final class HealthCheckMetricKeys {

    private HealthCheckMetricKeys() {
    }

    public static final class Profit {

        private Profit() {
        }

        /**
         * 近10年是否年年賺錢
         */
        public static final String TEN_YEAR_PROFITABLE = "profit.tenYearProfitable";
    }

    public static final class Reinvest {

        private Reinvest() {
        }

        /**
         * 盈餘再投資比率
         */
        public static final String REINVESTMENT_RATIO = "reinvest.reinvestmentRatio";
    }

    public static final class Core {

        private Core() {
        }

        /**
         * 本業收入比（季）
         */
        public static final String OPERATING_INCOME_RATIO_QUARTER = "core.operatingIncomeRatioQuarter";
        /**
         * 本業收入比（年）
         */
        public static final String OPERATING_INCOME_RATIO_YEAR = "core.operatingIncomeRatioYear";
    }

    public static final class NonOperating {

        private NonOperating() {
        }

        /**
         * 其他綜合損益占稅後淨利比率
         */
        public static final String OTHER_COMPREHENSIVE_INCOME_RATIO = "nonOperating.otherComprehensiveIncomeRatio";
    }

    public static final class RevenueInflation {

        private RevenueInflation() {
        }

        /**
         * 營收灌水比例
         */
        public static final String RATIO = "revenueInflation.ratio";
    }

    public static final class CashFlow {

        private CashFlow() {
        }

        /**
         * 營業現金流量
         */
        public static final String OPERATING = "cashFlow.operating";
        /**
         * 自由現金流量
         */
        public static final String FREE = "cashFlow.free";
    }

    public static final class Debt {

        private Debt() {
        }

        /**
         * 負債總額佔比
         */
        public static final String TOTAL_LIABILITIES_RATIO = "debt.totalLiabilitiesRatio";
        /**
         * 流動資產對流動負債
         */
        public static final String CURRENT_RATIO = "debt.currentRatio";
        /**
         * 速動資產對流動負債
         */
        public static final String QUICK_RATIO = "debt.quickRatio";
        /**
         * 利息保障倍數
         */
        public static final String INTEREST_COVERAGE_RATIO = "debt.interestCoverageRatio";
    }

    public static final class Capital {

        private Capital() {
        }

        /**
         * 股本膨脹率
         */
        public static final String EXPANSION_RATIO = "capital.expansionRatio";
    }

    public static final class Insider {

        private Insider() {
        }

        /**
         * 董監持股比
         */
        public static final String DIRECTOR_SHAREHOLDING_RATIO = "insider.directorShareholdingRatio";
        /**
         * 法人持股比季變化
         */
        public static final String INSTITUTIONAL_HOLDING_QUARTERLY_CHANGE = "insider.institutionalHoldingQuarterlyChange";
        /**
         * 全體董監質押
         */
        public static final String DIRECTOR_PLEDGE_RATIO = "insider.directorPledgeRatio";
    }

    public static final class Payout {

        private Payout() {
        }

        /**
         * 盈餘總分配率
         */
        public static final String TOTAL_PAYOUT_RATIO = "payout.totalPayoutRatio";
    }

    public static final class Margin {

        private Margin() {
        }

        /**
         * 毛利率（季）
         */
        public static final String QUARTER = "margin.quarter";
        /**
         * 年毛利率
         */
        public static final String YEAR = "margin.year";
    }

    public static final class Operations {

        private Operations() {
        }

        /**
         * 營業利益
         */
        public static final String OPERATING_INCOME = "operations.operatingIncome";
        /**
         * 淨利
         */
        public static final String NET_INCOME = "operations.netIncome";
        /**
         * EPS（季）
         */
        public static final String EPS_QUARTER = "operations.epsQuarter";
        /**
         * EPS（年）
         */
        public static final String EPS_YEAR = "operations.epsYear";
        /**
         * 股東權益報酬率
         */
        public static final String ROE = "operations.roe";
    }

    public static final class BankRegulatory {

        private BankRegulatory() {
        }

        /**
         * 資本適足率
         */
        public static final String CAPITAL_ADEQUACY_RATIO = "bankRegulatory.capitalAdequacyRatio";
        /**
         * 逾期放款比率
         */
        public static final String OVERDUE_LOAN_RATIO = "bankRegulatory.overdueLoanRatio";
        /**
         * 備抵呆帳覆蓋率
         */
        public static final String BAD_DEBT_COVERAGE_RATIO = "bankRegulatory.badDebtCoverageRatio";
    }

    public static final class BankFlows {

        private BankFlows() {
        }

        /**
         * 外資買賣超張數
         */
        public static final String FOREIGN_NET_BUY_SELL_SHARES = "bankFlows.foreignNetBuySellShares";
        /**
         * 外資持股比
         */
        public static final String FOREIGN_SHAREHOLDING_RATIO = "bankFlows.foreignShareholdingRatio";
        /**
         * 三大法人買賣超張數
         */
        public static final String INSTITUTIONAL_NET_BUY_SELL_SHARES = "bankFlows.institutionalNetBuySellShares";
    }

    public static final class BankRevenue {

        private BankRevenue() {
        }

        /**
         * 月營收
         */
        public static final String MONTHLY_REVENUE = "bankRevenue.monthlyRevenue";
        /**
         * 單月營收月增率
         */
        public static final String MOM_GROWTH = "bankRevenue.momGrowth";
        /**
         * 單月營收年增率
         */
        public static final String YOY_GROWTH = "bankRevenue.yoyGrowth";
    }
}
