package com.adamchung.stockanalysis.externaldata.twse.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.twse.dto.DirectorHoldingsRow;
import com.adamchung.stockanalysis.externaldata.twse.dto.TwseCompanyProfileDto;
import com.adamchung.stockanalysis.externaldata.twse.dto.TwseDirectorHoldingDto;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TWSE 官方開放資料（openapi.twse.com.tw）：董監大股東持股（t187ap11_L）＋公司基本資料
 * （t187ap03_L）。移植自 stock-financial-data-collector 的 {@code src/clients/twse-opendata.ts}。
 *
 * <p>改用這兩份官方開放資料取代原本規劃的 MOPS 網頁爬取，因為兩者本來就是同一批董監持股
 * 申報資料的官方重新發布版本，不受 mopsov.twse.com.tw 的 robots.txt 限制。目前僅提供
 * 最新一期資料，不含歷史期別（DEC-034 已知限制之一，這次整合後改為「有資料就自動帶入最新
 * 一期，之後每次同步自然累積成多季」，見 {@link ExternalHealthDataSyncService}）。
 */
@Service
public class TwseOpenDataClient {

    static final String DIRECTOR_HOLDINGS_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap11_L";
    static final String COMPANY_PROFILE_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap03_L";
    private static final Pattern DIRECTOR_TITLE_PATTERN = Pattern.compile("董事|監察人");

    private final ExternalDataHttpClient httpClient;

    public TwseOpenDataClient(ExternalDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Map<String, TwseCompanyProfileDto> fetchCompanyProfiles() {
        List<TwseCompanyProfileDto> records = httpClient.getJson(COMPANY_PROFILE_URL,
                new ParameterizedTypeReference<List<TwseCompanyProfileDto>>() {
                });
        Map<String, TwseCompanyProfileDto> byCode = new HashMap<>();
        for (TwseCompanyProfileDto record : records) {
            byCode.put(record.stockCode(), record);
        }
        return byCode;
    }

    /**
     * @param stockCodes 要查詢的股票代號清單
     * @return 每個代號各一列（找不到資料時整批拋例外，比照原 TS 實作——寧可整批失敗也不要
     *         悄悄漏掉某支股票）
     */
    public List<DirectorHoldingsRow> fetchDirectorHoldings(List<String> stockCodes) {
        List<TwseDirectorHoldingDto> holdingRecords = httpClient.getJson(DIRECTOR_HOLDINGS_URL,
                new ParameterizedTypeReference<List<TwseDirectorHoldingDto>>() {
                });
        Map<String, TwseCompanyProfileDto> profileByCode = fetchCompanyProfiles();

        java.util.Set<String> codeSet = new java.util.HashSet<>(stockCodes);
        Map<String, List<TwseDirectorHoldingDto>> recordsByCode = new HashMap<>();
        for (TwseDirectorHoldingDto record : holdingRecords) {
            if (!codeSet.contains(record.stockCode())) {
                continue;
            }
            recordsByCode.computeIfAbsent(record.stockCode(), k -> new ArrayList<>()).add(record);
        }

        List<DirectorHoldingsRow> result = new ArrayList<>();
        for (String code : stockCodes) {
            List<TwseDirectorHoldingDto> records = recordsByCode.get(code);
            TwseCompanyProfileDto profile = profileByCode.get(code);
            if (records == null || records.isEmpty() || profile == null) {
                throw new ExternalDataException("TWSE 開放資料找不到股票代號 " + code + " 的董監持股或公司基本資料");
            }
            result.add(aggregateDirectorHoldings(code, records, profile));
        }
        return result;
    }

    /**
     * DEC-072：真實 API 驗證發現（1216 統一）當同一個法人股東（例如「高權投資股份有限公司」）
     * 依累積投票制一次拿下多席董事時，TWSE 這份開放資料會把它的持股數在每一席各重複列一次
     * （職稱分別是「董事長本人」「董事本人」「董事本人」，姓名都是同一家投資公司，「目前持股」
     * 數字完全相同）——原本直接加總所有「職稱含董事／監察人」的列，會把同一批股份重複計算
     * 好幾次（1216 案例：284,330,536 股被算了 3 次，董監持股比從應有的 9.9% 灌水成 25.6%）。
     *
     * <p>「職稱」欄位另外還有「董事之法人代表人」這類列，是揭露「這個法人董事席次由哪位自然人
     * 代表出席」，「目前持股」欄位放的是這位代表人**自己**的個人持股，跟法人本身的持股是兩回事、
     * 不該併入「董監持股比」（已對真實 API 驗證：把這類列排除、法人股東依姓名去重後，1216
     * 算出的持股比／質押比分別是 9.9%／14.2%，與外部財經網站公開數字吻合）。
     *
     * <p>修正：只保留職稱以「本人」結尾的列（即席次本身的持股人，不含法人代表人列），再依
     * {@link TwseDirectorHoldingDto#name} 去重（同一個股東不論拿下幾席，持股只算一次）。
     */
    static DirectorHoldingsRow aggregateDirectorHoldings(
            String stockCode, List<TwseDirectorHoldingDto> records, TwseCompanyProfileDto profile) {
        List<TwseDirectorHoldingDto> seatHolderRows = records.stream()
                .filter(r -> DIRECTOR_TITLE_PATTERN.matcher(r.title()).find())
                .filter(r -> r.title().endsWith("本人"))
                .toList();
        if (seatHolderRows.isEmpty()) {
            throw new ExternalDataException("股票代號 " + stockCode + " 的資料中找不到任何董事／監察人職稱列");
        }

        Map<String, TwseDirectorHoldingDto> uniqueHolders = new LinkedHashMap<>();
        for (TwseDirectorHoldingDto row : seatHolderRows) {
            uniqueHolders.putIfAbsent(row.name(), row);
        }

        double totalShares = uniqueHolders.values().stream().mapToDouble(r -> parseNumber(r.currentShares())).sum();
        double totalPledgedShares = uniqueHolders.values().stream().mapToDouble(r -> parseNumber(r.pledgedShares())).sum();
        double issuedShares = parseNumber(profile.issuedShares());
        String period = seatHolderRows.get(0).dataYearMonth();

        return new DirectorHoldingsRow(
                stockCode,
                profile.companyName(),
                (totalShares / issuedShares) * 100,
                totalShares > 0 ? (totalPledgedShares / totalShares) * 100 : 0,
                rocYearMonthToIso(period),
                DIRECTOR_HOLDINGS_URL);
    }

    /** {@code "11506"}（民國115年6月，YYYMM）→ {@code "2026-06"}。 */
    private static String rocYearMonthToIso(String rocYearMonth) {
        int rocYear = Integer.parseInt(rocYearMonth.substring(0, 3));
        String month = rocYearMonth.substring(3, 5);
        return (rocYear + 1911) + "-" + month;
    }

    private static double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        return Double.parseDouble(raw.replace(",", "").trim());
    }
}
