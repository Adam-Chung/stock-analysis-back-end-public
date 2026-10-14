package com.adamchung.stockanalysis.externaldata.bank.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankCapitalAdequacyRow;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DEC-080：新增的合理性檢查（銀行家數範圍、比率上限），比照既有 BankNplCoverageClient 的做法。 */
class BankCapitalAdequacyClientTest {

    @Test
    void parses_a_plausible_workbook_into_rows() {
        byte[] bytes = workbookWithBanks(20, "12.5");

        List<BankCapitalAdequacyRow> rows =
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls");

        assertThat(rows).hasSize(20);
        assertThat(rows.get(0).capitalAdequacyRatioPercent()).isEqualTo(12.5);
    }

    @Test
    void accepts_an_unusually_high_but_possible_ratio() {
        byte[] bytes = workbookWithBanks(20, "698.83");

        List<BankCapitalAdequacyRow> rows =
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls");

        assertThat(rows.get(0).capitalAdequacyRatioPercent()).isEqualTo(698.83);
    }

    @Test
    void rejects_a_ratio_above_the_structural_safety_limit_as_a_likely_column_misalignment() {
        byte[] bytes = workbookWithBanks(20, "1000.01"); // 明顯異常，疑似欄位錯置

        assertThatThrownBy(() ->
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("超出合理範圍");
    }

    @Test
    void rejects_a_negative_ratio() {
        byte[] bytes = workbookWithBanks(20, "-0.01");

        assertThatThrownBy(() ->
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("超出合理範圍");
    }

    @Test
    void rejects_an_unexpected_period_header() {
        byte[] bytes = workbookWithBanks(20, "12.5", "資本適足率");

        assertThatThrownBy(() ->
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("季度欄位格式異常");
    }

    @Test
    void rejects_when_the_number_of_banks_found_is_outside_the_expected_range() {
        byte[] bytes = workbookWithBanks(2, "12.5"); // 遠低於 MIN_EXPECTED_BANKS=20

        assertThatThrownBy(() ->
                BankCapitalAdequacyClient.parseBankCapitalAdequacyWorkbook(bytes, "http://example.test/bis.xls"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("超出合理範圍")
                .hasMessageContaining("家");
    }

    private static byte[] workbookWithBanks(int bankCount, String ratioText) {
        return workbookWithBanks(bankCount, ratioText, "Q4");
    }

    /**
     * 建立指定銀行數、資本適足率及季度標題的測試活頁簿。
     */
    private static byte[] workbookWithBanks(int bankCount, String ratioText, String quarterHeader) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("sheet1");
            sheet.createRow(0);
            sheet.createRow(1);
            XSSFRow yearRow = sheet.createRow(2);
            yearRow.createCell(3).setCellValue("114年");
            XSSFRow quarterRow = sheet.createRow(3);
            quarterRow.createCell(3).setCellValue(quarterHeader);
            for (int i = 0; i < bankCount; i++) {
                XSSFRow row = sheet.createRow(4 + i);
                row.createCell(0).setCellValue(String.valueOf(1000 + i));
                row.createCell(1).setCellValue("測試銀行" + i);
                row.createCell(2).setCellValue("Test Bank " + i);
                row.createCell(3).setCellValue(ratioText);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
