package com.adamchung.stockanalysis.externaldata.bank.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.bank.PoiSheetReader;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankCapitalAdequacyRow;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 銀行局本國銀行體系資本適足率（{@code bis.xls}，全體銀行、不依股票代號篩選）。移植自
 * {@code src/clients/bank-capital-adequacy.ts}。
 *
 * <p>DEC-080：補上比照 {@link BankNplCoverageClient} 的合理性檢查（銀行家數範圍、比率上限）
 * ——這個 client 原本只檢查「有沒有解析出任何資料」，網站改版把欄位順序打亂但仍能解析出
 * 「看起來正常」的數字時（例如把銀行代碼欄位誤讀成比率）不會被抓到，這裡補齊跟
 * {@link BankNplCoverageClient} 同一等級的防護。
 */
@Service
public class BankCapitalAdequacyClient {

    private static final String CAPITAL_ADEQUACY_FILE_URL = "https://www.banking.gov.tw/webdowndoc?file=/stat/bis.xls";
    private static final int BANK_CODE_COLUMN = 0;
    private static final int BANK_NAME_ZH_COLUMN = 1;
    private static final int BANK_NAME_EN_COLUMN = 2;
    private static final int FIRST_DATA_COLUMN = 3;
    private static final int YEAR_HEADER_ROW = 2;
    private static final int QUARTER_HEADER_ROW = 3;
    private static final Pattern BANK_CODE_PATTERN = Pattern.compile("^\\d{3,4}$");
    private static final Pattern YEAR_HEADER_PATTERN = Pattern.compile("^\\d{2,3}年$");
    private static final Pattern QUARTER_HEADER_PATTERN = Pattern.compile("^(?:Q[1-4]|(?:3|6|9|12)月底)$");
    // 跟 BankNplCoverageClient 沿用同一組銀行家數範圍——兩者都是「本國銀行體系」全體銀行。
    private static final int MIN_EXPECTED_BANKS = 20;
    private static final int MAX_EXPECTED_BANKS = 60;
    // 新設銀行可能因風險性資產仍低而出現遠高於一般銀行的比率；100% 以上交由同步層警告，
    // 只有負值、非有限數值或超過 1000% 才視為疑似欄位錯置並中止這個資料來源。
    private static final double MIN_PLAUSIBLE_RATIO_PERCENT = 0;
    private static final double MAX_PLAUSIBLE_RATIO_PERCENT = 1000;

    private final ExternalDataHttpClient httpClient;

    public BankCapitalAdequacyClient(ExternalDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<BankCapitalAdequacyRow> fetchBankCapitalAdequacy() {
        byte[] bytes = httpClient.getBytes(CAPITAL_ADEQUACY_FILE_URL);
        return parseBankCapitalAdequacyWorkbook(bytes, CAPITAL_ADEQUACY_FILE_URL);
    }

    static List<BankCapitalAdequacyRow> parseBankCapitalAdequacyWorkbook(byte[] fileBytes, String sourceUrl) {
        List<List<String>> rows = PoiSheetReader.readFirstSheetAsStrings(fileBytes);
        List<String> yearRow = rows.size() > YEAR_HEADER_ROW ? rows.get(YEAR_HEADER_ROW) : List.of();
        List<String> quarterRow = rows.size() > QUARTER_HEADER_ROW ? rows.get(QUARTER_HEADER_ROW) : List.of();

        Map<Integer, String> periodByColumn = new LinkedHashMap<>();
        String currentYearLabel = "";
        for (int col = FIRST_DATA_COLUMN; col < quarterRow.size(); col++) {
            String yearCell = PoiSheetReader.cell(yearRow, col).trim();
            if (!yearCell.isEmpty()) {
                currentYearLabel = firstLine(yearCell);
                if (!YEAR_HEADER_PATTERN.matcher(currentYearLabel).matches()) {
                    throw new ExternalDataException("本國銀行體系資本適足率年份欄位格式異常「"
                            + currentYearLabel + "」：" + sourceUrl);
                }
            }
            String quarterCell = PoiSheetReader.cell(quarterRow, col).trim();
            if (quarterCell.isEmpty() || currentYearLabel.isEmpty()) {
                continue;
            }
            String quarterLabel = firstLine(quarterCell);
            if (!QUARTER_HEADER_PATTERN.matcher(quarterLabel).matches()) {
                throw new ExternalDataException("本國銀行體系資本適足率季度欄位格式異常「"
                        + quarterLabel + "」：" + sourceUrl);
            }
            periodByColumn.put(col, currentYearLabel + quarterLabel);
        }
        if (periodByColumn.isEmpty()) {
            throw new ExternalDataException("本國銀行體系資本適足率檔案找不到有效期間欄位：" + sourceUrl);
        }

        List<BankCapitalAdequacyRow> result = new ArrayList<>();
        Set<String> seenBankCodes = new HashSet<>();
        Set<String> seenBankPeriods = new HashSet<>();
        for (List<String> row : rows) {
            String bankCode = PoiSheetReader.cell(row, BANK_CODE_COLUMN).trim();
            if (!BANK_CODE_PATTERN.matcher(bankCode).matches()) {
                continue;
            }
            seenBankCodes.add(bankCode);
            String bankNameZh = PoiSheetReader.cell(row, BANK_NAME_ZH_COLUMN).trim();
            String bankNameEn = PoiSheetReader.cell(row, BANK_NAME_EN_COLUMN).trim();
            if (bankNameZh.isEmpty()) {
                throw new ExternalDataException("本國銀行體系資本適足率銀行名稱為空，銀行代碼「"
                        + bankCode + "」：" + sourceUrl);
            }
            for (Map.Entry<Integer, String> entry : periodByColumn.entrySet()) {
                Double capitalAdequacyRatioPercent = parsePercent(PoiSheetReader.cell(row, entry.getKey()));
                if (capitalAdequacyRatioPercent == null) {
                    continue;
                }
                if (!Double.isFinite(capitalAdequacyRatioPercent)
                        || capitalAdequacyRatioPercent < MIN_PLAUSIBLE_RATIO_PERCENT
                        || capitalAdequacyRatioPercent > MAX_PLAUSIBLE_RATIO_PERCENT) {
                    throw new ExternalDataException(
                            "銀行「" + bankNameZh + "」" + entry.getValue() + "資本適足率(" + capitalAdequacyRatioPercent
                                    + "%)超出合理範圍，疑似欄位錯置：" + sourceUrl);
                }
                String bankPeriodKey = bankCode + "|" + entry.getValue();
                if (!seenBankPeriods.add(bankPeriodKey)) {
                    throw new ExternalDataException(
                            "本國銀行體系資本適足率檔案出現重複資料「" + bankNameZh + " " + entry.getValue() + "」：" + sourceUrl);
                }
                result.add(new BankCapitalAdequacyRow(
                        bankCode, bankNameZh, bankNameEn, entry.getValue(), capitalAdequacyRatioPercent, sourceUrl));
            }
        }

        if (result.isEmpty()) {
            throw new ExternalDataException("無法從本國銀行體系資本適足率檔案解析出任何資料：" + sourceUrl);
        }
        if (seenBankCodes.size() < MIN_EXPECTED_BANKS || seenBankCodes.size() > MAX_EXPECTED_BANKS) {
            throw new ExternalDataException(
                    "解析出 " + seenBankCodes.size() + " 家銀行，超出合理範圍 " + MIN_EXPECTED_BANKS + "~" + MAX_EXPECTED_BANKS
                            + " 家，疑似格式已變更：" + sourceUrl);
        }
        return result;
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline) : text;
    }

    private static Double parsePercent(String raw) {
        String text = raw.replace(",", "").trim();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
