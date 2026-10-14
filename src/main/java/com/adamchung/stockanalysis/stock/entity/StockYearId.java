package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** 複合主鍵 (stock_id, year)，StockPriceYearlyEntity 與 StockValuationYearlyEntity 共用。 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StockYearId implements Serializable {

    private String stockId;

    private Integer year;
}
