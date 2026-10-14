package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;

/**
 * 從 FinMind 股利資料列解析出 stock_dividend.year。FinMind 原始的 "year" 欄位是民國年+季度的
 * 文字標籤，無法直接使用（SA-01 DEC-005／{@link com.adamchung.stockanalysis.entity.StockDividendEntity}
 * 的 Javadoc）。以現金股利除息交易日的西元年為主，該期間沒有現金股利時（純股票股利年度）
 * 退回公告日的西元年。
 */
public final class DividendYearResolver {

    private DividendYearResolver() {
    }

    /**
     * 解析股利年度。
     *
     * @param dividend FinMind 股利資料列
     * @return 解析出的西元年度；兩個日期都缺值時回傳 {@code null}
     */
    public static Integer resolve(FinMindDividendDto dividend) {
        if (dividend.cashExDividendTradingDate() != null) {
            return dividend.cashExDividendTradingDate().getYear();
        }
        if (dividend.announcementDate() != null) {
            return dividend.announcementDate().getYear();
        }
        return null;
    }
}
