package com.adamchung.stockanalysis.valuation.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 依台股「還原殖利率」慣例，把股票股利的稀釋效果算進除權息參考價後，換算出年度殖利率——
 * 取代直接採用 FinMind TaiwanStockPER.dividend_yield 的做法（不再信任該欄位，見
 * docs/agent/DECISIONS.md 對應決策）。公式：
 *
 * <pre>
 * 除權息參考價 = (均價 − 現金股利) ÷ (1 + 股票股利 ÷ 10)     // 股票股利/10 = 無償配股率（面額10元）
 * 年度總股利   = 均價 − 除權息參考價
 * 年度殖利率   = 年度總股利 ÷ 均價
 * </pre>
 *
 * 均價使用該年度已經算好的年度均價（stock_price_yearly），不是除權息前一交易日實際收盤價——
 * 跟系統其餘統計一致，只用年度顆粒度的資料，是刻意的簡化。同一年度若配息（配股）多次，
 * 呼叫端傳入的現金/股票股利應該是該年度累積到最後一筆的合計（見
 * {@link com.adamchung.stockanalysis.entity.StockDividendEntity} 的
 * {@code cumulativeCashDividend}/{@code cumulativeStockDividend}），這裡只套一次公式，
 * 不逐筆各自除權息複利。
 */
public final class DividendYieldCalculator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal PAR_VALUE = BigDecimal.TEN;

    private DividendYieldCalculator() {
    }

    /**
     * 計算單一年度的還原殖利率。
     *
     * @param avgClosePrice 該年度均價，缺值或非正值時無法計算
     * @param cashDividend 該年度累積現金股利合計，可為 {@code null}（視為 0）
     * @param stockDividend 該年度累積股票股利合計，可為 {@code null}（視為 0）
     * @return 該年度殖利率；均價缺值或非正值時回傳 {@code null}
     */
    public static BigDecimal calculate(BigDecimal avgClosePrice, BigDecimal cashDividend, BigDecimal stockDividend) {
        if (avgClosePrice == null || avgClosePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal cash = cashDividend == null ? BigDecimal.ZERO : cashDividend;
        BigDecimal stock = stockDividend == null ? BigDecimal.ZERO : stockDividend;
        BigDecimal stockDividendRate = stock.divide(PAR_VALUE, SCALE, ROUNDING);

        BigDecimal exRightsReferencePrice = avgClosePrice.subtract(cash)
                .divide(BigDecimal.ONE.add(stockDividendRate), SCALE, ROUNDING);
        BigDecimal totalDividendValue = avgClosePrice.subtract(exRightsReferencePrice);
        return totalDividendValue.divide(avgClosePrice, SCALE, ROUNDING);
    }
}
