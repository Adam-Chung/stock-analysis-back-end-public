package com.adamchung.stockanalysis.stock;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockDirectoryDeduplicatorTest {

    @Test
    void keeps_first_occurrence_when_the_same_stock_id_appears_twice_DEC007() {
        // Verified against the live API: 2330 comes back twice with different industry_category.
        FinMindStockInfoDto first = new FinMindStockInfoDto("半導體業", "2330", "台積電", "twse");
        FinMindStockInfoDto second = new FinMindStockInfoDto("電子工業", "2330", "台積電", "twse");
        FinMindStockInfoDto other = new FinMindStockInfoDto("金融業", "2881", "富邦金", "twse");

        List<FinMindStockInfoDto> result = StockDirectoryDeduplicator.keepFirstPerStockId(List.of(first, second, other));

        assertThat(result).containsExactly(first, other);
    }

    @Test
    void returns_empty_list_for_empty_input() {
        assertThat(StockDirectoryDeduplicator.keepFirstPerStockId(List.of())).isEmpty();
    }
}
