package com.adamchung.stockanalysis.externaldata.twse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * TWSE {@code www.twse.com.tw/rwd/...} 系列日報／月報的共用回應形狀（MI_QFIIS 外資及陸資
 * 持股、TWT47U 三大法人買賣超月報皆同一種殼）。{@code data} 是逐列的字串陣列，欄位順序依
 * 各自報表定義（見 {@link TwseForeignHoldingClient}／{@link TwseInstitutionalFlowClient}）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwseDailyReportResponse(
        String stat, // 查詢狀態（"OK"表示成功）
        List<List<String>> data // 逐列字串陣列資料
) {

    public boolean isOk() {
        return "OK".equals(stat) && data != null && !data.isEmpty();
    }
}
