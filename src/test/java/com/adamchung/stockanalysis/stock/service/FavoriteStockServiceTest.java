package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.entity.FavoriteStockEntity;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.repository.FavoriteStockRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteStockServiceTest {

    @Mock
    private FavoriteStockRepository favoriteStockRepository;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private FavoriteStockService service;

    @Captor
    private ArgumentCaptor<FavoriteStockEntity> favoriteCaptor;

    @Test
    void add_saves_a_new_favorite_with_the_default_actor_DEC21() {
        when(favoriteStockRepository.existsByStockId("2330")).thenReturn(false);
        when(stockRepository.existsById("2330")).thenReturn(true);

        service.add("2330");

        verify(favoriteStockRepository).save(favoriteCaptor.capture());
        FavoriteStockEntity saved = favoriteCaptor.getValue();
        assertThat(saved.getStockId()).isEqualTo("2330");
        assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.DEFAULT_USER);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.DEFAULT_USER);
    }

    @Test
    void add_is_idempotent_when_already_a_favorite() {
        when(favoriteStockRepository.existsByStockId("2330")).thenReturn(true);

        service.add("2330");

        verify(favoriteStockRepository, never()).save(any());
    }

    @Test
    void add_throws_for_an_unknown_stock_id() {
        when(favoriteStockRepository.existsByStockId("9999")).thenReturn(false);
        when(stockRepository.existsById("9999")).thenReturn(false);

        assertThatThrownBy(() -> service.add("9999")).isInstanceOf(java.util.NoSuchElementException.class);
        verify(favoriteStockRepository, never()).save(any());
    }

    @Test
    void remove_deletes_by_stock_id() {
        service.remove("2330");
        verify(favoriteStockRepository, times(1)).deleteByStockId("2330");
    }

    @Test
    void list_joins_favorites_with_stock_identity_in_created_order() {
        FavoriteStockEntity fav1 = new FavoriteStockEntity();
        fav1.setStockId("2330");
        FavoriteStockEntity fav2 = new FavoriteStockEntity();
        fav2.setStockId("2881");
        when(favoriteStockRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(fav1, fav2));

        StockEntity tsmc = new StockEntity();
        tsmc.setStockId("2330");
        tsmc.setStockName("台積電");
        tsmc.setIndustryCategory("半導體業");
        when(stockRepository.findById("2330")).thenReturn(java.util.Optional.of(tsmc));
        when(stockRepository.findById("2881")).thenReturn(java.util.Optional.empty());

        List<StockSummary> result = service.list();

        assertThat(result).containsExactly(
                new StockSummary("2330", "台積電", "半導體業"),
                new StockSummary("2881", "2881", null)); // falls back gracefully if stock row is missing
    }
}
