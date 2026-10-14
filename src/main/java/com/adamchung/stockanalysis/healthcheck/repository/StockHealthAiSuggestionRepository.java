package com.adamchung.stockanalysis.healthcheck.repository;

import com.adamchung.stockanalysis.healthcheck.entity.StockHealthAiSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 一支股票只保留最新一筆 AI 建議，PK 就是 stockId，不需要自訂查詢方法。 */
public interface StockHealthAiSuggestionRepository extends JpaRepository<StockHealthAiSuggestionEntity, String> {
}
