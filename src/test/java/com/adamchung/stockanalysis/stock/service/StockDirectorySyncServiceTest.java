package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import com.adamchung.stockanalysis.stock.StockDirectoryBatchWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDirectorySyncServiceTest {

    @Mock
    private FinMindClient finMindClient;

    @Mock
    private StockDirectoryBatchWriter batchWriter;

    @InjectMocks
    private StockDirectorySyncService service;

    @Test
    void syncDirectory_dedupes_before_batch_upsert() {
        FinMindStockInfoDto dup1 = new FinMindStockInfoDto("半導體業", "2330", "台積電", "twse");
        FinMindStockInfoDto dup2 = new FinMindStockInfoDto("電子工業", "2330", "台積電", "twse");
        FinMindStockInfoDto newStock = new FinMindStockInfoDto("金融業", "2881", "富邦金", "twse");
        FinMindStockInfoDto unsupported =
                new FinMindStockInfoDto("其他", "ID-LONGER-THAN-10", "非股票項目", "other");
        when(finMindClient.fetchAllStockInfo()).thenReturn(List.of(dup1, dup2, newStock, unsupported));

        int count = service.syncDirectory();

        assertThat(count).isEqualTo(2); // deduped: 2330 once, 2881 once

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinMindStockInfoDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchWriter).upsertAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(dup1, newStock);
    }
}
