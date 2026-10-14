package com.adamchung.stockanalysis.stock;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FinMind 全市場的 TaiwanStockInfo 清單，可能對同一個 stock_id 回傳多筆、industry_category
 * 不同的資料列（已對 2330 的真實 API 驗證過，見 docs/agent/DECISIONS.md DEC-007）——這裡只保留
 * 每個 stock_id 第一次出現的那一列，順序依 FinMind 回傳的原始順序。獨立拆出來讓去重規則
 * 可以單獨測試。
 */
public final class StockDirectoryDeduplicator {

    private StockDirectoryDeduplicator() {
    }

    /**
     * 依 stock_id 去重，保留每個代號第一次出現的資料列。
     *
     * @param rows 原始（可能含重複代號的）股票基本資料列
     * @return 去重後的資料列，順序不變
     */
    public static List<FinMindStockInfoDto> keepFirstPerStockId(List<FinMindStockInfoDto> rows) {
        Set<String> seen = new HashSet<>();
        List<FinMindStockInfoDto> result = new ArrayList<>();
        for (FinMindStockInfoDto row : rows) {
            if (seen.add(row.stockId())) {
                result.add(row);
            }
        }
        return result;
    }
}
