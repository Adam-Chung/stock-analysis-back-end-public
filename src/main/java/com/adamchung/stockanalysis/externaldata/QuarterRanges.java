package com.adamchung.stockanalysis.externaldata;

import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 曆年季度計算（純函式），移植自 stock-financial-data-collector 的
 * {@code src/lib/quarters.ts}，語意逐字對照，供 {@link TwseForeignHoldingClient}／
 * {@link TwseInstitutionalFlowClient} 決定要查詢哪幾個季度。
 */
public final class QuarterRanges {

    private static final int[] QUARTER_END_MONTHS = {3, 6, 9, 12};

    private QuarterRanges() {
    }

    /**
     * 依 {@code from} 往前推算最近 {@code count} 個「已經結束」的季底日期（新到舊）。
     * 尚未到達的季底（例如今天是季度中間）不計入，會往前一季推算。
     */
    public static List<LocalDate> recentQuarterEndDates(int count, LocalDate from) {
        List<LocalDate> dates = new ArrayList<>();
        int year = from.getYear();
        int monthIndex = -1;
        // 找出 from 所在年度、目前月份為止最後一個已到達的季底月份（3/6/9/12）在陣列中的位置；
        // 例如 from 是 8 月，3、6 都 <= 8，monthIndex 最後停在 1（指向 6 月），9、12 因為 > 8 不會再推進
        for (int month : QUARTER_END_MONTHS) {
            if (month <= from.getMonthValue()) {
                monthIndex++;
            }
        }
        while (dates.size() < count) {
            if (monthIndex < 0) {
                // 索引往前推到超出陣列範圍，代表要跨到上一年度，從去年的 Q4（12月）重新開始數
                year -= 1;
                monthIndex = QUARTER_END_MONTHS.length - 1;
            }
            int month = QUARTER_END_MONTHS[monthIndex];
            LocalDate lastDay = YearMonth.of(year, month).atEndOfMonth();
            // 只收「已經結束」的季底（季底日 <= from）；今天若剛好落在季度中間，當季季底還沒到，
            // 這個 if 會不成立而跳過，等於自動退回上一個已結束的季度
            if (!lastDay.isAfter(from)) {
                dates.add(lastDay);
            }
            monthIndex -= 1;
        }
        return dates;
    }

    /** 每個季底日期，各自配對該季的起始日與 {@code 'YYYYQ#'} 標籤。 */
    public static List<QuarterRange> recentQuarterRanges(int count, LocalDate from) {
        List<QuarterRange> ranges = new ArrayList<>();
        for (LocalDate end : recentQuarterEndDates(count, from)) {
            LocalDate start = end.withDayOfMonth(1).minusMonths(2);
            int quarterNumber = (end.getMonthValue() - 1) / 3 + 1;
            ranges.add(new QuarterRange(start, end, end.getYear() + "Q" + quarterNumber));
        }
        return ranges;
    }
}
