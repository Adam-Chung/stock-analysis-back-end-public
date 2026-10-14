package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.healthcheck.calculator.HealthCheckEvaluator;
import com.adamchung.stockanalysis.healthcheck.calculator.HealthCheckTemplateResolver;
import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckCategoryDefinition;
import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckMetricDefinition;
import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckTemplateDefinitions;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckProfileRequest;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckSnapshotRequest;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthAiSuggestionEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthProfileEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthAiSuggestionRepository;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthProfileRepository;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SA-03 企業體質檢視的應用服務。DEC-030：v1 全部由使用者手動輸入，這裡完全不呼叫 FinMind，
 * 只負責「依模板組出畫面需要的欄位」＋「把使用者送出的數值/筆記存進通用快照表」。
 */
@Service
public class HealthCheckApplicationService {

    private final StockRepository stockRepository;
    private final StockHealthProfileRepository profileRepository;
    private final StockHealthSnapshotRepository snapshotRepository;
    private final StockPriceYearlyRepository priceYearlyRepository;
    private final StockValuationYearlyRepository valuationYearlyRepository;
    private final StockHealthAiSuggestionRepository aiSuggestionRepository;

    public HealthCheckApplicationService(
            StockRepository stockRepository,
            StockHealthProfileRepository profileRepository,
            StockHealthSnapshotRepository snapshotRepository,
            StockPriceYearlyRepository priceYearlyRepository,
            StockValuationYearlyRepository valuationYearlyRepository,
            StockHealthAiSuggestionRepository aiSuggestionRepository) {
        this.stockRepository = stockRepository;
        this.profileRepository = profileRepository;
        this.snapshotRepository = snapshotRepository;
        this.priceYearlyRepository = priceYearlyRepository;
        this.valuationYearlyRepository = valuationYearlyRepository;
        this.aiSuggestionRepository = aiSuggestionRepository;
    }

    /**
     * 載入完整畫面資料。不觸發任何 FinMind 呼叫——只讀 {@code stock} 目前已知的欄位＋
     * 使用者過往手動輸入的快照。
     *
     * @param stockId 台股代號
     * @return 完整畫面回應
     * @throws NoSuchElementException 股票代號在 {@code stock} 表裡不存在時拋出
     */
    @Transactional(readOnly = true)
    public HealthCheckResponse get(String stockId) {
        StockEntity stock = requireStock(stockId);
        HealthCheckTemplate template = HealthCheckTemplateResolver.resolve(stock.getIndustryCategory());
        StockHealthProfileEntity profile = profileRepository.findById(stockId).orElse(null);

        List<HealthCheckResponse.CategoryView> categories = HealthCheckTemplateDefinitions.categoriesFor(template)
                .stream()
                .map(category -> toCategoryView(stockId, category))
                .toList();

        List<HealthCheckResponse.YearlyPriceTrendView> priceTrend =
                template == HealthCheckTemplate.BANK ? buildPriceTrend(stockId) : List.of();

        return new HealthCheckResponse(
                stock.getStockId(),
                stock.getStockName(),
                stock.getIndustryCategory(),
                template,
                stock.getLatestClosePrice(),
                toProfileView(profile),
                categories,
                priceTrend,
                toAiSuggestionView(stockId));
    }

    /**
     * 更新公司簡介／整體備註，不分季度只保留最新一份（DEC-029）。
     *
     * @param stockId 台股代號
     * @param request 使用者編輯的內容
     * @return 更新後的完整畫面回應
     */
    @Transactional
    public HealthCheckResponse updateProfile(String stockId, HealthCheckProfileRequest request) {
        requireStock(stockId);
        StockHealthProfileEntity profile = loadOrCreateProfile(stockId);
        profile.setProfile(request.profile());
        profile.setCustomNote(request.customNote());
        profile.setUpdatedBy(AuditActors.DEFAULT_USER);
        profileRepository.save(profile);
        return get(stockId);
    }

    /**
     * 「儲存」按鈕：整批更新各細項各自對應期間（季或月，DEC-043）的筆記，並更新
     * {@code stock_health_profile.last_reviewed_quarter}（SA-03 §4.4）。
     *
     * @param stockId 台股代號
     * @param request 這次要更新的細項清單，每筆各自帶 period
     * @return 更新後的完整畫面回應
     * @throws IllegalArgumentException entries 裡出現不屬於這支股票所套用模板的 metric_key
     *         （DEC-039：metric_key 不做資料庫層 FK，改由應用層驗證）
     */
    @Transactional
    public HealthCheckResponse updateSnapshot(String stockId, HealthCheckSnapshotRequest request) {
        StockEntity stock = requireStock(stockId);
        HealthCheckTemplate template = HealthCheckTemplateResolver.resolve(stock.getIndustryCategory());
        Set<String> validKeys = validMetricKeys(template);

        for (HealthCheckSnapshotRequest.Entry entry : request.entries()) {
            if (!validKeys.contains(entry.metricKey())) {
                throw new IllegalArgumentException(
                        "metric_key '" + entry.metricKey() + "' 不屬於這支股票套用的模板（" + template + "）");
            }
            StockHealthSnapshotEntity row = snapshotRepository
                    .findByStockIdAndQuarterAndMetricKey(stockId, entry.period(), entry.metricKey())
                    .orElseGet(() -> newSnapshotRow(stockId, entry.period(), entry.metricKey()));
            row.setNote(entry.note());
            row.setUpdatedBy(AuditActors.DEFAULT_USER);
            snapshotRepository.save(row);
        }

        StockHealthProfileEntity profile = loadOrCreateProfile(stockId);
        profile.setLastReviewedQuarter(request.reviewedQuarter());
        profile.setUpdatedBy(AuditActors.DEFAULT_USER);
        profileRepository.save(profile);

        return get(stockId);
    }

    /**
     * 外部同步（DEC-046~050／DEC-053）任一來源成功後呼叫。「更新本季資料」按鈕本身刻意不夾帶
     * 任何筆記／公司資訊變更（DEC-051），但畫面上的「更新時間」「資料季度」欄位如果永遠停留在
     * 使用者上次手動編輯的時間點，會讓人誤以為按了同步按鈕卻什麼都沒發生——這裡只更新這兩個
     * 欄位本身，不動 {@code profile}／{@code customNote} 內容（DEC-060）。
     *
     * <p>DEC-071：{@code updatedAt} 不能只靠 Hibernate {@code @UpdateTimestamp} 被動生效——
     * 當這次的 {@code reviewedQuarter} 剛好跟上次相同（例如同一天內連按兩次「更新本季資料」，
     * FinMind 最新可用季度沒有變化）、且 {@code updatedBy} 也還是同一個
     * {@link AuditActors#EXTERNAL_SYNC} 常數時，Hibernate 的 dirty checking 會判定這個實體沒有任何
     * 欄位真的變了，直接跳過整個 UPDATE 陳述式，連帶讓 {@code @UpdateTimestamp} 也不會觸發，
     * 使用者會看到「已更新 N 個細項」但「更新時間」卻沒有動。這裡改成明確呼叫
     * {@code profile.setUpdatedAt(Instant.now())} 強制這個實體一定是 dirty 的，確保只要外部
     * 同步成功呼叫到這裡，「更新時間」就一定會反映這次呼叫的時間點（實際寫入的精確值仍由
     * {@code @UpdateTimestamp} 在 flush 時重新產生，這裡的手動賦值只是用來觸發 dirty 判定）。
     *
     * @param stockId 台股代號
     * @param reviewedQuarter 「資料季度」的新值——優先採用 FinMind 財報三表這次實際抓到的
     *                        最新季度，沒有資料時才退回今天所屬的曆季（DEC-067）
     */
    @Transactional
    public void touchProfileAfterExternalSync(String stockId, String reviewedQuarter) {
        requireStock(stockId);
        StockHealthProfileEntity profile = loadOrCreateProfile(stockId);
        profile.setLastReviewedQuarter(reviewedQuarter);
        profile.setUpdatedBy(AuditActors.EXTERNAL_SYNC);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private HealthCheckResponse.CategoryView toCategoryView(String stockId, HealthCheckCategoryDefinition category) {
        List<HealthCheckResponse.MetricView> metrics = category.metrics().stream()
                .map(metric -> toMetricView(stockId, metric, category.historyLimit()))
                .toList();
        return new HealthCheckResponse.CategoryView(category.key(), category.name(), category.thresholdDescription(), metrics);
    }

    private HealthCheckResponse.MetricView toMetricView(String stockId, HealthCheckMetricDefinition metric, int historyLimit) {
        List<HealthCheckResponse.PeriodValueView> periods = snapshotRepository
                .findByStockIdAndMetricKeyOrderByQuarterDesc(stockId, metric.key())
                .stream()
                .limit(historyLimit)
                .map(row -> new HealthCheckResponse.PeriodValueView(
                        row.getQuarter(), row.getValue(), HealthCheckEvaluator.evaluate(metric, row.getValue()), row.getNote()))
                .toList();
        return new HealthCheckResponse.MetricView(
                metric.key(), metric.name(), metric.unit(), metric.evalType(), metric.threshold(), metric.thresholdLabel(),
                metric.isAnnual(), metric.autofillNoteWithValue(), periods);
    }

    private List<HealthCheckResponse.YearlyPriceTrendView> buildPriceTrend(String stockId) {
        Map<Integer, StockValuationYearlyEntity> valuationByYear = valuationYearlyRepository
                .findByIdStockIdOrderByIdYearDesc(stockId)
                .stream()
                .collect(Collectors.toMap(v -> v.getId().getYear(), Function.identity(), (a, b) -> a));

        return priceYearlyRepository.findByIdStockIdOrderByIdYearDesc(stockId).stream()
                .limit(10)
                .map(price -> toYearlyPriceTrendView(price, valuationByYear.get(price.getId().getYear())))
                .toList();
    }

    private HealthCheckResponse.YearlyPriceTrendView toYearlyPriceTrendView(
            StockPriceYearlyEntity price, StockValuationYearlyEntity valuation) {
        return new HealthCheckResponse.YearlyPriceTrendView(
                price.getId().getYear(),
                price.getAvgClosePrice(),
                valuation != null ? valuation.getAvgPer() : null,
                valuation != null ? valuation.getAvgPbr() : null);
    }

    private HealthCheckResponse.ProfileView toProfileView(StockHealthProfileEntity profile) {
        if (profile == null) {
            return new HealthCheckResponse.ProfileView(null, null, null, null);
        }
        return new HealthCheckResponse.ProfileView(
                profile.getProfile(), profile.getCustomNote(), profile.getLastReviewedQuarter(), profile.getUpdatedAt());
    }

    private HealthCheckResponse.AiSuggestionView toAiSuggestionView(String stockId) {
        StockHealthAiSuggestionEntity suggestion = aiSuggestionRepository.findById(stockId).orElse(null);
        return suggestion == null
                ? null
                : new HealthCheckResponse.AiSuggestionView(suggestion.getSuggestionText(), suggestion.getUpdatedAt());
    }

    private StockHealthProfileEntity loadOrCreateProfile(String stockId) {
        return profileRepository.findById(stockId).orElseGet(() -> {
            StockHealthProfileEntity profile = new StockHealthProfileEntity();
            profile.setStockId(stockId);
            profile.setCreatedBy(AuditActors.DEFAULT_USER);
            return profile;
        });
    }

    private StockHealthSnapshotEntity newSnapshotRow(String stockId, String quarter, String metricKey) {
        StockHealthSnapshotEntity row = new StockHealthSnapshotEntity();
        row.setStockId(stockId);
        row.setQuarter(quarter);
        row.setMetricKey(metricKey);
        row.setCreatedBy(AuditActors.DEFAULT_USER);
        return row;
    }

    private Set<String> validMetricKeys(HealthCheckTemplate template) {
        return HealthCheckTemplateDefinitions.categoriesFor(template).stream()
                .flatMap(category -> category.metrics().stream())
                .map(HealthCheckMetricDefinition::key)
                .collect(Collectors.toSet());
    }

    private StockEntity requireStock(String stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Unknown stock_id: " + stockId));
    }
}
