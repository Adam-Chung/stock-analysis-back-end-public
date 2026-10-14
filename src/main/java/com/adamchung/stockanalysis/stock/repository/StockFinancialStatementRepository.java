package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.StockFinancialStatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockFinancialStatementRepository extends JpaRepository<StockFinancialStatementEntity, Long> {

    List<StockFinancialStatementEntity> findByStockIdAndTypeOrderByStatementDateDesc(String stockId, String type);

    /** 對應資料表的 UNIQUE(stock_id, statement_date, type) 約束，供 upsert 使用。 */
    Optional<StockFinancialStatementEntity> findByStockIdAndStatementDateAndType(
            String stockId, LocalDate statementDate, String type);
}
