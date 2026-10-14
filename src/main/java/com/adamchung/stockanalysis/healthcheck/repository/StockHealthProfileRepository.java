package com.adamchung.stockanalysis.healthcheck.repository;

import com.adamchung.stockanalysis.healthcheck.entity.StockHealthProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHealthProfileRepository extends JpaRepository<StockHealthProfileEntity, String> {
}
