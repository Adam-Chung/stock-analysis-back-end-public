package com.adamchung.stockanalysis.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 管理者查看與停用本站 Google 使用者。 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final JdbcClient jdbcClient;

    /** 建立管理者使用者邊界。 */
    public AdminUserController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 列出帳號，不包含 Google subject 或任何憑證。 */
    @GetMapping
    public List<AdminUserView> list() {
        return jdbcClient.sql("""
                        SELECT id, email, display_name, role, enabled, created_at, updated_at
                        FROM app_user ORDER BY created_at
                        """).query(AdminUserView.class).list();
    }

    /** 更新帳號啟用狀態；管理者不可停用自己以免鎖死管理入口。 */
    @PatchMapping("/{userId}")
    @Transactional(rollbackFor = Throwable.class)
    public AdminUserView update(@PathVariable long userId, @Valid @RequestBody UpdateUserRequest request) {
        if (userId == CurrentUser.id() && !request.enabled()) {
            throw new IllegalArgumentException("管理者不可停用目前自己的帳號");
        }
        return jdbcClient.sql("""
                        UPDATE app_user SET enabled=:enabled, updated_at=now() WHERE id=:userId
                        RETURNING id, email, display_name, role, enabled, created_at, updated_at
                        """).param("enabled", request.enabled()).param("userId", userId)
                .query(AdminUserView.class).optional()
                .orElseThrow(() -> new AppUserNotFoundException(userId));
    }

    /** 管理者看到的帳號摘要。 */
    public record AdminUserView(
            long id, // 本站 user id
            String email, // Google 已驗證信箱
            String displayName, // 顯示名稱
            String role, // USER 或 ADMIN
            boolean enabled, // 是否允許使用
            Instant createdAt, // 註冊時間
            Instant updatedAt // 最後更新時間
    ) {
    }

    /** 管理者帳號狀態更新請求。 */
    public record UpdateUserRequest(
            @NotNull Boolean enabled // 是否允許該帳號使用本站
    ) {
    }
}
