package com.adamchung.stockanalysis.externaldata.twse.dto;

/**
 * 董監持股比／全體董監質押比彙總（僅最新一期，TWSE 官方開放資料不提供歷史，見
 * {@link TwseOpenDataClient}）。
 */
public record DirectorHoldingsRow(
        String stockCode, // 股票代號
        String companyName, // 公司名稱
        double directorShareholdingPercent, // 董監持股比率(%)
        double directorPledgeRatioPercent, // 董監質押比率(%)
        /** {@code 'YYYY-MM'}，TWSE 申報資料年月。 */
        String dataDate,
        String sourceUrl // 資料來源網址
) {
}
