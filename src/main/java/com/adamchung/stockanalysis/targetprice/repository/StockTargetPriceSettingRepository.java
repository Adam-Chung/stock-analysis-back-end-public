package com.adamchung.stockanalysis.targetprice.repository;

import com.adamchung.stockanalysis.targetprice.entity.StockTargetPriceSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTargetPriceSettingRepository extends JpaRepository<StockTargetPriceSettingEntity, String> {
}
