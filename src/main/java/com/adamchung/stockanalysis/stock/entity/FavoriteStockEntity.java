package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 我的最愛（DB-01 8.2）。目前尚無 user_id 欄位——單一使用者的收藏清單（DEC-21、DEC-27）。 */
@Entity
@Table(name = "favorite_stock", uniqueConstraints = @UniqueConstraint(columnNames = "stock_id"))
@Getter
@Setter
public class FavoriteStockEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false, length = 10)
    private String stockId;
}
