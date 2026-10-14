package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.StockEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRepository extends JpaRepository<StockEntity, String> {

    /** DB-01 8.3：在本地已同步的股票目錄中搜尋（DEC-26），不會呼叫 FinMind。 */
    List<StockEntity> findByStockIdContainingOrStockNameContainingOrderByStockId(
            String stockIdFragment, String stockNameFragment, Pageable pageable);
}
