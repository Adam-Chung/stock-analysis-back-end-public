package com.adamchung.stockanalysis.finmind.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinmindSyncLogRepository extends JpaRepository<FinmindSyncLogEntity, Long> {

    Optional<FinmindSyncLogEntity> findByStockIdAndDataset(String stockId, FinMindDataset dataset);

    List<FinmindSyncLogEntity> findByStockId(String stockId);
}
