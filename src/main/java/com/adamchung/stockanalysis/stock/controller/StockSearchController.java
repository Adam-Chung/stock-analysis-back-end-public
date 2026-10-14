package com.adamchung.stockanalysis.stock.controller;

import com.adamchung.stockanalysis.stock.dto.StockDirectorySyncResponse;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.service.StockDirectorySyncService;
import com.adamchung.stockanalysis.stock.service.StockSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 股票搜尋（DB-01 8.3）的 REST 端點。 */
@RestController
@RequestMapping("/api/stocks")
@Tag(name = "股票搜尋", description = "依代號或名稱關鍵字查詢本地股票目錄")
public class StockSearchController {

    private final StockSearchService stockSearchService;
    private final StockDirectorySyncService directorySyncService;

    public StockSearchController(
            StockSearchService stockSearchService, StockDirectorySyncService directorySyncService) {
        this.stockSearchService = stockSearchService;
        this.directorySyncService = directorySyncService;
    }

    /**
     * 依代號或名稱關鍵字搜尋股票。只查本地目錄，不會呼叫 FinMind。
     *
     * @param query 搜尋關鍵字
     * @return 符合條件的股票摘要清單
     */
    @Operation(summary = "搜尋股票")
    @GetMapping("/search")
    public List<StockSummary> search(@RequestParam("q") String query) {
        return stockSearchService.search(query);
    }

    /**
     * 手動同步整個股票目錄（從 FinMind 全市場清單重新批次寫入）。目錄同步不再隨搜尋請求自動
     * 觸發，需要更新目錄（例如首次建置、或有新股上市/更名）時才呼叫這個端點。
     *
     * @return 這次同步實際寫入的股票筆數
     */
    @Operation(summary = "手動同步股票目錄", description = "從 FinMind 全市場清單重新批次寫入，供首次建置或補資料使用")
    @PostMapping("/directory-sync")
    public StockDirectorySyncResponse syncDirectory() {
        return new StockDirectorySyncResponse(directorySyncService.syncDirectory());
    }
}
