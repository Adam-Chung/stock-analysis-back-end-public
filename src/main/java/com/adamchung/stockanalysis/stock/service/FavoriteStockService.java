package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.auth.CurrentUser;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** 我的最愛；收藏清單以目前 Google 使用者隔離。 */
@Service
public class FavoriteStockService {

    private final UserDataStore userDataStore;
    private final StockRepository stockRepository;

    public FavoriteStockService(UserDataStore userDataStore, StockRepository stockRepository) {
        this.userDataStore = userDataStore;
        this.stockRepository = stockRepository;
    }

    /**
     * 查詢目前的我的最愛清單，依加入時間排序。
     *
     * @return 已收藏的股票摘要清單；股票本身找不到時退回用股票代號當名稱顯示
     */
    public List<StockSummary> list() {
        return userDataStore.favoriteStockIds(CurrentUser.id()).stream()
                .map(stockId -> stockRepository.findById(stockId)
                        .map(s -> new StockSummary(s.getStockId(), s.getStockName(), s.getIndustryCategory()))
                        .orElseGet(() -> new StockSummary(stockId, stockId, null)))
                .toList();
    }

    /**
     * 將股票加入我的最愛，已收藏時不做任何事（idempotent）。
     *
     * @param stockId 股票代號
     * @throws NoSuchElementException 股票代號不存在時拋出
     */
    @Transactional(rollbackFor = Throwable.class)
    public void add(String stockId) {
        if (!stockRepository.existsById(stockId)) {
            throw new NoSuchElementException("Unknown stock_id: " + stockId);
        }
        userDataStore.addFavorite(CurrentUser.id(), stockId);
    }

    /**
     * 將股票從我的最愛移除。
     *
     * @param stockId 股票代號
     */
    @Transactional(rollbackFor = Throwable.class)
    public void remove(String stockId) {
        userDataStore.removeFavorite(CurrentUser.id(), stockId);
    }
}
