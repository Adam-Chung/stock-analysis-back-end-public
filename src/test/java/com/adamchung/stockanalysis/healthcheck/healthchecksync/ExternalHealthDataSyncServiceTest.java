package com.adamchung.stockanalysis.healthcheck.healthchecksync;

import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRegulatory;
import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.bank.client.BankCapitalAdequacyClient;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankCapitalAdequacyRow;
import com.adamchung.stockanalysis.externaldata.bank.client.BankDisclosurePageClient;
import com.adamchung.stockanalysis.externaldata.bank.client.BankNplCoverageClient;
import com.adamchung.stockanalysis.externaldata.bank.dto.BankNplCoverageRow;
import com.adamchung.stockanalysis.externaldata.bank.dto.NplCoverageFileLink;
import com.adamchung.stockanalysis.externaldata.twse.dto.DirectorHoldingsRow;
import com.adamchung.stockanalysis.externaldata.twse.InstitutionalQuarterlyChangeCalculator;
import com.adamchung.stockanalysis.externaldata.twse.client.TwseOpenDataClient;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.ExternalSyncResult;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalHealthDataSyncServiceTest {

    private static final String STOCK_ID = "2884";
    private static final String BANK_NAME = "玉山商業銀行";

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHealthSnapshotRepository snapshotRepository;
    @Mock
    private TwseOpenDataClient twseOpenDataClient;
    @Mock
    private InstitutionalQuarterlyChangeCalculator institutionalCalculator;
    @Mock
    private BankCapitalAdequacyClient capitalAdequacyClient;
    @Mock
    private BankDisclosurePageClient disclosurePageClient;
    @Mock
    private BankNplCoverageClient nplCoverageClient;

    @InjectMocks
    private ExternalHealthDataSyncService service;

    @Captor
    private ArgumentCaptor<StockHealthSnapshotEntity> snapshotCaptor;

    @Test
    void capitalAdequacyRatio_note_reports_rank_among_all_banks_in_the_same_period() {
        StockEntity stock = bankStock();
        when(stockRepository.findById(STOCK_ID)).thenReturn(Optional.of(stock));
        when(twseOpenDataClient.fetchDirectorHoldings(List.of(STOCK_ID))).thenReturn(
                List.of(new DirectorHoldingsRow(STOCK_ID, "玉山金", 1.0, 2.0, "2026-01", "url")));
        when(institutionalCalculator.fetchInstitutionalQuarterlyChange(5, List.of(STOCK_ID))).thenReturn(List.of());

        // 38 家銀行，玉山排在第三高（比它高的有2家）-> 排名3/38
        List<BankCapitalAdequacyRow> allBanks = new java.util.ArrayList<>();
        allBanks.add(new BankCapitalAdequacyRow("2891", "第一銀行", "First", "115年3月底", 16.0, "url"));
        allBanks.add(new BankCapitalAdequacyRow("2892", "第二銀行", "Second", "115年3月底", 15.5, "url"));
        allBanks.add(new BankCapitalAdequacyRow("2884", BANK_NAME, "ESun", "115年3月底", 15.3, "url"));
        for (int i = 0; i < 35; i++) {
            allBanks.add(new BankCapitalAdequacyRow("99" + i, "其他銀行" + i, "Other" + i, "115年3月底", 10.0, "url"));
        }
        when(capitalAdequacyClient.fetchBankCapitalAdequacy()).thenReturn(allBanks);
        NplCoverageFileLink link = new NplCoverageFileLink("url", 115, 3);
        when(disclosurePageClient.listNplCoverageFileLinks()).thenReturn(List.of(link));
        when(nplCoverageClient.fetchBankNplCoverage(link)).thenReturn(
                List.of(new BankNplCoverageRow(BANK_NAME, "115年3月", 0.1, 800.0, "url")));

        service.sync(STOCK_ID);

        verifyUpsertedNote(BankRegulatory.CAPITAL_ADEQUACY_RATIO, "排名3/38");
    }

    @Test
    void overdueLoanRatio_lower_is_better_note_reports_rank() {
        StockEntity stock = bankStock();
        when(stockRepository.findById(STOCK_ID)).thenReturn(Optional.of(stock));
        when(twseOpenDataClient.fetchDirectorHoldings(List.of(STOCK_ID))).thenReturn(
                List.of(new DirectorHoldingsRow(STOCK_ID, "玉山金", 1.0, 2.0, "2026-01", "url")));
        when(institutionalCalculator.fetchInstitutionalQuarterlyChange(5, List.of(STOCK_ID))).thenReturn(List.of());
        when(capitalAdequacyClient.fetchBankCapitalAdequacy()).thenReturn(
                List.of(new BankCapitalAdequacyRow("2884", BANK_NAME, "ESun", "115年3月底", 15.0, "url")));

        NplCoverageFileLink link = new NplCoverageFileLink("url", 115, 3);
        when(disclosurePageClient.listNplCoverageFileLinks()).thenReturn(List.of(link));
        List<BankNplCoverageRow> nplRows = List.of(
                new BankNplCoverageRow("A銀行", "115年3月", 0.05, 900.0, "url"), // 逾放比更低，排名比玉山前
                new BankNplCoverageRow(BANK_NAME, "115年3月", 0.14, 828.0, "url"),
                new BankNplCoverageRow("B銀行", "115年3月", 0.20, 700.0, "url"));
        when(nplCoverageClient.fetchBankNplCoverage(link)).thenReturn(nplRows);

        service.sync(STOCK_ID);

        // 逾放比：越低越好，A(0.05) < 玉山(0.14) < B(0.20)，玉山前面只有A比它好 -> 排名2/3
        verifyUpsertedNote(BankRegulatory.OVERDUE_LOAN_RATIO, "排名2/3");
        // 覆蓋率：越高越好，A(900) > 玉山(828) > B(700)，玉山前面只有A比它好 -> 排名2/3
        verifyUpsertedNote(BankRegulatory.BAD_DEBT_COVERAGE_RATIO, "排名2/3");
    }

    @Test
    void tied_values_receive_the_same_rank() {
        List<Double> values = List.of(20.0, 15.0, 15.0, 10.0);

        String rankForFirstFifteen = ExternalHealthDataSyncService.rankNote(values, 15.0, true);

        assertThat(rankForFirstFifteen).isEqualTo("排名2/4"); // 只有 20.0 比 15.0 好，兩個15.0並列第2
    }

    @Test
    void capital_adequacy_failure_does_not_block_npl_and_coverage_sync() {
        mockCommonBankData();
        when(capitalAdequacyClient.fetchBankCapitalAdequacy())
                .thenThrow(new ExternalDataException("資本適足率格式異常"));
        NplCoverageFileLink link = new NplCoverageFileLink("url", 115, 3);
        when(disclosurePageClient.listNplCoverageFileLinks()).thenReturn(List.of(link));
        when(nplCoverageClient.fetchBankNplCoverage(link)).thenReturn(
                List.of(new BankNplCoverageRow(BANK_NAME, "115年3月", 0.1, 800.0, "url")));

        ExternalSyncResult result = service.sync(STOCK_ID);

        assertThat(result.syncedMetricKeys())
                .contains(BankRegulatory.OVERDUE_LOAN_RATIO, BankRegulatory.BAD_DEBT_COVERAGE_RATIO)
                .doesNotContain(BankRegulatory.CAPITAL_ADEQUACY_RATIO);
        assertThat(result.warnings()).singleElement().asString().contains("資本適足率同步失敗");
    }

    @Test
    void unusually_high_capital_adequacy_is_saved_and_reported_as_a_warning() {
        mockCommonBankData();
        when(capitalAdequacyClient.fetchBankCapitalAdequacy()).thenReturn(List.of(
                new BankCapitalAdequacyRow("823", "將來商業銀行", "Next Bank", "111年3月底", 698.83, "url"),
                new BankCapitalAdequacyRow("2884", BANK_NAME, "ESun", "111年3月底", 15.0, "url")));
        NplCoverageFileLink link = new NplCoverageFileLink("url", 115, 3);
        when(disclosurePageClient.listNplCoverageFileLinks()).thenReturn(List.of(link));
        when(nplCoverageClient.fetchBankNplCoverage(link)).thenReturn(
                List.of(new BankNplCoverageRow(BANK_NAME, "115年3月", 0.1, 800.0, "url")));

        ExternalSyncResult result = service.sync(STOCK_ID);

        assertThat(result.syncedMetricKeys()).contains(BankRegulatory.CAPITAL_ADEQUACY_RATIO);
        assertThat(result.warnings()).singleElement().asString()
                .contains("將來商業銀行", "698.83%", "已保留計算");
        verifyUpsertedNote(BankRegulatory.CAPITAL_ADEQUACY_RATIO, "排名2/2");
    }

    private void verifyUpsertedNote(String metricKey, String expectedNote) {
        org.mockito.Mockito.verify(snapshotRepository, org.mockito.Mockito.atLeastOnce()).save(snapshotCaptor.capture());
        StockHealthSnapshotEntity match = snapshotCaptor.getAllValues().stream()
                .filter(row -> metricKey.equals(row.getMetricKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot saved for " + metricKey));
        assertThat(match.getNote()).isEqualTo(expectedNote);
    }

    /**
     * 建立銀行模板同步測試共用的股票、董監持股與法人持股資料。
     */
    private void mockCommonBankData() {
        when(stockRepository.findById(STOCK_ID)).thenReturn(Optional.of(bankStock()));
        when(twseOpenDataClient.fetchDirectorHoldings(List.of(STOCK_ID))).thenReturn(
                List.of(new DirectorHoldingsRow(STOCK_ID, "玉山金", 1.0, 2.0, "2026-01", "url")));
        when(institutionalCalculator.fetchInstitutionalQuarterlyChange(5, List.of(STOCK_ID))).thenReturn(List.of());
    }

    private StockEntity bankStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId(STOCK_ID);
        stock.setIndustryCategory("金融業（銀行）");
        return stock;
    }
}
