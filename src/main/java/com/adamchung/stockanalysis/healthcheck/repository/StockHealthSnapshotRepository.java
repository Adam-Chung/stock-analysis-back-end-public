package com.adamchung.stockanalysis.healthcheck.repository;

import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StockHealthSnapshotRepository extends JpaRepository<StockHealthSnapshotEntity, Long> {

    /** 對應資料表的 UNIQUE(stock_id, quarter, metric_key) 約束，供 upsert 使用。 */
    Optional<StockHealthSnapshotEntity> findByStockIdAndQuarterAndMetricKey(
            String stockId, String quarter, String metricKey);

    /** 給定股票＋細項，依季度（或月份標籤）倒序取值，供「近五季／近幾期」趨勢顯示使用。 */
    List<StockHealthSnapshotEntity> findByStockIdAndMetricKeyOrderByQuarterDesc(String stockId, String metricKey);

    /** 給定股票＋季度，取當季全部細項，用於畫面初始渲染與「上次檢視季度」判斷。 */
    List<StockHealthSnapshotEntity> findByStockIdAndQuarter(String stockId, String quarter);

    /**
     * 刪掉這個股票＋細項裡，期間不在這次同步計算結果內的舊資料列。給
     * {@code FinMindHealthCheckSyncService} 的「目標期間集合」類細項（例如股本膨脹率(年)：
     * 最新一季＋過去4個完整曆年Q4）使用——這類細項的目標期間會隨最新一季往前推進，舊一輪
     * 同步留下、現在已經不在目標集合內的期間，如果不清掉會一直卡在資料庫，跟前端
     * {@code historyLimit}（近5期）的「取最新N筆」讀取邏輯疊在一起時，會把真正該顯示的
     * 期間擠出畫面。衍生 delete 查詢預設不在交易中執行，這裡明確標記
     * {@code @Transactional}，讓呼叫方（{@code FinMindHealthCheckSyncService.sync()} 刻意
     * 沒有方法層級 transaction，見 DEC-020/DEC-056）不需要額外處理。
     */
    @Transactional
    void deleteByStockIdAndMetricKeyAndQuarterNotIn(String stockId, String metricKey, List<String> quarters);
}
