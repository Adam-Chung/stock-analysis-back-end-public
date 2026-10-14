package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.entity.FavoriteStockEntity;
import com.adamchung.stockanalysis.stock.repository.FavoriteStockRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** 我的最愛（DB-01 8.2/8.3）。目前尚無登入機制——單一收藏清單，非個別使用者各自獨立（DEC-21、DEC-27）。 */
@Service
public class FavoriteStockService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final StockRepository stockRepository;

    public FavoriteStockService(FavoriteStockRepository favoriteStockRepository, StockRepository stockRepository) {
        this.favoriteStockRepository = favoriteStockRepository;
        this.stockRepository = stockRepository;
    }

    /**
     * 查詢目前的我的最愛清單，依加入時間排序。
     *
     * @return 已收藏的股票摘要清單；股票本身找不到時退回用股票代號當名稱顯示
     */
    public List<StockSummary> list() {
        return favoriteStockRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(favorite -> stockRepository.findById(favorite.getStockId())
                        .map(s -> new StockSummary(s.getStockId(), s.getStockName(), s.getIndustryCategory()))
                        .orElseGet(() -> new StockSummary(favorite.getStockId(), favorite.getStockId(), null)))
                .toList();
    }

    /**
     * 將股票加入我的最愛，已收藏時不做任何事（idempotent）。
     *
     * @param stockId 股票代號
     * @throws NoSuchElementException 股票代號不存在時拋出
     */
    @Transactional
    public void add(String stockId) {
        if (favoriteStockRepository.existsByStockId(stockId)) {
            return; // idempotent，已經是收藏就不重複處理
        }
        if (!stockRepository.existsById(stockId)) {
            throw new NoSuchElementException("Unknown stock_id: " + stockId);
        }
        FavoriteStockEntity favorite = new FavoriteStockEntity();
        favorite.setStockId(stockId);
        favorite.setCreatedBy(AuditActors.DEFAULT_USER);
        favorite.setUpdatedBy(AuditActors.DEFAULT_USER);
        favoriteStockRepository.save(favorite);
    }

    /**
     * 將股票從我的最愛移除。
     *
     * @param stockId 股票代號
     */
    @Transactional
    public void remove(String stockId) {
        favoriteStockRepository.deleteByStockId(stockId);
    }
}
