package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockValuationYearlyRepository extends JpaRepository<StockValuationYearlyEntity, StockYearId> {

    List<StockValuationYearlyEntity> findByIdStockIdOrderByIdYearDesc(String stockId);
}
