package com.adamchung.stockanalysis.finmind.sync;

/**
 * 本應用程式會同步的 FinMind 資料集名稱。名稱與 FinMind 官方的資料集識別字完全一致
 * （見 docs/spec/SA-02-finmind-data-mapping.md），可以直接原樣用在 API 呼叫上。
 */
public enum FinMindDataset {
    TaiwanStockInfo,
    TaiwanStockPrice,
    TaiwanStockPER,
    TaiwanStockDividend,
    TaiwanStockFinancialStatements
}
