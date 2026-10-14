package com.adamchung.stockanalysis.healthcheck.healthchecksync;

import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankFlows;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Capital;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Core;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Debt;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Margin;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Operations;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Payout;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.FinMindHealthCheckSyncResult;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinMindHealthCheckSyncServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHealthSnapshotRepository snapshotRepository;
    @Mock
    private FinMindClient finMindClient;

    @InjectMocks
    private FinMindHealthCheckSyncService service;

    @Captor
    private ArgumentCaptor<StockHealthSnapshotEntity> snapshotCaptor;

    @Test
    void throws_when_stock_does_not_exist() {
        when(stockRepository.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sync("9999")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void general_template_syncs_debt_and_margin_groups_but_not_bank_only_datasets() {
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(
                List.of(financialStatement("2024-12-31", "OperatingIncome", "80"),
                        financialStatement("2024-12-31", "PreTaxIncome", "100")));
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of());
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        FinMindHealthCheckSyncResult result = service.sync("1216");

        assertThat(result.syncedMetricKeys()).contains(Core.OPERATING_INCOME_RATIO_QUARTER);
        verify(finMindClient, never()).fetchMonthRevenue(anyString(), any(), any());
        verify(finMindClient, never()).fetchInstitutionalInvestorsBuySell(anyString(), any(), any());
        verify(finMindClient, never()).fetchShareholding(anyString(), any(), any());
    }

    @Test
    void bank_template_syncs_bank_only_datasets_but_not_general_only_debt_metrics() {
        StockEntity stock = bankStock();
        when(stockRepository.findById("2884")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchBalanceSheet(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchMonthRevenue(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchInstitutionalInvestorsBuySell(eq("2884"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchShareholding(eq("2884"), any(), any())).thenReturn(List.of());

        FinMindHealthCheckSyncResult result = service.sync("2884");

        // bankFlows.* 即使沒有交易資料，近5季每季仍會產生一個 0 值資料點，視為已同步
        assertThat(result.syncedMetricKeys()).contains(
                BankFlows.FOREIGN_NET_BUY_SELL_SHARES, BankFlows.INSTITUTIONAL_NET_BUY_SELL_SHARES);
        assertThat(result.syncedMetricKeys()).doesNotContain(
                Debt.TOTAL_LIABILITIES_RATIO, Core.OPERATING_INCOME_RATIO_QUARTER, Margin.QUARTER);
        verify(finMindClient).fetchMonthRevenue(eq("2884"), any(), any());
    }

    @Test
    void upserts_new_row_with_created_by_and_updates_existing_row_by_natural_key() {
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(
                List.of(financialStatement("2024-12-31", "Revenue", "1000000000"),
                        financialStatement("2024-12-31", "OperatingIncome", "100000000")));
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of());

        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        StockHealthSnapshotEntity existing = new StockHealthSnapshotEntity();
        existing.setStockId("1216");
        existing.setQuarter("2024Q4");
        existing.setMetricKey(Operations.OPERATING_INCOME);
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(
                "1216", "2024Q4", Operations.OPERATING_INCOME))
                .thenReturn(Optional.of(existing));

        service.sync("1216");

        verify(snapshotRepository, times(1)).save(existing);
        assertThat(existing.getValue()).isEqualByComparingTo("1.0000"); // 1億 / 1億
        assertThat(existing.getUpdatedBy()).isEqualTo("finmind-healthcheck-sync");
    }

    @Test
    void writes_the_payout_ratio_breakdown_note_only_when_the_existing_note_is_blank() {
        // DEC-074：盈餘總分配率的公式明細筆記只在資料庫裡這期的筆記還是空白時才會寫入，
        // 已經存過任何筆記的期別（即使只是先前自動帶入又被使用者按下儲存）不會被同步覆蓋。
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(List.of(
                financialStatement("2024-03-31", "EPS", "0.5"),
                financialStatement("2024-06-30", "EPS", "0.5"),
                financialStatement("2024-09-30", "EPS", "0.5"),
                financialStatement("2024-12-31", "EPS", "0.5")));
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of(
                new com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto(
                        LocalDate.of(2025, 6, 1), "1216", "113年", new BigDecimal("1.4"),
                        null, LocalDate.of(2025, 6, 18), null, new BigDecimal("0.4"), null, null, null)));

        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        StockHealthSnapshotEntity blankNoteRow = new StockHealthSnapshotEntity();
        blankNoteRow.setStockId("1216");
        blankNoteRow.setQuarter("2024Q4");
        blankNoteRow.setMetricKey(Payout.TOTAL_PAYOUT_RATIO);
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey("1216", "2024Q4", Payout.TOTAL_PAYOUT_RATIO))
                .thenReturn(Optional.of(blankNoteRow));

        service.sync("1216");

        assertThat(blankNoteRow.getValue()).isEqualByComparingTo("90.0000"); // (1.4+0.4)/2.0*100
        assertThat(blankNoteRow.getNote()).isEqualTo("現金股利1.4+股票股利0.4 / EPS2 = 90%");
    }

    @Test
    void does_not_overwrite_an_already_saved_payout_ratio_note() {
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(List.of(
                financialStatement("2024-03-31", "EPS", "0.5"),
                financialStatement("2024-06-30", "EPS", "0.5"),
                financialStatement("2024-09-30", "EPS", "0.5"),
                financialStatement("2024-12-31", "EPS", "0.5")));
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of(
                new com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto(
                        LocalDate.of(2025, 6, 1), "1216", "113年", new BigDecimal("1.4"),
                        null, LocalDate.of(2025, 6, 18), null, new BigDecimal("0.4"), null, null, null)));

        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        StockHealthSnapshotEntity existingNoteRow = new StockHealthSnapshotEntity();
        existingNoteRow.setStockId("1216");
        existingNoteRow.setQuarter("2024Q4");
        existingNoteRow.setMetricKey(Payout.TOTAL_PAYOUT_RATIO);
        existingNoteRow.setNote("業外收益暫時拉高，下一季追蹤是否回落");
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey("1216", "2024Q4", Payout.TOTAL_PAYOUT_RATIO))
                .thenReturn(Optional.of(existingNoteRow));

        service.sync("1216");

        assertThat(existingNoteRow.getNote()).isEqualTo("業外收益暫時拉高，下一季追蹤是否回落");
    }

    @Test
    void fetches_dividends_with_an_end_date_buffered_past_today() {
        // DEC-073：FinMind TaiwanStockDividend 的 date 欄位常常比除息日晚幾天才確定，
        // end_date 如果直接用「今天」，剛除息完的股利記錄會被 FinMind 自己的日期區間篩選排除，
        // 導致算不出當年度的盈餘總分配率。這裡驗證 end_date 確實比「今天」晚（留了緩衝）。
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of());

        LocalDate beforeCall = LocalDate.now();
        service.sync("1216");

        ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(finMindClient).fetchDividend(eq("1216"), any(), endDateCaptor.capture());
        assertThat(endDateCaptor.getValue()).isAfter(beforeCall.plusMonths(2));
    }

    @Test
    void replaces_stale_capital_expansion_ratio_periods_outside_the_current_target_set() {
        // DEC-083：股本膨脹率(年)的目標期間會隨最新一季往前推進（最新一季＋過去4個完整曆年
        // Q4，見 HealthCheckFinancialMetricsCalculator.capitalExpansionRatio javadoc）。
        // DEC-077 之前的舊版同步會逐季輸出全部季度，這裡驗證改版後的同步會刪掉不在這次計算
        // 結果內的舊期間，不能只 upsert 新值、放著舊期間繼續卡在資料庫裡（使用者2026-08-07
        // 實際回報：股本膨脹率(年)顯示2026Q1／2025／2025Q3／2025Q2／2025Q1，後三個就是這種
        // 舊資料把改版後真正該顯示的年度期間擠出前端historyLimit=5的畫面）。
        StockEntity stock = generalStock();
        when(stockRepository.findById("1216")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchFinancialStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchBalanceSheet(eq("1216"), any(), any())).thenReturn(List.of(
                balanceSheetItem("2025-06-30", "CapitalStock", "1000000000"),
                balanceSheetItem("2020-06-30", "CapitalStock", "900000000")));
        when(finMindClient.fetchCashFlowsStatement(eq("1216"), any(), any())).thenReturn(List.of());
        when(finMindClient.fetchDividend(eq("1216"), any(), any())).thenReturn(List.of());
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        service.sync("1216");

        // 只有 2025Q2（最新一季）算得出比率——2020-06-30 那筆只是它的5年前比較基準，不會
        // 單獨產生資料點；過去4個完整曆年Q4（2024Q4/2023Q4/2022Q4/2021Q4）缺股本資料被跳過。
        verify(snapshotRepository).deleteByStockIdAndMetricKeyAndQuarterNotIn(
                "1216", Capital.EXPANSION_RATIO, List.of("2025Q2"));
    }

    private StockEntity generalStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("1216");
        stock.setIndustryCategory("食品工業");
        return stock;
    }

    private StockEntity bankStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2884");
        stock.setIndustryCategory("金融業（銀行）");
        return stock;
    }

    private FinMindFinancialStatementDto financialStatement(String date, String type, String value) {
        return new FinMindFinancialStatementDto(LocalDate.parse(date), "1216", type, new BigDecimal(value), null);
    }

    private com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto balanceSheetItem(
            String date, String type, String value) {
        return new com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto(
                LocalDate.parse(date), "1216", type, new BigDecimal(value), null);
    }
}
