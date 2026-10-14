package com.adamchung.stockanalysis.externaldata.twse.dto;

/**
 * 投信／自營商當季買賣超股數換算的持股比率變動（估算值，非精確持股水位——官方沒有公布這兩類
 * 法人的累計持股比率，見 {@link TwseInstitutionalFlowClient}）。
 */
public record InstitutionalFlowRow(
        String stockCode, // 股票代號
        String companyName, // 公司名稱
        /** {@code 'YYYYQ#'}。 */
        String period,
        double investmentTrustNetBuyRatioChangePercent, // 投信淨買超比率變動(%)
        double dealerNetBuyRatioChangePercent, // 自營商淨買超比率變動(%)
        String sourceUrl // 資料來源網址
) {
}
