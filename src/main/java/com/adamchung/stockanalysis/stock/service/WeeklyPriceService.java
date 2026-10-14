package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.stock.dto.WeeklyPriceCandleResponse;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/** 提供目標價頁面使用的近三年週 K 資料。 */
@Service
public class WeeklyPriceService {

    private static final int HISTORY_YEARS = 3;
    private static final long CACHE_TTL_NANOS = java.time.Duration.ofMinutes(30).toNanos();

    private final FinMindClient finMindClient;
    private final ConcurrentMap<String, CachedWeeklyPrices> cache = new ConcurrentHashMap<>();

    private record CachedWeeklyPrices(
            List<WeeklyPriceCandleResponse> candles, // 已聚合且不可變的週 K 清單
            long expiresAtNanos // 以單調時間表示的快取到期點
    ) {
    }

    /**
     * 建立週 K 資料服務。
     *
     * @param finMindClient FinMind 市場資料 client
     */
    public WeeklyPriceService(FinMindClient finMindClient) {
        this.finMindClient = finMindClient;
    }

    /**
     * 取得指定股票近三年的日價，依週一所屬週聚合為 OHLC，並依時間由舊到新排序。
     *
     * @param stockId 台股代號
     * @return 近三年週 K 棒
     */
    public List<WeeklyPriceCandleResponse> getWeeklyPrices(String stockId) {
        return getWeeklyPrices(stockId, false);
    }

    /**
     * 取得指定股票近三年的週 K；使用者主動更新時可略過並覆蓋既有快取。
     *
     * @param stockId 台股代號
     * @param forceRefresh 是否略過快取重新抓取 FinMind
     * @return 近三年週 K 棒
     */
    public List<WeeklyPriceCandleResponse> getWeeklyPrices(String stockId, boolean forceRefresh) {
        long now = System.nanoTime();
        CachedWeeklyPrices cached = cache.get(stockId);
        if (!forceRefresh && cached != null && now < cached.expiresAtNanos()) {
            return cached.candles();
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(HISTORY_YEARS);
        List<FinMindStockPriceDto> validRows = finMindClient.fetchStockPrice(stockId, startDate, endDate).stream()
                .filter(WeeklyPriceService::hasValidOhlc)
                .sorted(Comparator.comparing(FinMindStockPriceDto::date))
                .toList();

        Map<LocalDate, List<FinMindStockPriceDto>> rowsByWeek = validRows.stream().collect(Collectors.groupingBy(
                row -> row.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));

        List<WeeklyPriceCandleResponse> candles = rowsByWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toCandle(entry.getKey(), entry.getValue()))
                .toList();
        cache.put(stockId, new CachedWeeklyPrices(candles, now + CACHE_TTL_NANOS));
        return candles;
    }

    /** 將同一週的有效日價聚合為一根週 K 棒。 */
    private static WeeklyPriceCandleResponse toCandle(
            LocalDate weekStart, List<FinMindStockPriceDto> rows) {
        List<FinMindStockPriceDto> sortedRows = rows.stream()
                .sorted(Comparator.comparing(FinMindStockPriceDto::date))
                .toList();
        return new WeeklyPriceCandleResponse(
                weekStart,
                sortedRows.get(0).open(),
                sortedRows.stream().map(FinMindStockPriceDto::max).max(Comparator.naturalOrder()).orElseThrow(),
                sortedRows.stream().map(FinMindStockPriceDto::min).min(Comparator.naturalOrder()).orElseThrow(),
                sortedRows.get(sortedRows.size() - 1).close());
    }

    /** 僅保留日期與 OHLC 都存在且價格大於零的交易日，避免異常資料扭曲圖形。 */
    private static boolean hasValidOhlc(FinMindStockPriceDto row) {
        return row.date() != null
                && row.open() != null && row.open().signum() > 0
                && row.max() != null && row.max().signum() > 0
                && row.min() != null && row.min().signum() > 0
                && row.close() != null && row.close().signum() > 0;
    }
}
