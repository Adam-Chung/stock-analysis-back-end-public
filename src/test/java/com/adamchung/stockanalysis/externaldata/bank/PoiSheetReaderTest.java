package com.adamchung.stockanalysis.externaldata.bank;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoiSheetReaderTest {

    @Test
    void reads_a_valid_xlsx_workbook_into_rows_of_cell_strings() {
        byte[] bytes = xlsxWithFirstSheetRow("代號", "股票名稱");

        List<List<String>> rows = PoiSheetReader.readFirstSheetAsStrings(bytes);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly("代號", "股票名稱");
    }

    @Test
    void rejects_an_html_block_page_without_relying_on_pois_generic_message() {
        String html = "<!DOCTYPE html><html><body>存取遭拒，請稍後再試</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PoiSheetReader.readFirstSheetAsStrings(bytes))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining(String.valueOf(bytes.length))
                .hasMessageContaining("不是 Excel 檔案")
                .hasMessageContaining("存取遭拒");
    }

    @Test
    void truncates_the_content_preview_instead_of_dumping_the_whole_body() {
        String html = "<!DOCTYPE html>" + "x".repeat(1_000);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PoiSheetReader.readFirstSheetAsStrings(bytes))
                .isInstanceOf(ExternalDataException.class)
                .extracting(Throwable::getMessage)
                .satisfies(message -> assertThat(((String) message).length()).isLessThan(bytes.length));
    }

    private static byte[] xlsxWithFirstSheetRow(String... cellValues) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("sheet1");
            var row = sheet.createRow(0);
            for (int i = 0; i < cellValues.length; i++) {
                row.createCell(i).setCellValue(cellValues[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
