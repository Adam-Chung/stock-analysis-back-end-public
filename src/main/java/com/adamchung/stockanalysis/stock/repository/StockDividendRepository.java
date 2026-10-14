package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.StockDividendEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockDividendRepository extends JpaRepository<StockDividendEntity, Long> {

    List<StockDividendEntity> findByStockIdOrderByAnnouncementDateDesc(String stockId);

    /** 對應資料表的 UNIQUE(stock_id, year, announcement_date) 約束，供 upsert 使用。 */
    Optional<StockDividendEntity> findByStockIdAndYearAndAnnouncementDate(
            String stockId, Integer year, LocalDate announcementDate);
}
