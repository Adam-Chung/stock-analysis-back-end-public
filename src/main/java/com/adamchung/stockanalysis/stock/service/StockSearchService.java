package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.stock.controller.StockSearchController;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DB-01 8.3：搜尋股票——永遠只查本地目錄，不會呼叫 FinMind。目錄本身的同步改由使用者手動觸發
 * （見 {@link StockSearchController#syncDirectory()}），避免讓某一次搜尋請求意外扛下第一次
 * 建立整個市場目錄（約4,300筆）的延遲。
 */
@Service
public class StockSearchService {

    private static final int MAX_RESULTS = 20;

    private final StockRepository stockRepository;

    public StockSearchService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 依代號或名稱關鍵字搜尋股票。
     *
     * @param query 搜尋關鍵字
     * @return 符合條件的股票摘要清單，最多回傳 {@value #MAX_RESULTS} 筆
     */
    public List<StockSummary> search(String query) {
        return stockRepository
                .findByStockIdContainingOrStockNameContainingOrderByStockId(
                        query, query, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(s -> new StockSummary(s.getStockId(), s.getStockName(), s.getIndustryCategory()))
                .toList();
    }
}
