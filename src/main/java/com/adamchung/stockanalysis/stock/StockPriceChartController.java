package com.adamchung.stockanalysis.stock;

import com.adamchung.stockanalysis.stock.dto.WeeklyPriceCandleResponse;
import com.adamchung.stockanalysis.stock.service.WeeklyPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 股票價格圖表的唯讀 HTTP 邊界。 */
@RestController
@RequestMapping("/api/stocks/{stockId}/weekly-prices")
@Tag(name = "股票價格圖表", description = "提供近三年週 K 的 OHLC 資料")
public class StockPriceChartController {

    private final WeeklyPriceService weeklyPriceService;

    /** 建立股票價格圖表 controller。 */
    public StockPriceChartController(WeeklyPriceService weeklyPriceService) {
        this.weeklyPriceService = weeklyPriceService;
    }

    /**
     * 取得指定股票近三年的週 K 資料。
     *
     * @param stockId 台股代號
     * @param refresh 是否略過快取重新抓取
     * @return 依日期由舊至新排列的週 K 棒
     */
    @Operation(summary = "取得近三年週 K", description = "將 FinMind 日價聚合為近三年週線 OHLC")
    @GetMapping
    public List<WeeklyPriceCandleResponse> getWeeklyPrices(
            @PathVariable("stockId") String stockId,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return weeklyPriceService.getWeeklyPrices(stockId, refresh);
    }
}
