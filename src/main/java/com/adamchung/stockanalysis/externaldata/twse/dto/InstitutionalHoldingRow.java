package com.adamchung.stockanalysis.externaldata.twse.dto;

/** 外資及陸資持股比率（精確值，TWSE MI_QFIIS 每日彙總）。 */
public record InstitutionalHoldingRow(
        String stockCode, // 股票代號
        String companyName, // 公司名稱
        /** {@code 'YYYYMMDD'}，實際回傳資料的交易日（可能因非交易日而往前挪）。 */
        String period,
        double foreignAndMainlandHoldingRatioPercent, // 外資及陸資持股比率(%)
        String sourceUrl // 資料來源網址
) {
}
