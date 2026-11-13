package com.adamchung.stockanalysis.quota;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 共用外部資料的跨使用者冷卻時間，避免不同帳號重複消耗免費 API。 */
@Service
public class SharedSyncFreshnessService {

    public static final String HEALTH_EXTERNAL_SYNC = "HEALTH_EXTERNAL_SYNC";
    private static final Duration HEALTH_SYNC_TTL = Duration.ofHours(6);

    private final JdbcClient jdbcClient;

    /** 建立共用同步新鮮度服務。 */
    public SharedSyncFreshnessService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 判斷同一股票的企業體質公開資料是否仍在六小時共用快取期內。
     *
     * @param stockId 股票代號
     * @return 快取是否仍新鮮
     */
    public boolean isHealthSyncFresh(String stockId) {
        return jdbcClient.sql("""
                        SELECT last_success_at FROM shared_sync_state
                        WHERE stock_id=:stockId AND action=:action
                        """).param("stockId", stockId).param("action", HEALTH_EXTERNAL_SYNC)
                .query(Instant.class).optional()
                .map(lastSuccess -> lastSuccess.plus(HEALTH_SYNC_TTL).isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * 任一外部來源成功後更新共用快取時間。
     *
     * @param stockId 股票代號
     */
    @Transactional(rollbackFor = Throwable.class)
    public void markHealthSyncSuccess(String stockId) {
        jdbcClient.sql("""
                        INSERT INTO shared_sync_state (stock_id, action, last_success_at)
                        VALUES (:stockId, :action, now())
                        ON CONFLICT (stock_id, action) DO UPDATE SET last_success_at=EXCLUDED.last_success_at
                        """).param("stockId", stockId).param("action", HEALTH_EXTERNAL_SYNC).update();
    }
}
