package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.auth.TestSecurityContext;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteStockServiceTest {

    @Mock private UserDataStore userDataStore;
    @Mock private StockRepository stockRepository;
    private FavoriteStockService service;

    @BeforeEach
    void setUp() {
        TestSecurityContext.login(42L);
        service = new FavoriteStockService(userDataStore, stockRepository);
    }

    @Test
    void add_writes_only_to_the_current_users_favorites() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        service.add("2330");
        verify(userDataStore).addFavorite(42L, "2330");
    }

    @Test
    void add_rejects_an_unknown_stock() {
        when(stockRepository.existsById("9999")).thenReturn(false);
        assertThatThrownBy(() -> service.add("9999")).isInstanceOf(java.util.NoSuchElementException.class);
        verify(userDataStore, never()).addFavorite(42L, "9999");
    }

    @Test
    void remove_deletes_only_the_current_users_row() {
        service.remove("2330");
        verify(userDataStore).removeFavorite(42L, "2330");
    }

    @Test
    void list_joins_current_users_favorites_with_shared_stock_data() {
        when(userDataStore.favoriteStockIds(42L)).thenReturn(List.of("2330", "2881"));
        StockEntity tsmc = new StockEntity();
        tsmc.setStockId("2330");
        tsmc.setStockName("台積電");
        tsmc.setIndustryCategory("半導體業");
        when(stockRepository.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockRepository.findById("2881")).thenReturn(Optional.empty());

        assertThat(service.list()).containsExactly(
                new StockSummary("2330", "台積電", "半導體業"),
                new StockSummary("2881", "2881", null));
    }
}
