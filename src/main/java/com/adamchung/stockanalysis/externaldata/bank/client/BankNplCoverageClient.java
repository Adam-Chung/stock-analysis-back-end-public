package com.adamchung.stockanalysis.externaldata.bank.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.bank.PoiSheetReader;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankNplCoverageRow;
import com.adamchung.stockanalysis.externaldata.bank.dto.NplCoverageFileLink;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 銀行局「本國銀行資產品質評估分析統計表」逾期放款比率／備抵呆帳覆蓋率。移植自
 * {@code src/clients/bank-npl-coverage.ts}，含原本就有的合理性檢查（銀行家數範圍、比率上限）
 * ——這些檢查是移植時刻意保留，不是新加的，用來及早發現政府檔案格式變更導致的欄位錯置。
 */
@Service
public class BankNplCoverageClient {

    private static final int BANK_NAME_COLUMN = 0;
    private static final int OVERDUE_LOAN_RATIO_COLUMN = 7;
    private static final int COVERAGE_RATIO_COLUMN = 8;
    private static final String TOTAL_ROW_MARKER = "總";
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{2,3}年\\d{1,2}月$");
    private static final Pattern PERIOD_LABEL_PATTERN = Pattern.compile("資料月份[：:]\\s*");
    private static final int MIN_EXPECTED_BANKS = 20;
    private static final int MAX_EXPECTED_BANKS = 60;
    private static final double MAX_PLAUSIBLE_OVERDUE_RATIO_PERCENT = 50;
    // 覆蓋率＝備抵呆帳／逾期放款；當逾期放款趨近於 0 時比率可能高達數十萬%，屬合理現象，
    // 這裡只擋明顯的型別錯置或欄位跑掉。
    private static final double MAX_PLAUSIBLE_COVERAGE_RATIO_PERCENT = 10_000_000;

    private final ExternalDataHttpClient httpClient;

    public BankNplCoverageClient(ExternalDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<BankNplCoverageRow> fetchBankNplCoverage(NplCoverageFileLink link) {
        byte[] bytes = httpClient.getBytes(link.url());
        return parseBankNplCoverageWorkbook(bytes, link.url());
    }

    static List<BankNplCoverageRow> parseBankNplCoverageWorkbook(byte[] fileBytes, String sourceUrl) {
        List<List<String>> rows = PoiSheetReader.readFirstSheetAsStrings(fileBytes);

        String period = "";
        for (List<String> row : rows) {
            String first = PoiSheetReader.cell(row, 0);
            if (first.contains("資料月份")) {
                period = PERIOD_LABEL_PATTERN.matcher(first).replaceAll("").trim();
                break;
            }
        }
        if (!PERIOD_PATTERN.matcher(period).matches()) {
            throw new ExternalDataException("資產品質評估分析統計表的資料月份格式不符預期「" + period + "」：" + sourceUrl);
        }

        List<BankNplCoverageRow> result = new ArrayList<>();
        Set<String> seenBankNames = new HashSet<>();
        for (List<String> row : rows) {
            String bankName = PoiSheetReader.cell(row, BANK_NAME_COLUMN).trim();
            if (bankName.isEmpty() || bankName.contains(TOTAL_ROW_MARKER)) {
                continue;
            }
            Double overdueLoanRatioPercent = parsePercent(PoiSheetReader.cell(row, OVERDUE_LOAN_RATIO_COLUMN));
            Double provisionCoverageRatioPercent = parsePercent(PoiSheetReader.cell(row, COVERAGE_RATIO_COLUMN));
            if (overdueLoanRatioPercent == null || provisionCoverageRatioPercent == null) {
                continue;
            }
            if (!seenBankNames.add(bankName)) {
                throw new ExternalDataException("資產品質評估分析統計表出現重複銀行「" + bankName + "」：" + sourceUrl);
            }
            if (overdueLoanRatioPercent < 0 || overdueLoanRatioPercent > MAX_PLAUSIBLE_OVERDUE_RATIO_PERCENT
                    || provisionCoverageRatioPercent < 0
                    || provisionCoverageRatioPercent > MAX_PLAUSIBLE_COVERAGE_RATIO_PERCENT) {
                throw new ExternalDataException(
                        "銀行「" + bankName + "」的逾放比(" + overdueLoanRatioPercent + "%)或覆蓋率("
                                + provisionCoverageRatioPercent + "%)超出合理範圍，疑似欄位錯置：" + sourceUrl);
            }
            result.add(new BankNplCoverageRow(bankName, period, overdueLoanRatioPercent, provisionCoverageRatioPercent, sourceUrl));
        }

        if (result.size() < MIN_EXPECTED_BANKS || result.size() > MAX_EXPECTED_BANKS) {
            throw new ExternalDataException(
                    "解析出 " + result.size() + " 家銀行，超出合理範圍 " + MIN_EXPECTED_BANKS + "~" + MAX_EXPECTED_BANKS
                            + " 家，疑似格式已變更：" + sourceUrl);
        }
        return result;
    }

    private static Double parsePercent(String raw) {
        String text = raw.replace(",", "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
