package com.adamchung.stockanalysis.externaldata.twse.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.twse.TwseDailyReportResponse;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalHoldingRow;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TWSE「外資及陸資投資持股統計」（MI_QFIIS，官方每日彙總）。移植自
 * {@code src/clients/twse-foreign-holding.ts}。三大法人中僅外資（含陸資）有官方公開的
 * 「持股比率」水位；投信與自營商僅公開每日買賣超，見 {@link TwseInstitutionalFlowClient}。
 */
@Service
public class TwseForeignHoldingClient {

    public static final String QFIIS_URL = "https://www.twse.com.tw/rwd/zh/fund/MI_QFIIS";
    private static final int MAX_TRADING_DAY_LOOKBACK = 10;
    private static final int STOCK_CODE_COLUMN = 0;
    private static final int STOCK_NAME_COLUMN = 1;
    private static final int FOREIGN_MAINLAND_RATIO_COLUMN = 7;
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExternalDataHttpClient httpClient;

    public TwseForeignHoldingClient(ExternalDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** 非交易日或當日尚未產生資料時往前逐日重試，最多 {@value #MAX_TRADING_DAY_LOOKBACK} 天。 */
    public List<InstitutionalHoldingRow> fetchForeignHoldingRatios(LocalDate targetDate, List<String> stockCodes) {
        Set<String> codeSet = new HashSet<>(stockCodes);
        for (int daysBack = 0; daysBack < MAX_TRADING_DAY_LOOKBACK; daysBack++) {
            LocalDate date = targetDate.minusDays(daysBack);
            String dateParam = date.format(COMPACT_DATE);
            TwseDailyReportResponse response = httpClient.getJson(
                    QFIIS_URL + "?response=json&date=" + dateParam + "&selectType=ALLBUT0999",
                    new ParameterizedTypeReference<TwseDailyReportResponse>() {
                    });
            if (!response.isOk()) {
                continue;
            }

            List<InstitutionalHoldingRow> rows = new ArrayList<>();
            for (List<String> record : response.data()) {
                String code = record.get(STOCK_CODE_COLUMN);
                if (!codeSet.contains(code)) {
                    continue;
                }
                rows.add(new InstitutionalHoldingRow(
                        code,
                        record.get(STOCK_NAME_COLUMN),
                        dateParam,
                        Double.parseDouble(record.get(FOREIGN_MAINLAND_RATIO_COLUMN)),
                        QFIIS_URL));
            }
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        throw new ExternalDataException(
                "TWSE 外資及陸資持股統計在 " + targetDate.format(COMPACT_DATE) + " 往前 "
                        + MAX_TRADING_DAY_LOOKBACK + " 天內都查無資料");
    }
}
