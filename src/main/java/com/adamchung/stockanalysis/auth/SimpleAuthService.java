package com.adamchung.stockanalysis.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** 使用資料庫帳密與無期限 Token 提供臨時登入防護。 */
@Service
public class SimpleAuthService {

    private final JdbcClient jdbcClient;

    public SimpleAuthService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 驗證明碼帳密，成功時建立新的永久 Token。 */
    public Optional<String> login(String username, String password) {
        return jdbcClient.sql("""
                        SELECT id FROM app_user
                        WHERE username = :username AND password = :password AND enabled = TRUE
                        """)
                .param("username", username)
                .param("password", password)
                .query(Long.class)
                .optional()
                .map(this::createSession);
    }

    /** 確認 Token 是否仍存在於資料庫。 */
    public boolean isValid(String token) {
        return jdbcClient.sql("SELECT COUNT(*) FROM auth_session WHERE token = :token")
                .param("token", token)
                .query(Integer.class)
                .single() > 0;
    }

    /** 刪除目前 Token，使該瀏覽器登出。 */
    public void logout(String token) {
        jdbcClient.sql("DELETE FROM auth_session WHERE token = :token").param("token", token).update();
    }

    /** 為指定使用者建立不可預測的 Token。 */
    private String createSession(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        jdbcClient.sql("INSERT INTO auth_session (token, user_id) VALUES (:token, :userId)")
                .param("token", token)
                .param("userId", userId)
                .update();
        return token;
    }
}

