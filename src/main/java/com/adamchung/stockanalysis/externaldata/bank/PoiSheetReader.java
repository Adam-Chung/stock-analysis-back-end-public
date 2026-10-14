package com.adamchung.stockanalysis.externaldata.bank;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 Excel 工作表讀成「逐列、逐欄字串」的二維陣列，比照 collector 原本用的
 * {@code XLSX.utils.sheet_to_json(sheet, {header:1, raw:false, defval:""})}——每一格都是
 * 顯示用字串（不管原始型別是數字/文字/公式），空白格補空字串，方便用固定欄位索引存取。
 * {@link org.apache.poi.ss.usermodel.WorkbookFactory#create(java.io.InputStream)} 會自動
 * 判斷是舊版 {@code .xls}（銀行局資本適足率）還是 {@code .xlsx}（資產品質評估分析統計表）。
 */
public final class PoiSheetReader {

    private static final byte[] OLE2_SIGNATURE =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final int CONTENT_PREVIEW_LENGTH = 200;

    private PoiSheetReader() {
    }

    public static List<List<String>> readFirstSheetAsStrings(byte[] fileBytes) {
        // DEC-061 只把底層 IOException 的 getMessage() 帶進錯誤訊息，
        // 但 POI 認不出格式時丟出的是通用訊息（例如「neither an OLE2 stream, nor an OOXML
        // stream」），完全看不出實際收到的是什麼——先用檔案簽章判斷，不是 Excel 就直接把
        // 內容開頭（很可能是政府網站的攔截頁/錯誤頁 HTML）寫進例外訊息，不必等 POI 解析失敗。
        if (!looksLikeExcelFile(fileBytes)) {
            throw new ExternalDataException(
                    "回應內容不是 Excel 檔案（%d bytes，開頭非 OLE2/OOXML 簽章）。內容開頭：%s"
                            .formatted(fileBytes.length, previewText(fileBytes)));
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<List<String>> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int lastCell = row.getLastCellNum();
                    for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                        Cell cell = row.getCell(cellIndex);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                }
                rows.add(cells);
            }
            return rows;
        } catch (IOException e) {
            // 把根本原因帶進訊息本身，不是只放進 cause——controller 目前只把
            // exception.getMessage() 回給前端（見 ExternalHealthDataSyncController），
            // cause 訊息不會被使用者看到，過去只寫「Excel 檔案讀取失敗」讓人完全查不出是
            // 網路逾時、對方回傳了非 Excel 內容（例如錯誤頁面）、還是檔案本身真的損毀（DEC-061）。
            throw new ExternalDataException(
                    "Excel 檔案讀取失敗（%d bytes）：%s".formatted(fileBytes.length, e.getMessage()), e);
        }
    }

    /** 依欄位索引安全取值；該列沒有這麼多欄時回傳空字串（等同 JS 陣列取超界索引得到 undefined 再轉字串）。 */
    public static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    /** 舊版 .xls 是 OLE2 複合文件、.xlsx 是 zip（OOXML），簽章比對足以判斷「這根本不是 Excel」。 */
    private static boolean looksLikeExcelFile(byte[] bytes) {
        return startsWith(bytes, OLE2_SIGNATURE) || startsWith(bytes, ZIP_SIGNATURE);
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** 把回應開頭當文字預覽；HTML 錯誤頁多半是 UTF-8，控制字元換成空白避免訊息本身格式跑掉。 */
    private static String previewText(byte[] bytes) {
        int length = Math.min(bytes.length, CONTENT_PREVIEW_LENGTH);
        String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
        StringBuilder sanitized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sanitized.append(Character.isISOControl(c) && c != ' ' ? ' ' : c);
        }
        return sanitized.toString().trim();
    }
}
