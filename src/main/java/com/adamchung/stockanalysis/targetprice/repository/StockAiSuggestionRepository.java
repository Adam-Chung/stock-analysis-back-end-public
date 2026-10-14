package com.adamchung.stockanalysis.targetprice.repository;

import com.adamchung.stockanalysis.targetprice.entity.StockAiSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAiSuggestionRepository extends JpaRepository<StockAiSuggestionEntity, String> {
}
