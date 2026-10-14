package com.adamchung.stockanalysis.stock.controller;

import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.stock.dto.FavoriteStockUpdateResponse;
import com.adamchung.stockanalysis.stock.service.FavoriteStockService;
import com.adamchung.stockanalysis.stock.service.FavoriteStockUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/** 我的最愛（DB-01 8.2/8.3）的 REST 端點。 */
@RestController
@RequestMapping("/api/favorites")
@Tag(name = "我的最愛", description = "收藏股票清單的查詢、加入與移除")
public class FavoriteStockController {

    private final FavoriteStockService favoriteStockService;
    private final FavoriteStockUpdateService favoriteStockUpdateService;

    public FavoriteStockController(
            FavoriteStockService favoriteStockService,
            FavoriteStockUpdateService favoriteStockUpdateService) {
        this.favoriteStockService = favoriteStockService;
        this.favoriteStockUpdateService = favoriteStockUpdateService;
    }

    /**
     * 查詢目前的我的最愛清單，依加入時間排序。
     *
     * @return 已收藏的股票摘要清單
     */
    @Operation(summary = "查詢我的最愛清單")
    @GetMapping
    public List<StockSummary> list() {
        return favoriteStockService.list();
    }

    /**
     * 逐檔更新所有收藏股票並回傳目標價摘要。
     *
     * @return 依收藏順序排列的更新結果
     */
    @Operation(summary = "更新所有收藏股票")
    @PostMapping("/refresh")
    public List<FavoriteStockUpdateResponse> refreshAll() {
        return favoriteStockUpdateService.refreshAll();
    }

    /**
     * 將股票加入我的最愛，已收藏時不做任何事。
     *
     * @param stockId 股票代號
     * @return 204 No Content
     */
    @Operation(summary = "加入我的最愛")
    @PostMapping("/{stockId}")
    public ResponseEntity<Void> add(@PathVariable String stockId) {
        favoriteStockService.add(stockId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 將股票從我的最愛移除。
     *
     * @param stockId 股票代號
     * @return 204 No Content
     */
    @Operation(summary = "從我的最愛移除")
    @DeleteMapping("/{stockId}")
    public ResponseEntity<Void> remove(@PathVariable String stockId) {
        favoriteStockService.remove(stockId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 將股票代號不存在的情況映射為 HTTP 404。
     *
     * @return 404 Not Found
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleUnknownStock() {
        return ResponseEntity.notFound().build();
    }
}
