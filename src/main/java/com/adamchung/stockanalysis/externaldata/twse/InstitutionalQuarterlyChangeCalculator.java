package com.adamchung.stockanalysis.externaldata.twse;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.externaldata.QuarterRanges;
import com.adamchung.stockanalysis.externaldata.twse.client.TwseForeignHoldingClient;
import com.adamchung.stockanalysis.externaldata.twse.client.TwseInstitutionalFlowClient;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalFlowRow;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalHoldingRow;
import com.adamchung.stockanalysis.common.PercentageMath;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「法人持股比季變化(%)」（SA-04，DEC-046：取代原「董監+法人持股比(%)」）的合成邏輯。
 * 移植自 {@code src/clients/institutional-quarterly-change.ts}：外資及陸資用兩期水位相減
 * 得到精確變動，投信／自營商用當季買賣超估算變動，三者加總成單一數字寫入
 * {@code stock_health_snapshot}（見 {@link InstitutionalQuarterlyChangeRow#totalChangePercent()}）。
 */
@Service
public class InstitutionalQuarterlyChangeCalculator {

    private final TwseForeignHoldingClient foreignHoldingClient;
    private final TwseInstitutionalFlowClient institutionalFlowClient;

    public InstitutionalQuarterlyChangeCalculator(
            TwseForeignHoldingClient foreignHoldingClient, TwseInstitutionalFlowClient institutionalFlowClient) {
        this.foreignHoldingClient = foreignHoldingClient;
        this.institutionalFlowClient = institutionalFlowClient;
    }

    /**
     * @param quarterCount 要計算的季數
     * @param stockCodes   股票代號清單
     * @return 每季每股票代號一列，新到舊排序
     */
    public List<InstitutionalQuarterlyChangeRow> fetchInstitutionalQuarterlyChange(
            int quarterCount, List<String> stockCodes) {
        List<QuarterRange> rangesNewestFirst = QuarterRanges.recentQuarterRanges(quarterCount + 1, LocalDate.now());
        List<QuarterRange> targetRanges = rangesNewestFirst.subList(0, quarterCount);

        // 外資及陸資持股水位
        List<List<InstitutionalHoldingRow>> levelsByQuarterEnd = new ArrayList<>();
        for (QuarterRange range : rangesNewestFirst) {
            levelsByQuarterEnd.add(foreignHoldingClient.fetchForeignHoldingRatios(range.end(), stockCodes));
        }
        // 法人買賣超
        List<List<InstitutionalFlowRow>> flowsByQuarter = new ArrayList<>();
        for (QuarterRange range : targetRanges) {
            flowsByQuarter.add(institutionalFlowClient.fetchQuarterlyInstitutionalNetFlow(range, stockCodes));
        }

        List<InstitutionalQuarterlyChangeRow> rows = new ArrayList<>();
        for (int i = 0; i < targetRanges.size(); i++) {
            rows.addAll(combineQuarter(
                    targetRanges.get(i), levelsByQuarterEnd.get(i), levelsByQuarterEnd.get(i + 1), flowsByQuarter.get(i)));
        }
        return rows;
    }

    static List<InstitutionalQuarterlyChangeRow> combineQuarter(
            QuarterRange quarter,
            List<InstitutionalHoldingRow> currentLevels,
            List<InstitutionalHoldingRow> previousLevels,
            List<InstitutionalFlowRow> flows) {
        Map<String, InstitutionalHoldingRow> previousByCode = new HashMap<>();
        for (InstitutionalHoldingRow row : previousLevels) {
            previousByCode.put(row.stockCode(), row);
        }
        Map<String, InstitutionalFlowRow> flowByCode = new HashMap<>();
        for (InstitutionalFlowRow row : flows) {
            flowByCode.put(row.stockCode(), row);
        }

        List<InstitutionalQuarterlyChangeRow> result = new ArrayList<>();
        for (InstitutionalHoldingRow current : currentLevels) {
            InstitutionalHoldingRow previous = previousByCode.get(current.stockCode());
            InstitutionalFlowRow flow = flowByCode.get(current.stockCode());
            if (previous == null || flow == null) {
                throw new ExternalDataException(
                        quarter.label() + " 缺少股票代號 " + current.stockCode() + " 的前一季水位或法人買賣超資料");
            }
            result.add(new InstitutionalQuarterlyChangeRow(
                    current.stockCode(),
                    current.companyName(),
                    quarter.label(),
                    // 外資及陸資：精確作法，本季水位－上季水位，兩期都是官方公布的實際持股比率
                    round2(current.foreignAndMainlandHoldingRatioPercent() - previous.foreignAndMainlandHoldingRatioPercent()),
                    // 投信：估算作法，官方不公布持股水位，直接沿用當季買賣超股數換算出的比率變動
                    flow.investmentTrustNetBuyRatioChangePercent(),
                    // 自營商：估算作法，同投信，用當季買賣超股數換算出的比率變動
                    flow.dealerNetBuyRatioChangePercent(),
                    TwseForeignHoldingClient.QFIIS_URL,
                    TwseInstitutionalFlowClient.TWT47U_URL));
        }
        return result;
    }

    /** 用 {@link PercentageMath} 統一計算，避免 double+Math.round 的二進位浮點誤差（DEC-079）。 */
    private static double round2(double value) {
        return PercentageMath.round(value, 2);
    }
}
