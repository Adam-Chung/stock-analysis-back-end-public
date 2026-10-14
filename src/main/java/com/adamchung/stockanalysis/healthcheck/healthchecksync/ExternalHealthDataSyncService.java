package com.adamchung.stockanalysis.healthcheck.healthchecksync;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRegulatory;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Insider;
import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.RocPeriod;
import com.adamchung.stockanalysis.externaldata.bank.BankIdentityLookup;
import com.adamchung.stockanalysis.externaldata.bank.client.BankCapitalAdequacyClient;
import com.adamchung.stockanalysis.externaldata.bank.client.BankDisclosurePageClient;
import com.adamchung.stockanalysis.externaldata.bank.client.BankNplCoverageClient;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankCapitalAdequacyRow;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankNplCoverageRow;
import com.adamchung.stockanalysis.externaldata.bank.dto.NplCoverageFileLink;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.ExternalSyncResult;
import com.adamchung.stockanalysis.externaldata.twse.InstitutionalQuarterlyChangeCalculator;
import com.adamchung.stockanalysis.externaldata.twse.InstitutionalQuarterlyChangeRow;
import com.adamchung.stockanalysis.externaldata.twse.client.TwseOpenDataClient;
import com.adamchung.stockanalysis.externaldata.twse.dto.DirectorHoldingsRow;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import com.adamchung.stockanalysis.healthcheck.calculator.HealthCheckTemplateResolver;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 stock-financial-data-collector 移植過來的五個「原本 FinMind 抓不到」的細項
 * （DEC-034／DEC-046~049）自動寫入 {@code stock_health_snapshot}。這是使用者主動觸發的
 * 明確動作（見 {@code ExternalHealthDataSyncController}），不是 GET 畫面時自動觸發——這些
 * 政府公開資料來源本身有節流限制（{@link ExternalDataHttpClient}），一次完整同步可能耗時
 * 數十秒，維持 DEC-030「更新是明確操作」的精神。
 */
@Service
public class ExternalHealthDataSyncService {

    private static final int INSTITUTIONAL_QUARTER_COUNT = 5;
    private static final int NPL_COVERAGE_QUARTER_COUNT = 5;
    private static final double HIGH_CAPITAL_ADEQUACY_WARNING_PERCENT = 100;
    private static final Set<Integer> QUARTER_END_MONTHS = Set.of(3, 6, 9, 12);

    private final StockRepository stockRepository;
    private final StockHealthSnapshotRepository snapshotRepository;
    private final TwseOpenDataClient twseOpenDataClient;
    private final InstitutionalQuarterlyChangeCalculator institutionalCalculator;
    private final BankCapitalAdequacyClient capitalAdequacyClient;
    private final BankDisclosurePageClient disclosurePageClient;
    private final BankNplCoverageClient nplCoverageClient;

    public ExternalHealthDataSyncService(
            StockRepository stockRepository,
            StockHealthSnapshotRepository snapshotRepository,
            TwseOpenDataClient twseOpenDataClient,
            InstitutionalQuarterlyChangeCalculator institutionalCalculator,
            BankCapitalAdequacyClient capitalAdequacyClient,
            BankDisclosurePageClient disclosurePageClient,
            BankNplCoverageClient nplCoverageClient) {
        this.stockRepository = stockRepository;
        this.snapshotRepository = snapshotRepository;
        this.twseOpenDataClient = twseOpenDataClient;
        this.institutionalCalculator = institutionalCalculator;
        this.capitalAdequacyClient = capitalAdequacyClient;
        this.disclosurePageClient = disclosurePageClient;
        this.nplCoverageClient = nplCoverageClient;
    }

    /**
     * 對指定股票執行一次完整外部資料同步：董監持股比／全體董監質押（最新一期）、法人持股比
     * 季變化（近5季）、以及銀行模板專屬的資本適足率／逾放比／覆蓋率（近5季，僅 BANK 模板）。
     *
     * <p>刻意**不**在方法層級加 {@code @Transactional}（DEC-056）——理由跟 DEC-020（target-price 同步
     * 曾經因為整個方法包在同一個 transaction，導致晚一步驟失敗就把早一步驟已成功寫入的資料
     * 一併回滾）完全一樣：董監持股/質押、法人持股比季變化在前，銀行監理三指標（會呼叫政府
     * Excel 檔案，相對最容易失敗）在後，如果整個方法是同一個 transaction，銀行監理指標
     * 讀取失敗時會連帶把已經成功的董監/法人資料也回滾掉，讓使用者以為那些欄位「同步失敗」，
     * 實際上只是被無關的後續步驟拖累。移除方法層級 transaction 後，每個
     * {@link #upsert} 呼叫底下的 {@code snapshotRepository.save()} 各自獨立提交
     * （Spring Data JPA repository 方法預設就是各自一個 transaction），任一步驟失敗只影響
     * 那一步驟本身。
     *
     * @param stockId 台股代號
     * @return 這次同步實際寫入的 metric_key 清單
     * @throws NoSuchElementException 股票代號不存在時拋出
     * @throws ExternalDataException  外部資料來源呼叫/解析失敗，或銀行名稱對照表找不到對應資料時拋出
     */
    public ExternalSyncResult sync(String stockId) {
        StockEntity stock = requireStock(stockId);
        HealthCheckTemplate template = HealthCheckTemplateResolver.resolve(stock.getIndustryCategory());

        List<String> syncedMetricKeys = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // 董監持股比／全體董監質押
        syncDirectorHoldings(stockId, syncedMetricKeys);
        // 法人持股比季變化
        syncInstitutionalQuarterlyChange(stockId, syncedMetricKeys);
        // 銀行模板專屬的資本適足率／逾放比／覆蓋率
        if (template == HealthCheckTemplate.BANK) {
            syncBankRegulatoryIndicators(stockId, syncedMetricKeys, warnings);
        }
        return new ExternalSyncResult(stockId, syncedMetricKeys, warnings);
    }

    private void syncDirectorHoldings(String stockId, List<String> syncedMetricKeys) {
        DirectorHoldingsRow row = twseOpenDataClient.fetchDirectorHoldings(List.of(stockId)).get(0);
        String quarter = RocPeriod.isoMonthToQuarterLabel(row.dataDate());
        upsert(stockId, quarter, Insider.DIRECTOR_SHAREHOLDING_RATIO, row.directorShareholdingPercent(), null);
        upsert(stockId, quarter, Insider.DIRECTOR_PLEDGE_RATIO, row.directorPledgeRatioPercent(), null);
        syncedMetricKeys.add(Insider.DIRECTOR_SHAREHOLDING_RATIO);
        syncedMetricKeys.add(Insider.DIRECTOR_PLEDGE_RATIO);
    }

    private void syncInstitutionalQuarterlyChange(String stockId, List<String> syncedMetricKeys) {
        List<InstitutionalQuarterlyChangeRow> rows = institutionalCalculator
                .fetchInstitutionalQuarterlyChange(INSTITUTIONAL_QUARTER_COUNT, List.of(stockId));
        for (InstitutionalQuarterlyChangeRow row : rows) {
            String note = "外資陸資變動(精確)=%.2f%%；投信估算=%.2f%%；自營商估算=%.2f%%".formatted(
                    row.foreignAndMainlandHoldingChangePercent(),
                    row.investmentTrustNetBuyRatioChangePercent(),
                    row.dealerNetBuyRatioChangePercent());
            upsert(stockId, row.period(), Insider.INSTITUTIONAL_HOLDING_QUARTERLY_CHANGE, row.totalChangePercent(), note);
        }
        syncedMetricKeys.add(Insider.INSTITUTIONAL_HOLDING_QUARTERLY_CHANGE);
    }

    /**
     * 資本適足率／逾期放款比率／備抵呆帳覆蓋率這三個細項，除了寫入該銀行自己的數值，還會
     * 在同一批抓下來的全體銀行資料裡算出該銀行在所有銀行中的排名，寫進筆記欄位（例如
     * 「排名30/38」），比照 {@link #syncInstitutionalQuarterlyChange} 已有的「筆記存系統算出
     * 的說明文字」做法——每次同步都會覆蓋筆記，不是使用者可自由編輯的欄位。資本適足率與
     * 逾放比／覆蓋率分開執行，單一來源失敗只加入 warning，不阻止另一來源同步。
     */
    private void syncBankRegulatoryIndicators(
            String stockId, List<String> syncedMetricKeys, List<String> warnings) {
        String bankName = BankIdentityLookup.bankNameFor(stockId).orElseThrow(() -> new ExternalDataException(
                "尚未設定股票 " + stockId + " 對應的官方銀行名稱，無法比對資本適足率/逾放比資料，"
                        + "需要先在 BankIdentityLookup 補上對照"));

        try {
            syncCapitalAdequacy(stockId, bankName, syncedMetricKeys, warnings);
        } catch (ExternalDataException exception) {
            warnings.add("銀行局資本適足率同步失敗：" + exception.getMessage());
        }

        try {
            syncNplCoverage(stockId, bankName, syncedMetricKeys);
        } catch (ExternalDataException exception) {
            warnings.add("銀行局逾放比/覆蓋率同步失敗：" + exception.getMessage());
        }
    }

    /**
     * 同步資本適足率並計算同期間銀行排名；高於一般範圍的有限數值仍保留，但加入警告供使用者複核。
     */
    private void syncCapitalAdequacy(
            String stockId, String bankName, List<String> syncedMetricKeys, List<String> warnings) {
        List<BankCapitalAdequacyRow> allCapitalRows = capitalAdequacyClient.fetchBankCapitalAdequacy();
        List<BankCapitalAdequacyRow> unusuallyHighRows = allCapitalRows.stream()
                .filter(row -> row.capitalAdequacyRatioPercent() > HIGH_CAPITAL_ADEQUACY_WARNING_PERCENT)
                .toList();
        if (!unusuallyHighRows.isEmpty()) {
            BankCapitalAdequacyRow first = unusuallyHighRows.get(0);
            warnings.add("銀行局資本適足率有 " + unusuallyHighRows.size() + " 筆高於 100%，已保留計算，請複核；例如銀行「"
                    + first.bankNameZh() + "」" + first.period() + "為 " + first.capitalAdequacyRatioPercent() + "%");
        }
        Map<String, List<BankCapitalAdequacyRow>> capitalRowsByPeriod =
                allCapitalRows.stream().collect(Collectors.groupingBy(BankCapitalAdequacyRow::period));
        List<BankCapitalAdequacyRow> capitalRows = allCapitalRows.stream()
                .filter(row -> bankName.equals(row.bankNameZh()))
                .toList();
        if (capitalRows.isEmpty()) {
            throw new ExternalDataException(
                    "資本適足率資料中找不到銀行「" + bankName + "」，BankIdentityLookup 的名稱可能與官方資料不符");
        }
        for (BankCapitalAdequacyRow row : capitalRows) {
            List<Double> allValuesThisPeriod = capitalRowsByPeriod.get(row.period()).stream()
                    .map(BankCapitalAdequacyRow::capitalAdequacyRatioPercent)
                    .toList();
            String rankNote = rankNote(allValuesThisPeriod, row.capitalAdequacyRatioPercent(), true);
            upsert(stockId, RocPeriod.toQuarterLabel(row.period()), BankRegulatory.CAPITAL_ADEQUACY_RATIO,
                    row.capitalAdequacyRatioPercent(), rankNote);
        }
        syncedMetricKeys.add(BankRegulatory.CAPITAL_ADEQUACY_RATIO);
    }

    /**
     * 同步逾放比與備抵呆帳覆蓋率，並分別依同期間全體銀行數值計算排名。
     */
    private void syncNplCoverage(String stockId, String bankName, List<String> syncedMetricKeys) {
        List<NplCoverageFileLink> links = disclosurePageClient.listNplCoverageFileLinks().stream()
                .filter(link -> QUARTER_END_MONTHS.contains(link.rocMonth()))
                .sorted(Comparator.comparingInt(NplCoverageFileLink::rocYear)
                        .thenComparingInt(NplCoverageFileLink::rocMonth)
                        .reversed())
                .limit(NPL_COVERAGE_QUARTER_COUNT)
                .toList();
        boolean foundAnyNplRow = false;
        for (NplCoverageFileLink link : links) {
            List<BankNplCoverageRow> allRowsThisPeriod = nplCoverageClient.fetchBankNplCoverage(link);
            List<Double> overdueValues = allRowsThisPeriod.stream()
                    .map(BankNplCoverageRow::overdueLoanRatioPercent).toList();
            List<Double> coverageValues = allRowsThisPeriod.stream()
                    .map(BankNplCoverageRow::provisionCoverageRatioPercent).toList();
            List<BankNplCoverageRow> nplRows = allRowsThisPeriod.stream()
                    .filter(row -> bankName.equals(row.bankName()))
                    .toList();
            for (BankNplCoverageRow row : nplRows) {
                foundAnyNplRow = true;
                String quarter = RocPeriod.toQuarterLabel(row.period());
                upsert(stockId, quarter, BankRegulatory.OVERDUE_LOAN_RATIO, row.overdueLoanRatioPercent(),
                        rankNote(overdueValues, row.overdueLoanRatioPercent(), false));
                upsert(stockId, quarter, BankRegulatory.BAD_DEBT_COVERAGE_RATIO, row.provisionCoverageRatioPercent(),
                        rankNote(coverageValues, row.provisionCoverageRatioPercent(), true));
            }
        }
        if (!foundAnyNplRow) {
            throw new ExternalDataException(
                    "逾放比/覆蓋率資料中找不到銀行「" + bankName + "」，BankIdentityLookup 的名稱可能與官方資料不符");
        }

        syncedMetricKeys.add(BankRegulatory.OVERDUE_LOAN_RATIO);
        syncedMetricKeys.add(BankRegulatory.BAD_DEBT_COVERAGE_RATIO);
    }

    /**
     * 算出 {@code targetValue} 在 {@code allValues}（同一期全體銀行的數值）中的名次，格式
     * 「排名X/Y」。用「有幾家銀行比自己好」＋1 當名次（並列名次相同，而不是單純依排序位置
     * 決定名次），{@code higherIsBetter=true} 時數值越高名次越前面（資本適足率／備抵呆帳
     * 覆蓋率），{@code false} 時數值越低名次越前面（逾期放款比率）。
     */
    static String rankNote(List<Double> allValues, double targetValue, boolean higherIsBetter) {
        long betterCount = allValues.stream()
                .filter(value -> higherIsBetter ? value > targetValue : value < targetValue)
                .count();
        return "排名%d/%d".formatted(betterCount + 1, allValues.size());
    }

    private void upsert(String stockId, String quarter, String metricKey, double value, String note) {
        StockHealthSnapshotEntity row = snapshotRepository.findByStockIdAndQuarterAndMetricKey(stockId, quarter, metricKey)
                .orElseGet(() -> {
                    StockHealthSnapshotEntity fresh = new StockHealthSnapshotEntity();
                    fresh.setStockId(stockId);
                    fresh.setQuarter(quarter);
                    fresh.setMetricKey(metricKey);
                    fresh.setCreatedBy(AuditActors.EXTERNAL_DATA_SYNC);
                    return fresh;
                });
        row.setValue(BigDecimal.valueOf(value));
        if (note != null) {
            row.setNote(note);
        }
        row.setUpdatedBy(AuditActors.EXTERNAL_DATA_SYNC);
        snapshotRepository.save(row);
    }

    private StockEntity requireStock(String stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Unknown stock_id: " + stockId));
    }
}
