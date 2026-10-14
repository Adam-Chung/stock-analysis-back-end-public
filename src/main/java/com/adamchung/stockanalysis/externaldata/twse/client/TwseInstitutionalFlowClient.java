package com.adamchung.stockanalysis.externaldata.twse.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.externaldata.twse.TwseDailyReportResponse;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalFlowRow;
import com.adamchung.stockanalysis.externaldata.twse.dto.TwseCompanyProfileDto;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TWSE「三大法人買賣超月報」（TWT47U，官方月度彙總）。移植自
 * {@code src/clients/twse-institutional-flow.ts}。算出來的是「淨買賣超佔發行股數比例」的
 * 季度變動，不是持股水位——投信／自營商沒有官方公布的累計持股比率，這是估算值。
 */
@Service
public class TwseInstitutionalFlowClient {

    public static final String TWT47U_URL = "https://www.twse.com.tw/rwd/zh/fund/TWT47U";
    private static final int STOCK_CODE_COLUMN = 0;
    private static final int STOCK_NAME_COLUMN = 1;
    private static final int TRUST_NET_COLUMN = 10;
    private static final int DEALER_NET_COLUMN = 11;
    private static final int MONTHS_PER_QUARTER = 3;
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExternalDataHttpClient httpClient;
    private final TwseOpenDataClient openDataClient;

    public TwseInstitutionalFlowClient(ExternalDataHttpClient httpClient, TwseOpenDataClient openDataClient) {
        this.httpClient = httpClient;
        this.openDataClient = openDataClient;
    }

    public List<InstitutionalFlowRow> fetchQuarterlyInstitutionalNetFlow(QuarterRange quarter, List<String> stockCodes) {
        Set<String> codeSet = new HashSet<>(stockCodes);
        Map<String, MonthlyNetFlow> netByCode = new HashMap<>();

        for (int month = 0; month < MONTHS_PER_QUARTER; month++) {
            var date = quarter.start().plusMonths(month);
            String dateParam = date.format(COMPACT_DATE);
            TwseDailyReportResponse response = httpClient.getJson(
                    TWT47U_URL + "?response=json&date=" + dateParam + "&selectType=ALLBUT0999",
                    new ParameterizedTypeReference<TwseDailyReportResponse>() {
                    });
            if (!response.isOk()) {
                throw new ExternalDataException(
                        quarter.label() + " 的 " + date.getYear() + "年" + date.getMonthValue()
                                + "月三大法人買賣超月報查詢失敗（stat=" + response.stat() + "）：" + TWT47U_URL);
            }

            for (List<String> record : response.data()) {
                String code = record.get(STOCK_CODE_COLUMN);
                if (!codeSet.contains(code)) {
                    continue;
                }
                MonthlyNetFlow existing = netByCode.computeIfAbsent(code,
                        k -> new MonthlyNetFlow(record.get(STOCK_NAME_COLUMN).trim(), 0, 0));
                // 把這一季 3 個月的投信/自營商淨買超股數累加成整季合計
                existing.trustNetShares += parseNumber(record.get(TRUST_NET_COLUMN));
                existing.dealerNetShares += parseNumber(record.get(DEALER_NET_COLUMN));
            }
        }

        Map<String, TwseCompanyProfileDto> profileByCode = openDataClient.fetchCompanyProfiles();
        List<InstitutionalFlowRow> rows = new ArrayList<>();
        for (String code : stockCodes) {
            MonthlyNetFlow net = netByCode.get(code);
            TwseCompanyProfileDto profile = profileByCode.get(code);
            if (net == null || profile == null) {
                throw new ExternalDataException(quarter.label() + " 找不到股票代號 " + code + " 的三大法人買賣超或公司基本資料");
            }
            double issuedShares = parseNumber(profile.issuedShares());
            // 估算持股比率變動＝整季淨買超股數 ÷ 已發行股數 × 100（不是精確持股水位，見class Javadoc）
            rows.add(new InstitutionalFlowRow(
                    code,
                    net.companyName,
                    quarter.label(),
                    (net.trustNetShares / issuedShares) * 100,
                    (net.dealerNetShares / issuedShares) * 100,
                    TWT47U_URL));
        }
        return rows;
    }

    private static double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", "").trim());
    }

    private static final class MonthlyNetFlow {
        final String companyName;
        double trustNetShares;
        double dealerNetShares;

        MonthlyNetFlow(String companyName, double trustNetShares, double dealerNetShares) {
            this.companyName = companyName;
            this.trustNetShares = trustNetShares;
            this.dealerNetShares = dealerNetShares;
        }
    }
}
