package com.adamchung.stockanalysis.externaldata.twse;

/**
 * 三大法人（外資及陸資／投信／自營商）當季持股比率變動的合成結果，對應
 * SA-04「法人持股比季變化(%)」（DEC-046，取代原「董監+法人持股比(%)」）。
 */
public record InstitutionalQuarterlyChangeRow(
        String stockCode, // 股票代號
        String companyName, // 公司名稱
        /** {@code 'YYYYQ#'}。 */
        String period,
        /** 外資及陸資：期末持股水位相減得出的精確季變動（非估算）。 */
        double foreignAndMainlandHoldingChangePercent,
        /** 投信：以當季買賣超股數 ÷ 發行股數估算的季變動。 */
        double investmentTrustNetBuyRatioChangePercent,
        /** 自營商：以當季買賣超股數 ÷ 發行股數估算的季變動。 */
        double dealerNetBuyRatioChangePercent,
        String foreignHoldingSourceUrl, // 外資持股資料來源網址
        String institutionalFlowSourceUrl // 法人買賣超資料來源網址
) {
    /** 三個組成部分加總的合計值，即實際寫入 stock_health_snapshot 的數字。 */
    public double totalChangePercent() {
        return foreignAndMainlandHoldingChangePercent
                + investmentTrustNetBuyRatioChangePercent
                + dealerNetBuyRatioChangePercent;
    }
}
