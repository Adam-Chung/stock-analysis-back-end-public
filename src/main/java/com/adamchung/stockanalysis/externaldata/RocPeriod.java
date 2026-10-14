package com.adamchung.stockanalysis.externaldata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 民國年格式的期間字串（銀行局資料集慣用格式）轉換工具。 */
public final class RocPeriod {

    private static final Pattern ROC_YEAR_MONTH = Pattern.compile("(\\d{2,3})年(\\d{1,2})月");

    private RocPeriod() {
    }

    /**
     * {@code "115年3月底"} 或 {@code "115年3月"} → {@code "2026Q1"}。
     *
     * @throws ExternalDataException 字串不符合「民國年+月」格式時拋出
     */
    public static String toQuarterLabel(String rocPeriod) {
        Matcher matcher = ROC_YEAR_MONTH.matcher(rocPeriod);
        if (!matcher.find()) {
            throw new ExternalDataException("無法解析民國年月格式：" + rocPeriod);
        }
        int rocYear = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int adYear = rocYear + 1911;
        int quarter = (month - 1) / 3 + 1;
        return adYear + "Q" + quarter;
    }

    /** {@code "YYYY-MM"}（TWSE 開放資料的資料年月）→ {@code "YYYYQ#"}。 */
    public static String isoMonthToQuarterLabel(String isoYearMonth) {
        String[] parts = isoYearMonth.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int quarter = (month - 1) / 3 + 1;
        return year + "Q" + quarter;
    }
}
