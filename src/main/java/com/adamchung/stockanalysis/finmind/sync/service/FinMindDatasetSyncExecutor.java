package com.adamchung.stockanalysis.finmind.sync.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockDividendEntity;
import com.adamchung.stockanalysis.stock.entity.StockFinancialStatementEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockValuationDto;
import com.adamchung.stockanalysis.finmind.sync.FinMindDataset;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogEntity;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.finmind.sync.calculator.DividendYearResolver;
import com.adamchung.stockanalysis.finmind.sync.calculator.PriceYearlyAggregator;
import com.adamchung.stockanalysis.finmind.sync.calculator.ValuationYearlyAggregator;
import com.adamchung.stockanalysis.stock.repository.StockDividendRepository;
import com.adamchung.stockanalysis.stock.repository.StockFinancialStatementRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 執行單一 FinMind 資料集的「抓取＋聚合＋upsert」，每個方法各自獨立 commit。
 *
 * <p>拆成獨立方法（而不是像過去 {@code fullInitSync} 那樣把多個資料集包在同一個交易裡）是
 * 為了貫徹 docs/agent/DECISIONS.md 對應決策的精神：任一資料集的 FinMind 呼叫失敗時，不應該
 * 連帶回滾其他已經成功、已經耗用 FinMind 額度的資料集。呼叫端（{@link FinMindSyncService}、
 * {@code com.adamchung.stockanalysis.stock} 套件下的分階段驗證端點）必須透過 Spring 代理呼叫
 * 這個 bean 的 public 方法，才能讓每個方法各自的 {@code @Transactional} 生效——類別內部互相
 * 呼叫（self-invocation）會繞過 Spring AOP proxy，不會產生獨立交易。
 */
@Service
public class FinMindDatasetSyncExecutor {

    private static final int FETCH_LOOKBACK_YEARS = 11;
    private static final String EPS_TYPE = "EPS";

    private final FinMindClient finMindClient;
    private final StockRepository stockRepository;
    private final StockPriceYearlyRepository priceYearlyRepository;
    private final StockValuationYearlyRepository valuationYearlyRepository;
    private final StockDividendRepository dividendRepository;
    private final StockFinancialStatementRepository financialStatementRepository;
    private final FinmindSyncLogRepository syncLogRepository;

    public FinMindDatasetSyncExecutor(
            FinMindClient finMindClient,
            StockRepository stockRepository,
            StockPriceYearlyRepository priceYearlyRepository,
            StockValuationYearlyRepository valuationYearlyRepository,
            StockDividendRepository dividendRepository,
            StockFinancialStatementRepository financialStatementRepository,
            FinmindSyncLogRepository syncLogRepository) {
        this.finMindClient = finMindClient;
        this.stockRepository = stockRepository;
        this.priceYearlyRepository = priceYearlyRepository;
        this.valuationYearlyRepository = valuationYearlyRepository;
        this.dividendRepository = dividendRepository;
        this.financialStatementRepository = financialStatementRepository;
        this.syncLogRepository = syncLogRepository;
    }

    /**
     * 從 TaiwanStockInfo 抓取並 upsert 股票基本資料，永遠覆寫（不像 {@link FinMindSyncService}
     * 的存在性檢查只在缺資料時才呼叫）。
     *
     * @param stockId 股票代號
     * @return upsert 後的股票實體
     * @throws FinMindClientException FinMind 查無此股票時拋出
     */
    @Transactional
    public StockEntity syncProfile(String stockId) {
        FinMindStockInfoDto info = finMindClient.fetchStockInfo(stockId).stream()
                .findFirst()
                .orElseThrow(() -> new FinMindClientException(
                        "FinMind has no TaiwanStockInfo row for stock_id=" + stockId));

        StockEntity stock = stockRepository.findById(stockId).orElseGet(StockEntity::new);
        boolean isNew = stock.getStockId() == null;
        stock.setStockId(info.stockId());
        stock.setStockName(info.stockName());
        stock.setIndustryCategory(info.industryCategory());
        stock.setMarketType(info.type());
        if (isNew) {
            stock.setCreatedBy(AuditActors.FINMIND_SYNC);
        }
        stock.setUpdatedBy(AuditActors.FINMIND_SYNC);
        return stockRepository.save(stock);
    }

    /**
     * 同步指定區間的年度股價統計。
     *
     * @param stockId 股票代號
     * @param start 查詢起始日
     * @param end 查詢結束日
     * @param currentYear 當前年度，用來排除尚未結束的年度
     * @return 這次 upsert 的年度統計（依年份遞增排序）
     */
    @Transactional
    public List<YearlyPriceStats> syncYearlyPrice(String stockId, LocalDate start, LocalDate end, int currentYear) {
        List<FinMindStockPriceDto> dailyPrices = finMindClient.fetchStockPrice(stockId, start, end);
        List<YearlyPriceStats> yearlyPrices = PriceYearlyAggregator.aggregate(dailyPrices).stream()
                .filter(p -> p.year() < currentYear) // 進行中的當年度絕不落地存表
                .toList();
        yearlyPrices.forEach(stats -> upsertPriceYearly(stockId, stats));
        upsertSyncLog(
                stockId,
                FinMindDataset.TaiwanStockPrice,
                maxYearEnd(yearlyPrices.stream().map(YearlyPriceStats::year).toList()));
        return yearlyPrices;
    }

    /**
     * 同步指定區間的年度估值統計。
     *
     * @param stockId 股票代號
     * @param start 查詢起始日
     * @param end 查詢結束日
     * @param currentYear 當前年度，用來排除尚未結束的年度
     * @return 這次 upsert 的年度統計（依年份遞增排序）
     */
    @Transactional
    public List<YearlyValuationStats> syncYearlyValuation(
            String stockId, LocalDate start, LocalDate end, int currentYear) {
        List<FinMindStockValuationDto> dailyValuations = finMindClient.fetchStockValuation(stockId, start, end);
        List<YearlyValuationStats> yearlyValuations = ValuationYearlyAggregator.aggregate(dailyValuations).stream()
                .filter(v -> v.year() < currentYear)
                .toList();
        yearlyValuations.forEach(stats -> upsertValuationYearly(stockId, stats));
        upsertSyncLog(
                stockId,
                FinMindDataset.TaiwanStockPER,
                maxYearEnd(yearlyValuations.stream().map(YearlyValuationStats::year).toList()));
        return yearlyValuations;
    }

    /**
     * 同步股利歷史。資料量小，所以每次同步都整批重抓全部歷史（DEC-20）。同一年度若配息（配股）
     * 多次，依公告日由舊到新，把現金/股票股利累加寫進每一筆的
     * {@code cumulativeCashDividend}/{@code cumulativeStockDividend}——年度內最後一筆（公告日
     * 最新）的累積值即為該年度完整總股利，供 {@link com.adamchung.stockanalysis.valuation
     * .calculator.DividendYieldCalculator} 計算殖利率時直接取用，不需要在應用層重新分組加總。
     *
     * @param stockId 股票代號
     * @return upsert 後的股利實體清單
     */
    @Transactional
    public List<StockDividendEntity> syncDividends(String stockId) {
        List<FinMindDividendDto> rows =
                finMindClient.fetchDividend(stockId, LocalDate.now().minusYears(FETCH_LOOKBACK_YEARS), LocalDate.now());
        Map<DividendKey, StockDividendEntity> existingByKey = new HashMap<>();
        dividendRepository.findByStockIdOrderByAnnouncementDateDesc(stockId).forEach(entity ->
                existingByKey.put(
                        new DividendKey(entity.getYear(), entity.getAnnouncementDate()), entity));

        Map<Integer, List<FinMindDividendDto>> byYear = rows.stream()
                .filter(row -> DividendYearResolver.resolve(row) != null && row.announcementDate() != null)
                .collect(Collectors.groupingBy(DividendYearResolver::resolve));

        List<StockDividendEntity> entities = new ArrayList<>();
        for (Map.Entry<Integer, List<FinMindDividendDto>> entry : byYear.entrySet()) {
            int year = entry.getKey();
            List<FinMindDividendDto> yearRows = entry.getValue().stream()
                    .sorted(Comparator.comparing(FinMindDividendDto::announcementDate))
                    .toList();

            BigDecimal cumulativeCash = BigDecimal.ZERO;
            BigDecimal cumulativeStock = BigDecimal.ZERO;
            for (FinMindDividendDto row : yearRows) {
                cumulativeCash = cumulativeCash.add(sumNonNull(row.cashEarningsDistribution(), row.cashStatutorySurplus()));
                cumulativeStock = cumulativeStock.add(sumNonNull(row.stockEarningsDistribution(), row.stockStatutorySurplus()));

                DividendKey key = new DividendKey(year, row.announcementDate());
                StockDividendEntity entity = existingByKey.computeIfAbsent(key, ignored -> new StockDividendEntity());
                boolean isNew = entity.getId() == null;
                entity.setStockId(stockId);
                entity.setYear(year);
                entity.setCashEarningsDistribution(row.cashEarningsDistribution());
                entity.setCashStatutorySurplus(row.cashStatutorySurplus());
                entity.setCashExDividendTradingDate(row.cashExDividendTradingDate());
                entity.setCashDividendPaymentDate(row.cashDividendPaymentDate());
                entity.setStockEarningsDistribution(row.stockEarningsDistribution());
                entity.setStockStatutorySurplus(row.stockStatutorySurplus());
                entity.setStockExDividendTradingDate(row.stockExDividendTradingDate());
                entity.setAnnouncementDate(row.announcementDate());
                entity.setCumulativeCashDividend(cumulativeCash);
                entity.setCumulativeStockDividend(cumulativeStock);
                if (isNew) {
                    entity.setCreatedBy(AuditActors.FINMIND_SYNC);
                }
                entity.setUpdatedBy(AuditActors.FINMIND_SYNC);
                entities.add(entity);
            }
        }
        dividendRepository.saveAll(entities);
        upsertSyncLog(stockId, FinMindDataset.TaiwanStockDividend, null);
        return entities;
    }

    /**
     * 兩個可能為 null 的股利金額相加（同性質欄位合計，例如盈餘分配+公積轉增資），視 null 為 0。
     *
     * @param a 第一個金額，可為 {@code null}
     * @param b 第二個金額，可為 {@code null}
     * @return 合計金額
     */
    private BigDecimal sumNonNull(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.add(right);
    }

    /**
     * 同步季度財報（僅 EPS）。
     *
     * @param stockId 股票代號
     * @return upsert 後的財報實體清單（僅 EPS）
     */
    @Transactional
    public List<StockFinancialStatementEntity> syncFinancialStatements(String stockId) {
        List<FinMindFinancialStatementDto> rows =
                finMindClient.fetchFinancialStatement(
                        stockId, LocalDate.now().minusYears(FETCH_LOOKBACK_YEARS), LocalDate.now());
        Map<LocalDate, StockFinancialStatementEntity> existingByDate = new HashMap<>();
        financialStatementRepository.findByStockIdAndTypeOrderByStatementDateDesc(stockId, EPS_TYPE)
                .forEach(entity -> existingByDate.put(entity.getStatementDate(), entity));
        List<StockFinancialStatementEntity> entities = new ArrayList<>();
        for (FinMindFinancialStatementDto row : rows) {
            if (row.value() == null || row.date() == null || !EPS_TYPE.equals(row.type())) {
                continue;
            }
            StockFinancialStatementEntity entity =
                    existingByDate.computeIfAbsent(row.date(), ignored -> new StockFinancialStatementEntity());
            boolean isNew = entity.getId() == null;
            entity.setStockId(stockId);
            entity.setStatementDate(row.date());
            entity.setType(row.type());
            entity.setValue(row.value());
            entity.setOriginName(row.originName());
            if (isNew) {
                entity.setCreatedBy(AuditActors.FINMIND_SYNC);
            }
            entity.setUpdatedBy(AuditActors.FINMIND_SYNC);
            entities.add(entity);
        }
        financialStatementRepository.saveAll(entities);
        upsertSyncLog(stockId, FinMindDataset.TaiwanStockFinancialStatements, null);
        return entities;
    }

    /**
     * 依 (stock_id, year) upsert 一筆年度股價統計。
     *
     * @param stockId 股票代號
     * @param stats 年度股價統計
     */
    private void upsertPriceYearly(String stockId, YearlyPriceStats stats) {
        StockYearId id = new StockYearId(stockId, stats.year());
        StockPriceYearlyEntity entity = priceYearlyRepository.findById(id).orElseGet(StockPriceYearlyEntity::new);
        boolean isNew = entity.getId() == null;
        entity.setId(id);
        entity.setAvgClosePrice(stats.avgClosePrice());
        entity.setHighPrice(stats.highPrice());
        entity.setLowPrice(stats.lowPrice());
        if (isNew) {
            entity.setCreatedBy(AuditActors.FINMIND_SYNC);
        }
        entity.setUpdatedBy(AuditActors.FINMIND_SYNC);
        priceYearlyRepository.save(entity);
    }

    /**
     * 依 (stock_id, year) upsert 一筆年度估值統計。
     *
     * @param stockId 股票代號
     * @param stats 年度估值統計
     */
    private void upsertValuationYearly(String stockId, YearlyValuationStats stats) {
        StockYearId id = new StockYearId(stockId, stats.year());
        StockValuationYearlyEntity entity = valuationYearlyRepository.findById(id).orElseGet(StockValuationYearlyEntity::new);
        boolean isNew = entity.getId() == null;
        entity.setId(id);
        entity.setAvgPer(stats.avgPer());
        entity.setLowPer(stats.lowPer());
        entity.setHighPer(stats.highPer());
        entity.setAvgPbr(stats.avgPbr());
        entity.setLowPbr(stats.lowPbr());
        entity.setHighPbr(stats.highPbr());
        if (isNew) {
            entity.setCreatedBy(AuditActors.FINMIND_SYNC);
        }
        entity.setUpdatedBy(AuditActors.FINMIND_SYNC);
        valuationYearlyRepository.save(entity);
    }

    /**
     * 每次都會 touch updated_at（讓「上次更新時間」反映這次嘗試），即使 newLastDataDate 是
     * null（Dividend/FinancialStatements，或這次 Price/PER 沒有抓到新的完整年度）——這種情況下
     * 既有的 last_data_date 會維持原樣，絕不會被清空。
     *
     * @param stockId 股票代號
     * @param dataset FinMind 資料集
     * @param newLastDataDate 這次抓到的最後完整年度日期；沒有新完整年度時傳 {@code null}
     */
    private void upsertSyncLog(String stockId, FinMindDataset dataset, LocalDate newLastDataDate) {
        FinmindSyncLogEntity log = syncLogRepository.findByStockIdAndDataset(stockId, dataset).orElseGet(FinmindSyncLogEntity::new);
        boolean isNew = log.getId() == null;
        log.setStockId(stockId);
        log.setDataset(dataset);
        if (newLastDataDate != null) {
            log.setLastDataDate(newLastDataDate);
        }
        log.setStatus("ok");
        if (isNew) {
            log.setCreatedBy(AuditActors.FINMIND_SYNC);
        }
        log.setUpdatedBy(AuditActors.FINMIND_SYNC);
        // 即使資料內容沒有變化，也要讓畫面上的「上次更新時間」反映本次成功同步。
        log.setUpdatedAt(Instant.now());
        syncLogRepository.save(log);
    }

    /**
     * 取年份清單中的最大值，轉成該年度 12/31 的日期。
     *
     * @param years 年份清單
     * @return 最大年份的 12/31；清單為空時回傳 {@code null}
     */
    private LocalDate maxYearEnd(List<Integer> years) {
        return years.stream().max(Integer::compareTo).map(y -> LocalDate.of(y, 12, 31)).orElse(null);
    }

    private record DividendKey(
            Integer year, // 年度
            LocalDate announcementDate // 公告日期
    ) {
    }
}
