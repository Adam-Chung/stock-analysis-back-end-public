package com.adamchung.stockanalysis.stock.repository;

import com.adamchung.stockanalysis.stock.entity.FavoriteStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteStockRepository extends JpaRepository<FavoriteStockEntity, Long> {

    List<FavoriteStockEntity> findAllByOrderByCreatedAtAsc();

    Optional<FavoriteStockEntity> findByStockId(String stockId);

    boolean existsByStockId(String stockId);

    void deleteByStockId(String stockId);
}
