package com.adamchung.stockanalysis.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 驗證管理者帳號更新端點的找不到資料行為。 */
class AdminUserControllerTest {

    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final AdminUserController controller = new AdminUserController(jdbcClient);

    /** 清除測試執行緒的登入身分。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 更新不存在的使用者時拋出可映射為 404 的 domain exception。 */
    @Test
    @SuppressWarnings("unchecked")
    void update_throwsUserNotFoundWhenNoRowWasUpdated() {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<AdminUserController.AdminUserView> query =
                mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(AdminUserController.AdminUserView.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.empty());
        TestSecurityContext.login(42L);

        assertThatThrownBy(() -> controller.update(999L, new AdminUserController.UpdateUserRequest(true)))
                .isInstanceOf(AppUserNotFoundException.class)
                .hasMessage("找不到使用者：999");
    }
}
