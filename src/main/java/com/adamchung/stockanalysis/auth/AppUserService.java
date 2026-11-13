package com.adamchung.stockanalysis.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 建立 Google 使用者、預設收藏並安全認領單人版舊資料。 */
@Service
public class AppUserService {

    private static final String DEFAULT_STOCK_TSMC = "2330";
    private static final String DEFAULT_STOCK_ESUN = "2884";

    private final JdbcClient jdbcClient;
    private final String initialAdminEmail;

    /**
     * 建立使用者服務。
     *
     * @param jdbcClient 資料庫操作邊界
     * @param initialAdminEmail 唯一可認領既有個人資料的 Google 信箱，由部署環境提供
     */
    public AppUserService(
            JdbcClient jdbcClient,
            @Value("${app.auth.initial-admin-email:}") String initialAdminEmail) {
        this.jdbcClient = jdbcClient;
        this.initialAdminEmail = initialAdminEmail == null ? "" : initialAdminEmail.strip();
    }

    /**
     * 以 Google subject 為不可變識別建立或更新本地帳號；新帳號加入兩檔預設收藏。
     * 指定管理者信箱第一次登入時，會在同一交易內認領舊版單人資料。
     *
     * @param googleSubject Google 的穩定唯一識別碼
     * @param email Google 已驗證信箱
     * @param displayName Google 顯示名稱
     * @param pictureUrl Google 頭像網址
     * @return 可放入 session 的使用者 principal
     */
    @Transactional(rollbackFor = Throwable.class)
    public AppUserPrincipal provision(
            String googleSubject,
            String email,
            String displayName,
            String pictureUrl) {
        boolean shouldBeAdmin = !initialAdminEmail.isBlank() && initialAdminEmail.equalsIgnoreCase(email);
        UserRow user = jdbcClient.sql("""
                        INSERT INTO app_user (google_subject, email, display_name, picture_url, role)
                        VALUES (:subject, :email, :name, :picture, :role)
                        ON CONFLICT (google_subject) DO UPDATE SET
                            email = EXCLUDED.email,
                            display_name = EXCLUDED.display_name,
                            picture_url = EXCLUDED.picture_url,
                            role = CASE WHEN app_user.role = 'ADMIN' THEN 'ADMIN' ELSE EXCLUDED.role END,
                            updated_at = now()
                        RETURNING id, email, display_name, picture_url, role, enabled, legacy_data_claimed
                        """)
                .param("subject", googleSubject)
                .param("email", email)
                .param("name", displayName)
                .param("picture", pictureUrl)
                .param("role", shouldBeAdmin ? "ADMIN" : "USER")
                .query(UserRow.class)
                .single();
        if (!user.enabled()) {
            throw new DisabledAccountException();
        }

        seedDefaultFavorites(user.id());
        if (shouldBeAdmin && !user.legacyDataClaimed()) {
            claimLegacyData(user.id());
            jdbcClient.sql("UPDATE app_user SET legacy_data_claimed = TRUE, updated_at = now() WHERE id = :userId")
                    .param("userId", user.id())
                    .update();
        }
        return new AppUserPrincipal(
                user.id(), user.email(), user.displayName(), user.pictureUrl(), "ADMIN".equals(user.role()));
    }

    /**
     * 刪除目前使用者與所有個人資料；外鍵 cascade 不影響共用市場資料。
     *
     * @param userId 本站使用者 id
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deleteAccount(long userId) {
        jdbcClient.sql("DELETE FROM app_user WHERE id=:userId").param("userId", userId).update();
    }

    /**
     * 新帳號預設收藏台積電與玉山金；主檔尚不存在時略過，避免破壞全新資料庫啟動。
     *
     * @param userId 本站使用者 id
     */
    private void seedDefaultFavorites(long userId) {
        jdbcClient.sql("""
                        INSERT INTO user_favorite_stock (user_id, stock_id)
                        SELECT :userId, stock_id FROM stock WHERE stock_id IN (:tsmc, :esun)
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """)
                .param("userId", userId)
                .param("tsmc", DEFAULT_STOCK_TSMC)
                .param("esun", DEFAULT_STOCK_ESUN)
                .update();
    }

    /**
     * 將 Google 登入前的單人資料複製到指定 owner；共用市場/財報值仍留在原表。
     *
     * @param userId owner 的本站使用者 id
     */
    private void claimLegacyData(long userId) {
        jdbcClient.sql("""
                        INSERT INTO user_favorite_stock (user_id, stock_id, created_at, updated_at)
                        SELECT :userId, stock_id, created_at, updated_at FROM favorite_stock
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).update();
        jdbcClient.sql("""
                        INSERT INTO user_target_price_setting
                            (user_id, stock_id, investment_strategy, eps_avg_years, bps_avg_years,
                             setting_note, target_price_level, created_at, updated_at)
                        SELECT :userId, stock_id, investment_strategy, eps_avg_years, bps_avg_years,
                               setting_note, target_price_level, created_at, updated_at
                        FROM stock_target_price_setting
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).update();
        jdbcClient.sql("""
                        INSERT INTO user_stock_ai_suggestion
                            (user_id, stock_id, suggestion_text, model_name, created_at, updated_at)
                        SELECT :userId, stock_id, suggestion_text, model_name, created_at, updated_at
                        FROM stock_ai_suggestion
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).update();
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_profile
                            (user_id, stock_id, profile, custom_note, last_reviewed_quarter, created_at, updated_at)
                        SELECT :userId, stock_id, profile, custom_note, last_reviewed_quarter, created_at, updated_at
                        FROM stock_health_profile
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).update();
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_note
                            (user_id, stock_id, period, metric_key, note, created_at, updated_at)
                        SELECT :userId, stock_id, quarter, metric_key, note, created_at, updated_at
                        FROM stock_health_snapshot WHERE note IS NOT NULL
                        ON CONFLICT (user_id, stock_id, period, metric_key) DO NOTHING
                        """).param("userId", userId).update();
        jdbcClient.sql("""
                        INSERT INTO user_stock_health_ai_suggestion
                            (user_id, stock_id, suggestion_text, model_name, created_at, updated_at)
                        SELECT :userId, stock_id, suggestion_text, model_name, created_at, updated_at
                        FROM stock_health_ai_suggestion
                        ON CONFLICT (user_id, stock_id) DO NOTHING
                        """).param("userId", userId).update();
    }

    /** 登入用的資料庫投影。 */
    private record UserRow(
            long id, // 本站 user id
            String email, // Google 已驗證信箱
            String displayName, // 顯示名稱
            String pictureUrl, // 頭像網址
            String role, // USER 或 ADMIN
            boolean enabled, // 管理者是否允許登入
            boolean legacyDataClaimed // 是否已完成舊資料認領
    ) {
    }
}
