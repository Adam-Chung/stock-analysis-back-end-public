package com.adamchung.stockanalysis.targetprice.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.finmind.sync.FinMindDataset;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogEntity;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceSettingsRequest;
import com.adamchung.stockanalysis.targetprice.entity.StockAiSuggestionEntity;
import com.adamchung.stockanalysis.targetprice.entity.StockTargetPriceSettingEntity;
import com.adamchung.stockanalysis.targetprice.repository.StockAiSuggestionRepository;
import com.adamchung.stockanalysis.targetprice.repository.StockTargetPriceSettingRepository;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockDividendEntity;
import com.adamchung.stockanalysis.stock.entity.StockFinancialStatementEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.stock.repository.StockDividendRepository;
import com.adamchung.stockanalysis.stock.repository.StockFinancialStatementRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import com.adamchung.stockanalysis.finmind.sync.service.FinMindSyncService;
import com.adamchung.stockanalysis.valuation.calculator.DividendYieldCalculator;
import com.adamchung.stockanalysis.valuation.calculator.EpsAggregator;
import com.adamchung.stockanalysis.valuation.InsufficientValuationDataException;
import com.adamchung.stockanalysis.valuation.dto.QuarterlyEps;
import com.adamchung.stockanalysis.valuation.TargetPriceValuationService;
import com.adamchung.stockanalysis.valuation.dto.ValuationInput;
import com.adamchung.stockanalysis.valuation.dto.ValuationResult;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/** 統整目標價 API 所需的同步、落地儲存與純運算估值。 */
@Service
public class TargetPriceApplicationService {

    private static final String EPS_TYPE = "EPS";
    private static final int TEN_YEAR_WINDOW = 10;
    private static final Set<Integer> ALLOWED_AVERAGE_WINDOWS = Set.of(0, 3, 5, 10);
    private static final EnumSet<FinMindDataset> SCREEN_DATASETS = EnumSet.of(
            FinMindDataset.TaiwanStockPrice,
            FinMindDataset.TaiwanStockPER,
            FinMindDataset.TaiwanStockDividend,
            FinMindDataset.TaiwanStockFinancialStatements);

    private final FinMindSyncService syncService;
    private final TargetPriceValuationService valuationService;
    private final StockRepository stockRepository;
    private final StockTargetPriceSettingRepository settingRepository;
    private final StockPriceYearlyRepository priceRepository;
    private final StockValuationYearlyRepository valuationRepository;
    private final StockFinancialStatementRepository financialStatementRepository;
    private final StockDividendRepository dividendRepository;
    private final FinmindSyncLogRepository syncLogRepository;
    private final StockAiSuggestionRepository aiSuggestionRepository;

    /**
     * 建立目標價使用案例服務，注入所有需要的資料邊界。
     *
     * @param syncService FinMind 同步服務
     * @param valuationService 純運算估值服務
     * @param stockRepository 股票主檔 repository
     * @param settingRepository 目標價設定 repository
     * @param priceRepository 年度股價統計 repository
     * @param valuationRepository 年度估值統計 repository
     * @param financialStatementRepository 財報 repository
     * @param dividendRepository 股利 repository
     * @param syncLogRepository FinMind 同步紀錄 repository
     * @param aiSuggestionRepository AI 建議 repository
     */
    public TargetPriceApplicationService(
            FinMindSyncService syncService,
            TargetPriceValuationService valuationService,
            StockRepository stockRepository,
            StockTargetPriceSettingRepository settingRepository,
            StockPriceYearlyRepository priceRepository,
            StockValuationYearlyRepository valuationRepository,
            StockFinancialStatementRepository financialStatementRepository,
            StockDividendRepository dividendRepository,
            FinmindSyncLogRepository syncLogRepository,
            StockAiSuggestionRepository aiSuggestionRepository) {
        this.syncService = syncService;
        this.valuationService = valuationService;
        this.stockRepository = stockRepository;
        this.settingRepository = settingRepository;
        this.priceRepository = priceRepository;
        this.valuationRepository = valuationRepository;
        this.financialStatementRepository = financialStatementRepository;
        this.dividendRepository = dividendRepository;
        this.syncLogRepository = syncLogRepository;
        this.aiSuggestionRepository = aiSuggestionRepository;
    }

    /**
     * 載入單一畫面：需要時先初始化歷史資料，並更新今日成交價。
     *
     * <p>刻意在這一層不加 {@code @Transactional}（見 DECISIONS.md DEC-020）：
     * {@link FinMindSyncService#fullInitSync}／{@link FinMindSyncService#refreshLatestPriceIfStale}
     * 各自獨立 commit。若這個方法把兩者包進同一個交易，之後任何失敗（例如較小的最新價查詢
     * 遇到 FinMind 額度錯誤，或組裝回應時拋出
     * {@link com.adamchung.stockanalysis.valuation.InsufficientValuationDataException}）
     * 都會連同已經抓回來、耗費額度的完整歷史同步一起回滾——導致下次請求得從頭重抓一次，
     * 白白多耗一次 FinMind 額度。
     *
     * @param stockId 台股代號
     * @return 完整畫面回應
     */
    public TargetPriceResponse get(String stockId) {
        if (!hasCompleteScreenSyncHistory(stockId)) {
            syncService.fullInitSync(stockId);
        }
        syncService.refreshLatestPriceIfStale(stockId);
        return assemble(stockId);
    }

    /**
     * 只落地儲存 SA-01 使用者設定，然後重新計算回應。
     *
     * @param stockId 台股代號
     * @param request 已驗證的設定內容
     * @return 重新計算後的畫面回應
     */
    @Transactional
    public TargetPriceResponse updateSettings(String stockId, TargetPriceSettingsRequest request) {
        validateAverageWindow("epsAvgYears", request.epsAvgYears());
        validateAverageWindow("bpsAvgYears", request.bpsAvgYears());
        if (request.epsAvgYears() == 0 && request.bpsAvgYears() == 0) {
            throw new IllegalArgumentException("EPS and BPS cannot both be excluded from valuation");
        }
        if (!stockRepository.existsById(stockId)) {
            throw new NoSuchElementException("Unknown stock_id: " + stockId);
        }

        StockTargetPriceSettingEntity setting = settingRepository.findById(stockId)
                .orElseGet(StockTargetPriceSettingEntity::new);
        boolean isNew = setting.getStockId() == null;
        setting.setStockId(stockId);
        setting.setInvestmentStrategy(request.investmentStrategy());
        setting.setEpsAvgYears(request.epsAvgYears().shortValue());
        setting.setBpsAvgYears(request.bpsAvgYears().shortValue());
        setting.setSettingNote(request.settingNote());
        setting.setTargetPriceLevel(request.targetPriceLevel().shortValue());
        if (isNew) {
            setting.setCreatedBy(AuditActors.DEFAULT_USER);
        }
        setting.setUpdatedBy(AuditActors.DEFAULT_USER);
        settingRepository.save(setting);
        return assemble(stockId);
    }

    /**
     * 執行手動觸發的 FinMind 差量更新，不變動使用者設定。
     *
     * <p>同樣刻意在這一層不加 {@code @Transactional}——理由見 {@link #get} 的 Javadoc
     * （DECISIONS.md DEC-020）。{@code incrementalUpdate} 會自行獨立 commit。
     *
     * @param stockId 台股代號
     * @return 重新計算後的畫面回應
     */
    public TargetPriceResponse refresh(String stockId) {
        syncService.incrementalUpdate(stockId);
        syncService.refreshLatestPriceIfStale(stockId);
        return assemble(stockId);
    }

    /**
     * 讀取目前已落地的畫面數值，不呼叫 FinMind。
     *
     * @param stockId 台股代號
     * @return 完全來自本地資料庫的完整畫面回應
     */
    @Transactional(readOnly = true)
    public TargetPriceResponse getPersisted(String stockId) {
        return assemble(stockId);
    }

    /**
     * 完全依據已落地的資料組裝回應。
     *
     * @param stockId 台股代號
     * @return 完整畫面回應
     */
    private TargetPriceResponse assemble(String stockId) {
        StockEntity stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Unknown stock_id: " + stockId));
        if (stock.getLatestClosePrice() == null) {
            throw new InsufficientValuationDataException("No latest close price available for stock_id=" + stockId);
        }

        StockTargetPriceSettingEntity setting = settingRepository.findById(stockId)
                .orElseGet(this::defaultSetting);
        List<StockPriceYearlyEntity> prices = priceRepository.findByIdStockIdOrderByIdYearDesc(stockId);
        List<StockValuationYearlyEntity> valuations =
                valuationRepository.findByIdStockIdOrderByIdYearDesc(stockId);
        List<StockFinancialStatementEntity> epsRows =
                financialStatementRepository.findByStockIdAndTypeOrderByStatementDateDesc(stockId, EPS_TYPE);
        List<StockDividendEntity> dividends = dividendRepository.findByStockIdOrderByAnnouncementDateDesc(stockId);

        Map<Integer, BigDecimal> avgClosePriceByYear = prices.stream()
                .collect(Collectors.toMap(row -> row.getId().getYear(), StockPriceYearlyEntity::getAvgClosePrice));
        Map<Integer, StockDividendEntity> latestDividendByYear = latestDividendPerYear(dividends);

        ValuationResult result = valuationService.calculate(new ValuationInput(
                stock.getLatestClosePrice(),
                setting.getEpsAvgYears(),
                setting.getBpsAvgYears(),
                setting.getTargetPriceLevel(),
                prices.stream().map(this::toPriceStats).toList(),
                valuations.stream()
                        .map(row -> toValuationStats(row, avgClosePriceByYear, latestDividendByYear))
                        .toList(),
                EpsAggregator.toAnnual(epsRows.stream()
                        .map(row -> new QuarterlyEps(row.getStatementDate(), row.getValue()))
                        .toList()),
                stock.getCurrentYearHighPrice(),
                stock.getCurrentYearLowPrice()));

        StockAiSuggestionEntity suggestion = aiSuggestionRepository.findById(stockId).orElse(null);
        TargetPriceResponse.AiSuggestionView suggestionView = suggestion == null
                ? null
                : new TargetPriceResponse.AiSuggestionView(
                        suggestion.getSuggestionText(), suggestion.getUpdatedAt());

        return new TargetPriceResponse(
                stock.getStockId(),
                stock.getStockName(),
                stock.getIndustryCategory(),
                stock.getLatestClosePrice(),
                stock.getLatestPriceDate(),
                result.low10(),
                result.high10(),
                result.tenYearAvgYield(),
                resolveDividendDate(dividends),
                resolveLastUpdated(syncLogRepository.findByStockId(stockId)),
                result.tiers(),
                result.targetPrice(),
                result.gapRatio(),
                result.plusOneYearDividendPrice(),
                result.yearsToRecover(),
                result.yearsOfProfitIfSold(),
                result.frontYear(),
                setting.getInvestmentStrategy(),
                setting.getEpsAvgYears(),
                setting.getBpsAvgYears(),
                setting.getSettingNote(),
                setting.getTargetPriceLevel(),
                result.dataCoverage(),
                suggestionView,
                yearlyLowPrices(prices, stock.getCurrentYearLowPrice()));
    }

    /**
     * 為尚未儲存設定的股票建立不落地的預設值。
     *
     * @return 預設的目標價設定
     */
    private StockTargetPriceSettingEntity defaultSetting() {
        StockTargetPriceSettingEntity setting = new StockTargetPriceSettingEntity();
        setting.setSettingNote("EPS取近5年平均、BPS取近5年平均");
        return setting;
    }

    /**
     * 將年度股價實體映射到計算邊界所需的型別。
     *
     * @param row 年度股價實體
     * @return 計算用的年度股價統計
     */
    private YearlyPriceStats toPriceStats(StockPriceYearlyEntity row) {
        return new YearlyPriceStats(
                row.getId().getYear(), row.getAvgClosePrice(), row.getHighPrice(), row.getLowPrice());
    }

    /**
     * 組出「近10個完整年度＋當年度（若有即時最低價）」的年度最低價清單，依年份新到舊排序，
     * 供前端在使用者改變「目標價選擇」下拉選單時，不必重新呼叫後端即可即時重算「前目標價
     * 出現」——跟 {@link TargetPriceValuationService#calculate} 內部
     * {@code findFrontYear} 用的資料窗口與優先序完全一致。
     *
     * @param prices 該股票的完整年度股價實體
     * @param currentYearLowPrice 當年度至今的即時最低價，可為 {@code null}
     * @return 依年份新到舊排序的年度最低價清單
     */
    private List<TargetPriceResponse.YearlyLowPrice> yearlyLowPrices(
            List<StockPriceYearlyEntity> prices, BigDecimal currentYearLowPrice) {
        List<TargetPriceResponse.YearlyLowPrice> result = prices.stream()
                .sorted(Comparator.comparingInt((StockPriceYearlyEntity row) -> row.getId().getYear()).reversed())
                .limit(TEN_YEAR_WINDOW)
                .map(row -> new TargetPriceResponse.YearlyLowPrice(row.getId().getYear(), row.getLowPrice()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (currentYearLowPrice != null) {
            result.addFirst(new TargetPriceResponse.YearlyLowPrice(LocalDate.now().getYear(), currentYearLowPrice));
        }
        return result;
    }

    /**
     * 將年度估值實體映射到計算邊界所需的型別。殖利率不是資料表欄位，是用該年度均價與該年度
     * 累積股利，透過 {@link DividendYieldCalculator} 當下即時算出來的（見對應決策紀錄）。
     *
     * @param row 年度估值實體
     * @param avgClosePriceByYear 每個完整年度的均價
     * @param latestDividendByYear 每個完整年度、公告日最新（即累積股利已完整）的股利實體
     * @return 計算用的年度估值統計
     */
    private YearlyValuationStats toValuationStats(
            StockValuationYearlyEntity row,
            Map<Integer, BigDecimal> avgClosePriceByYear,
            Map<Integer, StockDividendEntity> latestDividendByYear) {
        int year = row.getId().getYear();
        StockDividendEntity latestDividend = latestDividendByYear.get(year);
        BigDecimal avgDividendYield = DividendYieldCalculator.calculate(
                avgClosePriceByYear.get(year),
                latestDividend == null ? null : latestDividend.getCumulativeCashDividend(),
                latestDividend == null ? null : latestDividend.getCumulativeStockDividend());
        return new YearlyValuationStats(
                year,
                row.getAvgPer(),
                row.getLowPer(),
                row.getHighPer(),
                row.getAvgPbr(),
                row.getLowPbr(),
                row.getHighPbr(),
                avgDividendYield);
    }

    /**
     * 依年度分組，取每個年度公告日最新的一筆股利實體——其累積股利即為該年度完整總股利。
     *
     * @param dividends 該股票的股利紀錄
     * @return 年度 → 該年度公告日最新的股利實體
     */
    private Map<Integer, StockDividendEntity> latestDividendPerYear(List<StockDividendEntity> dividends) {
        return dividends.stream()
                .filter(d -> d.getYear() != null && d.getAnnouncementDate() != null)
                .collect(Collectors.toMap(
                        StockDividendEntity::getYear,
                        d -> d,
                        (a, b) -> a.getAnnouncementDate().isAfter(b.getAnnouncementDate()) ? a : b));
    }

    /**
     * 實作 SA-01 DEC-14：優先取最近的未來除息日，否則退回最近的過去除息日。
     *
     * @param dividends 該股票的股利紀錄
     * @return 決議出的股利發日；找不到任何除息日時回傳 {@code null}
     */
    private LocalDate resolveDividendDate(List<StockDividendEntity> dividends) {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = dividends.stream()
                .map(StockDividendEntity::getCashExDividendTradingDate)
                .filter(date -> date != null)
                .toList();
        return dates.stream()
                .filter(date -> !date.isBefore(today))
                .min(LocalDate::compareTo)
                .orElseGet(() -> dates.stream()
                        .filter(date -> date.isBefore(today))
                        .max(LocalDate::compareTo)
                        .orElse(null));
    }

    /**
     * 依 SA-01 DEC-19，回傳畫面所需資料集中最舊的一次成功同步時間。
     *
     * @param logs 該股票的 FinMind 同步紀錄
     * @return 最保守（最舊）的成功同步時間；尚未完整同步時回傳 {@code null}
     */
    private Instant resolveLastUpdated(List<FinmindSyncLogEntity> logs) {
        List<FinmindSyncLogEntity> successfulScreenLogs = logs.stream()
                .filter(log -> SCREEN_DATASETS.contains(log.getDataset()))
                .filter(log -> "ok".equals(log.getStatus()))
                .toList();
        long coveredDatasets = successfulScreenLogs.stream()
                .map(FinmindSyncLogEntity::getDataset)
                .distinct()
                .count();
        if (coveredDatasets != SCREEN_DATASETS.size()) {
            return null;
        }
        return successfulScreenLogs.stream()
                .map(FinmindSyncLogEntity::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * 需要四個畫面資料集全部同步成功，才視為初始化已完成。
     *
     * @param stockId 台股代號
     * @return 四個畫面資料集是否皆已成功同步過
     */
    private boolean hasCompleteScreenSyncHistory(String stockId) {
        return syncLogRepository.findByStockId(stockId).stream()
                .filter(log -> SCREEN_DATASETS.contains(log.getDataset()))
                .filter(log -> "ok".equals(log.getStatus()))
                .map(FinmindSyncLogEntity::getDataset)
                .distinct()
                .count() == SCREEN_DATASETS.size();
    }

    /**
     * 強制限定為 3／5／10 年的離散選項，這是 Bean Validation 的範圍驗證無法表達的規則。
     *
     * @param field 欄位名稱，用於錯誤訊息
     * @param years 使用者輸入的年數
     * @throws IllegalArgumentException 年數不是 0、3、5、10 其中之一時拋出
     */
    private void validateAverageWindow(String field, Integer years) {
        if (years == null || !ALLOWED_AVERAGE_WINDOWS.contains(years)) {
            throw new IllegalArgumentException(field + " must be one of 0, 3, 5, 10");
        }
    }
}
