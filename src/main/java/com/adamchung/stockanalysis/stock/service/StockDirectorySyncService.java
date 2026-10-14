package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import com.adamchung.stockanalysis.stock.StockDirectoryBatchWriter;
import com.adamchung.stockanalysis.stock.StockDirectoryDeduplicator;
import com.adamchung.stockanalysis.stock.controller.StockSearchController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 用 FinMind 全市場清單填入本地 `stock` 表的身分欄位（DB-01 8.1／DEC-26），讓搜尋可以查本地
 * 資料，不用每次按鍵都打一次 FinMind。只會更新身分欄位（stock_name/industry_category/
 * market_type）——絕不覆蓋 latest_close_price/latest_price_date，那兩個欄位歸個股的
 * 延遲抓取流程管理（DEC-18）。只透過 {@link StockSearchController#syncDirectory()} 手動觸發，
 * 不會隨搜尋請求自動執行，避免讓使用者的某一次搜尋意外扛下整個市場目錄的同步延遲。
 */
@Service
public class StockDirectorySyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockDirectorySyncService.class);
    private static final int STOCK_ID_MAX_LENGTH = 10;

    private final FinMindClient finMindClient;
    private final StockDirectoryBatchWriter batchWriter;

    public StockDirectorySyncService(FinMindClient finMindClient, StockDirectoryBatchWriter batchWriter) {
        this.finMindClient = finMindClient;
        this.batchWriter = batchWriter;
    }

    /**
     * 同步整個股票目錄：抓取 FinMind 全市場清單、去重、過濾不支援的代號後批次寫入。
     *
     * @return 實際寫入的股票筆數
     */
    @Transactional
    public int syncDirectory() {
        List<FinMindStockInfoDto> rawRows =
                StockDirectoryDeduplicator.keepFirstPerStockId(finMindClient.fetchAllStockInfo());
        List<FinMindStockInfoDto> rows = rawRows.stream()
                .filter(this::hasSupportedStockId)
                .toList();
        int skippedRows = rawRows.size() - rows.size();
        if (skippedRows > 0) {
            LOGGER.warn(
                    "Skipped {} stock-directory rows whose stock_id is blank or longer than {} characters",
                    skippedRows,
                    STOCK_ID_MAX_LENGTH);
        }

        batchWriter.upsertAll(rows);
        return rows.size();
    }

    /**
     * 判斷股票代號是否在支援範圍內（非空白，且長度不超過資料表欄位限制）。
     *
     * @param row 股票基本資料列
     * @return 代號是否可支援
     */
    private boolean hasSupportedStockId(FinMindStockInfoDto row) {
        return row.stockId() != null
                && !row.stockId().isBlank()
                && row.stockId().length() <= STOCK_ID_MAX_LENGTH;
    }
}
