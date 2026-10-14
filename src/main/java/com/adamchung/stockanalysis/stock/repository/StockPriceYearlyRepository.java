package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockPriceYearlyRepository extends JpaRepository<StockPriceYearlyEntity, StockYearId> {

    List<StockPriceYearlyEntity> findByIdStockIdOrderByIdYearDesc(String stockId);
}
