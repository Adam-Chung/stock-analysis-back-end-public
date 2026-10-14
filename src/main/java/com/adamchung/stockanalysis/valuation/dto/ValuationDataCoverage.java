package com.adamchung.stockanalysis.valuation.dto;

/**
 * 每個估值指標實際採用的完整年度數。
 *
 * <p>PER 與 PBR 序列各自獨立計數，因為虧損股某一年可能沒有可用的 PER，
 * 但仍然有可用的 PBR 資料。</p>
 */
public record ValuationDataCoverage(
        int lowPerYears, // 低本益比可用完整年度數
        int avgPerYears, // 平均本益比可用完整年度數
        int highPerYears, // 高本益比可用完整年度數
        int lowPbrYears, // 低股價淨值比可用完整年度數
        int avgPbrYears, // 平均股價淨值比可用完整年度數
        int highPbrYears, // 高股價淨值比可用完整年度數
        int dividendYieldYears, // 殖利率可用完整年度數
        int epsYears, // EPS可用完整年度數
        int bpsYears // BPS（每股淨值）可用完整年度數
) {
}
