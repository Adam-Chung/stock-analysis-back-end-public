package com.adamchung.stockanalysis.finmind.sync.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 協調從 FinMind 抓取資料、依 DB-01 5. 的讀取/更新流程存進資料庫的整體順序。兩個入口方法對應
 * 流程裡的兩種觸發情境：{@link #fullInitSync} 對應從未同步過的股票，{@link #incrementalUpdate}
 * 對應手動按下「更新」按鈕。更新今日最新價（DEC-18）是另一個較輕量的獨立關注點，由每次進頁面
 * 都會呼叫的 {@link #refreshLatestPriceIfStale} 處理。
 *
 * <p>實際「抓取＋聚合＋upsert」的每個資料集動作，委派給 {@link FinMindDatasetSyncExecutor}——
 * 這個類別的 {@link #fullInitSync}／{@link #incrementalUpdate} 刻意不再標註方法層級的
 * {@code @Transactional}，讓委派出去的每個資料集透過 Spring 代理各自獨立 commit，任一資料集
 * 失敗都不會回滾其他已經成功、已經耗用 FinMind 額度的資料集（見 docs/agent/DECISIONS.md 對應
 * 決策：如果整個方法包在同一個交易裡，最後一步的失敗會連同前面已成功、成本最高的步驟一起回滾，
 * 下次重試就得整套重新抓取，重複燒掉額度）。
 */
@Service
public class FinMindSyncService {

    private static final int TEN_YEAR_WINDOW = 10;

    private final FinMindClient finMindClient;
    private final StockRepository stockRepository;
    private final StockPriceYearlyRepository priceYearlyRepository;
    private final FinmindSyncLogRepository syncLogRepository;
    private final FinMindDatasetSyncExecutor datasetSyncExecutor;

    public FinMindSyncService(
            FinMindClient finMindClient,
            StockRepository stockRepository,
            StockPriceYearlyRepository priceYearlyRepository,
            FinmindSyncLogRepository syncLogRepository,
            FinMindDatasetSyncExecutor datasetSyncExecutor) {
        this.finMindClient = finMindClient;
        this.stockRepository = stockRepository;
        this.priceYearlyRepository = priceYearlyRepository;
        this.syncLogRepository = syncLogRepository;
        this.datasetSyncExecutor = datasetSyncExecutor;
    }

    /**
     * DB-01 5. 進頁面流程第2步：這支股票完全沒有任何 finmind_sync_log 紀錄時回傳 {@code true}。
     *
     * @param stockId 股票代號
     * @return 是否從未同步過
     */
    public boolean hasSyncHistory(String stockId) {
        return !syncLogRepository.findByStockId(stockId).isEmpty();
    }

    /**
     * 股票第一次同步：Price/PER 抓近10個完整曆年的區間（一次區間查詢，於應用程式端聚合成
     * 年度統計），Dividend/FinancialStatements 抓全部歷史。四個資料集各自委派給
     * {@link FinMindDatasetSyncExecutor}、各自獨立 commit。
     *
     * @param stockId 股票代號
     */
    public void fullInitSync(String stockId) {
        ensureStockExists(stockId);
        int currentYear = LocalDate.now().getYear();
        LocalDate start = LocalDate.of(currentYear - TEN_YEAR_WINDOW, 1, 1);
        LocalDate end = LocalDate.of(currentYear - 1, 12, 31);
        datasetSyncExecutor.syncYearlyPrice(stockId, start, end, currentYear);
        datasetSyncExecutor.syncYearlyValuation(stockId, start, end, currentYear);
        datasetSyncExecutor.syncDividends(stockId);
        datasetSyncExecutor.syncFinancialStatements(stockId);
    }

    /**
     * 手動「更新」按鈕（DB-01 DEC-20）：Price/PER 只抓差異區間（上次完整年度+1 到今天），
     * Dividend/FinancialStatements 整批重抓。絕不會動到 stock_target_price_setting。四個資料集
     * 各自委派給 {@link FinMindDatasetSyncExecutor}、各自獨立 commit。
     *
     * @param stockId 股票代號
     */
    public void incrementalUpdate(String stockId) {
        ensureStockExists(stockId);
        int currentYear = LocalDate.now().getYear();
        int lastCompleteYear = lastCompleteYear(stockId, currentYear);
        LocalDate start = LocalDate.of(lastCompleteYear + 1, 1, 1);
        LocalDate end = LocalDate.now();
        datasetSyncExecutor.syncYearlyPrice(stockId, start, end, currentYear);
        datasetSyncExecutor.syncYearlyValuation(stockId, start, end, currentYear);
        datasetSyncExecutor.syncDividends(stockId);
        datasetSyncExecutor.syncFinancialStatements(stockId);
    }

    /**
     * DB-01 DEC-18：同一支股票每個曆日只會打一次 FinMind。這次呼叫抓的是整個當年度至今
     * （不只是回顧幾天的區間），這樣同一次呼叫也能順便更新 current_year_high_price/
     * current_year_low_price——stock_price_yearly 永遠不會存當年度（DEC-24），沒有這個機制的
     * 話，今年創的新高/新低在年度結束前都不會反映在 10年高/10年低 上。永遠從當年度1月1日開始
     * 查詢，讓這個機制在跨年時自動重置，不需要額外的重置邏輯。
     *
     * @param stockId 股票代號
     */
    @Transactional
    public void refreshLatestPriceIfStale(String stockId) {
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stock_id: " + stockId));

        LocalDate today = LocalDate.now();
        if (today.equals(stock.getLatestPriceDate())) {
            return;
        }

        List<FinMindStockPriceDto> yearToDate =
                finMindClient.fetchStockPrice(stockId, LocalDate.of(today.getYear(), 1, 1), today);

        yearToDate.stream()
                .filter(p -> p.close() != null)
                .max(Comparator.comparing(FinMindStockPriceDto::date))
                .ifPresent(latest -> {
                    stock.setLatestClosePrice(latest.close());
                    stock.setLatestPriceDate(latest.date());
                    yearToDate.stream()
                            .map(FinMindStockPriceDto::max)
                            .filter(Objects::nonNull)
                            .max(BigDecimal::compareTo)
                            .ifPresent(stock::setCurrentYearHighPrice);
                    yearToDate.stream()
                            .map(FinMindStockPriceDto::min)
                            .filter(Objects::nonNull)
                            .min(BigDecimal::compareTo)
                            .ifPresent(stock::setCurrentYearLowPrice);
                    stock.setUpdatedBy(AuditActors.FINMIND_SYNC);
                    stockRepository.save(stock);
                });
    }

    /**
     * 確保 {@code stock} 表已有這支股票的基本資料，沒有的話就委派給
     * {@link FinMindDatasetSyncExecutor#syncProfile} 從 FinMind 抓一次建立。
     *
     * @param stockId 股票代號
     */
    private void ensureStockExists(String stockId) {
        if (stockRepository.existsById(stockId)) {
            return;
        }
        datasetSyncExecutor.syncProfile(stockId);
    }

    /**
     * 找出這支股票已存的最後一個完整年度，供增量更新計算差異區間的起點。
     *
     * @param stockId 股票代號
     * @param currentYear 當前年度
     * @return 最後一個完整年度；從未同步過時回傳 fullInitSync 會用的同一個起始年度
     */
    private int lastCompleteYear(String stockId, int currentYear) {
        List<StockPriceYearlyEntity> yearlyPrices =
                priceYearlyRepository.findByIdStockIdOrderByIdYearDesc(stockId);
        Integer earliestInvalidYear = yearlyPrices.stream()
                .filter(this::hasInvalidYearlyPrice)
                .map(row -> row.getId().getYear())
                .min(Integer::compareTo)
                .orElse(null);
        if (earliestInvalidYear != null) {
            // 從異常年度重新抓取，讓 upsert 以有效的 FinMind 聚合結果覆寫既有 0 值。
            return earliestInvalidYear - 1;
        }
        return yearlyPrices.stream()
                .map(p -> p.getId().getYear())
                .max(Integer::compareTo)
                // 從未同步過：退回跟 fullInitSync 一樣的區間
                .orElse(currentYear - TEN_YEAR_WINDOW - 1);
    }

    /** 判斷既有年度價格是否包含 null、0 或負數，這類資料需要從該年度重新同步。 */
    private boolean hasInvalidYearlyPrice(StockPriceYearlyEntity row) {
        return !isPositive(row.getAvgClosePrice())
                || !isPositive(row.getHighPrice())
                || !isPositive(row.getLowPrice());
    }

    /** 判斷價格是否存在且大於 0。 */
    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
