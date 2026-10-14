package com.adamchung.stockanalysis.externaldata.bank;

import java.util.Map;
import java.util.Optional;

/**
 * 上市金控／銀行股票代號 → 銀行局資本適足率／資產品質評估分析統計表所用的官方銀行名稱。
 *
 * <p>這份對照表是人工整理，資本適足率／逾放比資料集本身只用「銀行名稱」識別（不是股票代號，
 * 見 {@link BankCapitalAdequacyRow}／{@link BankNplCoverageRow}），沒有任何官方資料集提供
 * 「金控股票代號 ↔ 銀行子公司名稱」的對照，需要另外維護。第一次對特定股票執行同步時，如果
 * 抓到的資料裡找不到這裡填的名稱，{@code ExternalHealthDataSyncService} 會丟出明確錯誤
 * （而非略過或模糊比對），屆時要對照實際回應內容更新這裡的名稱字串。
 *
 * <p>2026-08-02 已對真實資料驗證過（直接執行 stock-financial-data-collector 的
 * {@code fetch:bank-indicators}，比對 `data/bank-indicators/capital-adequacy-ratio.csv`／
 * `npl-coverage-ratio.csv` 實際輸出的 38 家銀行名稱），修正 2 處「臺」「台」異體字誤植：
 * 台灣中小企業銀行→臺灣中小企業銀行（2834）、台灣新光商業銀行→臺灣新光商業銀行（2888）。
 * 移除 5820 日盛國際商業銀行——實際資料裡查無此名稱，推測是 2022 年日盛金控被星展銀行
 * （DBS）收購下市後已不存在，這個股票代號目前不該出現在對照表裡；如果之後有需要才重新確認。
 */
public final class BankIdentityLookup {

    private static final Map<String, String> BANK_NAME_BY_STOCK_ID = Map.ofEntries(
            Map.entry("2801", "彰化商業銀行"),
            Map.entry("2809", "京城商業銀行"),
            Map.entry("2812", "台中商業銀行"),
            Map.entry("2834", "臺灣中小企業銀行"),
            Map.entry("2836", "高雄銀行"),
            Map.entry("2838", "聯邦商業銀行"),
            Map.entry("2845", "遠東國際商業銀行"),
            Map.entry("2849", "安泰商業銀行"),
            Map.entry("2880", "華南商業銀行"),
            Map.entry("2881", "台北富邦商業銀行"),
            Map.entry("2882", "國泰世華商業銀行"),
            Map.entry("2883", "凱基商業銀行"),
            Map.entry("2884", "玉山商業銀行"),
            Map.entry("2885", "元大商業銀行"),
            Map.entry("2886", "兆豐國際商業銀行"),
            Map.entry("2887", "台新國際商業銀行"),
            Map.entry("2888", "臺灣新光商業銀行"),
            Map.entry("2890", "永豐商業銀行"),
            Map.entry("2891", "中國信託商業銀行"),
            Map.entry("2892", "第一商業銀行"),
            Map.entry("2897", "王道商業銀行"),
            Map.entry("5876", "上海商業儲蓄銀行"),
            Map.entry("5880", "合作金庫商業銀行")
    );

    private BankIdentityLookup() {
    }

    public static Optional<String> bankNameFor(String stockId) {
        return Optional.ofNullable(BANK_NAME_BY_STOCK_ID.get(stockId));
    }
}
