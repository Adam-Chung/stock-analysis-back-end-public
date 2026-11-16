package com.adamchung.stockanalysis.quota;

import com.adamchung.stockanalysis.auth.AppUserPrincipal;
import com.adamchung.stockanalysis.auth.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** 以資料庫原子計數限制每人與全站每日昂貴外部呼叫。 */
@Service
public class DailyUsageService {

    public static final String HEALTH_EXTERNAL_SYNC = "HEALTH_EXTERNAL_SYNC";
    public static final String AI_SUGGESTION = "AI_SUGGESTION";

    private final JdbcClient jdbcClient;
    private final ZoneId zoneId;
    private final int healthSyncLimit;
    private final int aiUserLimit;
    private final int aiGlobalLimit;

    /**
     * 建立每日額度服務。
     *
     * @param jdbcClient 資料庫操作邊界
     * @param zoneId 每日切換時區，固定預設台北
     * @param healthSyncLimit 每人企業體質同步上限
     * @param aiUserLimit 每人 AI 建議上限
     * @param aiGlobalLimit 全站 AI 建議上限
     */
    public DailyUsageService(
            JdbcClient jdbcClient,
            @Value("${app.quota.zone-id:Asia/Taipei}") String zoneId,
            @Value("${app.quota.health-external-sync-per-user-per-day:3}") int healthSyncLimit,
            @Value("${app.quota.ai-suggestion-per-user-per-day:3}") int aiUserLimit,
            @Value("${app.quota.ai-suggestion-global-per-day:30}") int aiGlobalLimit) {
        this.jdbcClient = jdbcClient;
        this.zoneId = ZoneId.of(zoneId);
        this.healthSyncLimit = healthSyncLimit;
        this.aiUserLimit = aiUserLimit;
        this.aiGlobalLimit = aiGlobalLimit;
    }

    /** 為目前使用者保留一次企業體質外部同步額度。 */
    public void reserveHealthExternalSync() {
        AppUserPrincipal principal = CurrentUser.require();
        if (principal.admin()) {
            return;
        }
        long userId = principal.userId();
        reserve("USER:" + userId, userId, HEALTH_EXTERNAL_SYNC, healthSyncLimit,
                "企業體質外部同步每日最多使用 3 次");
    }

    /**
     * 獨立提交一次 AI 呼叫額度；管理者略過個人上限，但仍共用全站免費額度保護。
     * 額度代表實際送出的外部呼叫嘗試，因此後續 Groq 失敗時也不退回，避免失敗重試繞過上限。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void reserveAiSuggestion() {
        AppUserPrincipal principal = CurrentUser.require();
        if (!principal.admin()) {
            long userId = principal.userId();
            reserve("USER:" + userId, userId, AI_SUGGESTION, aiUserLimit,
                    "AI 建議每人每日最多使用 3 次");
        }
        reserve("GLOBAL", null, AI_SUGGESTION, aiGlobalLimit,
                "今日全站 AI 免費額度已用完，請明天再試");
    }

    /**
     * 使用 PostgreSQL upsert 的條件更新原子增加額度，避免同時請求突破上限。
     *
     * @param scopeKey USER:id 或 GLOBAL
     * @param userId 個人額度的 user id，全站額度為 null
     * @param action 額度類型
     * @param limit 每日上限
     * @param message 超限訊息
     */
    private void reserve(String scopeKey, Long userId, String action, int limit, String message) {
        Integer count = jdbcClient.sql("""
                        INSERT INTO user_daily_usage (user_id, scope_key, usage_date, action, usage_count)
                        VALUES (:userId, :scopeKey, :usageDate, :action, 1)
                        ON CONFLICT (scope_key, usage_date, action) DO UPDATE SET
                            usage_count=user_daily_usage.usage_count + 1,
                            updated_at=now()
                        WHERE user_daily_usage.usage_count < :limit
                        RETURNING usage_count
                        """)
                .param("userId", userId).param("scopeKey", scopeKey)
                .param("usageDate", LocalDate.now(zoneId)).param("action", action).param("limit", limit)
                .query(Integer.class).optional().orElse(null);
        if (count == null) {
            throw new DailyQuotaExceededException(message);
        }
    }
}
