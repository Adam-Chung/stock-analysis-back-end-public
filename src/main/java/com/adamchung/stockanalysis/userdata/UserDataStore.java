package com.adamchung.stockanalysis.userdata;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 所有個人化資料的 user_id 邊界；呼叫端不可直接省略使用者維度。 */
@Repository
public class UserDataStore {

    private final JdbcClient jdbcClient;

    /**
     * 建立個人資料存取邊界。
     *
     * @param jdbcClient Spring JDBC client
     */
    public UserDataStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 依加入順序列出使用者收藏的股票代號。 */
    public List<String> favoriteStockIds(long userId) {
        return jdbcClient.sql("SELECT stock_id FROM user_favorite_stock WHERE user_id=:userId ORDER BY created_at, id")
                .param("userId", userId).query(String.class).list();
    }

    /** 冪等新增收藏。 */
    public void addFavorite(long userId, String stockId) {
        jdbcClient.sql("""
                        INSERT INTO user_favorite_stock (user_id, stock_id) VALUES (:userId, :stockId)
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).param("stockId", stockId).update();
    }

    /** 刪除指定使用者自己的收藏。 */
    public void removeFavorite(long userId, String stockId) {
        jdbcClient.sql("DELETE FROM user_favorite_stock WHERE user_id=:userId AND stock_id=:stockId")
                .param("userId", userId).param("stockId", stockId).update();
    }

    /** 讀取使用者的個股估值設定。 */
    public Optional<TargetPriceSetting> targetPriceSetting(long userId, String stockId) {
        return jdbcClient.sql("""
                        SELECT investment_strategy, eps_avg_years, bps_avg_years, setting_note, target_price_level
                        FROM user_target_price_setting WHERE user_id=:userId AND stock_id=:stockId
                        """).param("userId", userId).param("stockId", stockId)
                .query(TargetPriceSetting.class).optional();
    }

    /** 新增或覆蓋使用者自己的估值設定。 */
    public void saveTargetPriceSetting(long userId, String stockId, TargetPriceSetting setting) {
        jdbcClient.sql("""
                        INSERT INTO user_target_price_setting
                            (user_id, stock_id, investment_strategy, eps_avg_years, bps_avg_years,
                             setting_note, target_price_level)
                        VALUES (:userId, :stockId, :strategy, :eps, :bps, :note, :level)
                        ON CONFLICT (user_id, stock_id) DO UPDATE SET
                            investment_strategy=EXCLUDED.investment_strategy,
                            eps_avg_years=EXCLUDED.eps_avg_years,
                            bps_avg_years=EXCLUDED.bps_avg_years,
                            setting_note=EXCLUDED.setting_note,
                            target_price_level=EXCLUDED.target_price_level,
                            updated_at=now()
                        """)
                .param("userId", userId).param("stockId", stockId)
                .param("strategy", setting.investmentStrategy()).param("eps", setting.epsAvgYears())
                .param("bps", setting.bpsAvgYears()).param("note", setting.settingNote())
                .param("level", setting.targetPriceLevel()).update();
    }

    /** 讀取目標價頁的個人 AI 建議。 */
    public Optional<AiSuggestion> targetAiSuggestion(long userId, String stockId) {
        return aiSuggestion("user_stock_ai_suggestion", userId, stockId);
    }

    /** 儲存目標價頁的個人 AI 建議。 */
    @Transactional(rollbackFor = Throwable.class)
    public AiSuggestion saveTargetAiSuggestion(long userId, String stockId, String text, String modelName) {
        return saveAiSuggestion("user_stock_ai_suggestion", userId, stockId, text, modelName);
    }

    /** 讀取企業體質頁的個人 AI 建議。 */
    public Optional<AiSuggestion> healthAiSuggestion(long userId, String stockId) {
        return aiSuggestion("user_stock_health_ai_suggestion", userId, stockId);
    }

    /** 儲存企業體質頁的個人 AI 建議。 */
    @Transactional(rollbackFor = Throwable.class)
    public AiSuggestion saveHealthAiSuggestion(long userId, String stockId, String text, String modelName) {
        return saveAiSuggestion("user_stock_health_ai_suggestion", userId, stockId, text, modelName);
    }

    /** 讀取使用者的企業體質簡介、備註與上次確認季度。 */
    public Optional<HealthProfile> healthProfile(long userId, String stockId) {
        return jdbcClient.sql("""
                        SELECT profile, custom_note, last_reviewed_quarter, updated_at
                        FROM user_stock_health_profile WHERE user_id=:userId AND stock_id=:stockId
                        """).param("userId", userId).param("stockId", stockId)
                .query(HealthProfile.class).optional();
    }

    /** 更新使用者自己的企業簡介與整體備註，保留上次確認季度。 */
    public void saveHealthProfile(long userId, String stockId, String profile, String customNote) {
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_profile (user_id, stock_id, profile, custom_note)
                        VALUES (:userId, :stockId, :profile, :customNote)
                        ON CONFLICT (user_id, stock_id) DO UPDATE SET
                            profile=EXCLUDED.profile, custom_note=EXCLUDED.custom_note, updated_at=now()
                        """).param("userId", userId).param("stockId", stockId)
                .param("profile", profile).param("customNote", customNote).update();
    }

    /** 更新目前使用者的「上次檢視季度」。 */
    public void touchHealthReview(long userId, String stockId, String reviewedQuarter) {
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_profile (user_id, stock_id, last_reviewed_quarter)
                        VALUES (:userId, :stockId, :quarter)
                        ON CONFLICT (user_id, stock_id) DO UPDATE SET
                            last_reviewed_quarter=EXCLUDED.last_reviewed_quarter, updated_at=now()
                        """).param("userId", userId).param("stockId", stockId)
                .param("quarter", reviewedQuarter).update();
    }

    /** 讀取指定股票的全部個人細項筆記，以 period + metricKey 當索引。 */
    public Map<HealthNoteKey, String> healthNotes(long userId, String stockId) {
        return jdbcClient.sql("""
                        SELECT period, metric_key, note FROM user_stock_health_note
                        WHERE user_id=:userId AND stock_id=:stockId
                        """).param("userId", userId).param("stockId", stockId)
                .query(HealthNoteRow.class).list().stream()
                .collect(Collectors.toMap(row -> new HealthNoteKey(row.period(), row.metricKey()),
                        HealthNoteRow::note, (first, second) -> second));
    }

    /** 新增或覆蓋使用者的一筆細項筆記。 */
    public void saveHealthNote(long userId, String stockId, String period, String metricKey, String note) {
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_note (user_id, stock_id, period, metric_key, note)
                        VALUES (:userId, :stockId, :period, :metricKey, :note)
                        ON CONFLICT (user_id, stock_id, period, metric_key) DO UPDATE SET
                            note=EXCLUDED.note, updated_at=now()
                        """).param("userId", userId).param("stockId", stockId)
                .param("period", period).param("metricKey", metricKey).param("note", note).update();
    }

    /** 共用的 AI 建議讀取實作；table 名稱只由本類別內固定常數呼叫。 */
    private Optional<AiSuggestion> aiSuggestion(String table, long userId, String stockId) {
        return jdbcClient.sql("SELECT suggestion_text, model_name, updated_at FROM " + table
                        + " WHERE user_id=:userId AND stock_id=:stockId")
                .param("userId", userId).param("stockId", stockId).query(AiSuggestion.class).optional();
    }

    /** 共用的 AI 建議 upsert 實作；table 名稱只由本類別內固定常數呼叫。 */
    private AiSuggestion saveAiSuggestion(String table, long userId, String stockId, String text, String modelName) {
        return jdbcClient.sql("INSERT INTO " + table + " (user_id, stock_id, suggestion_text, model_name) "
                        + "VALUES (:userId,:stockId,:text,:model) ON CONFLICT (user_id,stock_id) DO UPDATE SET "
                        + "suggestion_text=EXCLUDED.suggestion_text, model_name=EXCLUDED.model_name, updated_at=now() "
                        + "RETURNING suggestion_text, model_name, updated_at")
                .param("userId", userId).param("stockId", stockId).param("text", text).param("model", modelName)
                .query(AiSuggestion.class).single();
    }

    /** 個人估值設定。 */
    public record TargetPriceSetting(
            String investmentStrategy, // 個人投資策略
            short epsAvgYears, // EPS 平均年數，0 代表不參考
            short bpsAvgYears, // BPS 平均年數，0 代表不參考
            String settingNote, // 個人估值備註
            short targetPriceLevel // 1 到 6 的目標價級距
    ) {
    }

    /** 個人 AI 建議。 */
    public record AiSuggestion(
            String suggestionText, // AI 建議文字
            String modelName, // 實際使用模型
            Instant updatedAt // 產生時間
    ) {
    }

    /** 個人企業體質頁表頭資料。 */
    public record HealthProfile(
            String profile, // 個人公司簡介
            String customNote, // 個人整體備註
            String lastReviewedQuarter, // 個人上次確認季度
            Instant updatedAt // 個人資料最後更新時間
    ) {
    }

    /** 個人企業細項筆記索引。 */
    public record HealthNoteKey(
            String period, // YYYYQ# 或 YYYY-MM
            String metricKey // 企業體質細項 key
    ) {
    }

    /** 個人企業細項筆記資料庫投影。 */
    private record HealthNoteRow(
            String period, // 期別
            String metricKey, // 細項 key
            String note // 個人筆記
    ) {
    }
}
