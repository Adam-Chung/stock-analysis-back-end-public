package com.adamchung.stockanalysis.externaldata.bank.dto;

/** 本國銀行體系資本適足率（銀行局 bis.xls，全體銀行、非個別股票篩選）。 */
public record BankCapitalAdequacyRow(
        String bankCode, // 銀行代碼
        String bankNameZh, // 銀行中文名稱
        String bankNameEn, // 銀行英文名稱
        /** 例如 {@code "115年3月底"}（民國年，原始檔案表頭格式，年/季欄位直接組字）。 */
        String period,
        double capitalAdequacyRatioPercent, // 資本適足率(%)
        String sourceUrl // 資料來源網址
) {
}
