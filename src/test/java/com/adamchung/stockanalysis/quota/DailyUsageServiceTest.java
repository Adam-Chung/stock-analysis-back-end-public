package com.adamchung.stockanalysis.quota;

import com.adamchung.stockanalysis.auth.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 驗證管理者與一般使用者的每日額度差異。 */
class DailyUsageServiceTest {

    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final DailyUsageService service = new DailyUsageService(
            jdbcClient, "Asia/Taipei", 3, 3, 30);

    /** 清除測試執行緒的登入身分，避免影響其他案例。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 管理者執行企業體質同步時不占用個人每日額度。 */
    @Test
    void reserveHealthExternalSync_skipsDailyLimitForAdmin() {
        login(true);

        service.reserveHealthExternalSync();

        verifyNoInteractions(jdbcClient);
    }

    /** 管理者產生 AI 建議時略過個人額度，但仍保留全站免費額度。 */
    @Test
    void reserveAiSuggestion_skipsUserLimitButKeepsGlobalLimitForAdmin() {
        JdbcClient.StatementSpec statement = successfulReservation();
        login(true);

        service.reserveAiSuggestion();

        verify(statement).param("scopeKey", "GLOBAL");
        verify(statement, never()).param("scopeKey", "USER:7");
    }

    /** 一般使用者產生 AI 建議時同時占用個人與全站額度。 */
    @Test
    void reserveAiSuggestion_reservesUserAndGlobalLimitsForUser() {
        JdbcClient.StatementSpec statement = successfulReservation();
        login(false);

        service.reserveAiSuggestion();

        verify(statement).param("scopeKey", "USER:7");
        verify(statement).param("scopeKey", "GLOBAL");
    }

    /** AI 額度必須使用獨立交易提交，避免後續外部呼叫失敗時退回額度。 */
    @Test
    void reserveAiSuggestion_commitsInAnIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = DailyUsageService.class
                .getMethod("reserveAiSuggestion")
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor()).containsExactly(Throwable.class);
    }

    /**
     * 建立會回傳成功計數的 JDBC fluent mock。
     *
     * @return 可供驗證參數的 statement mock
     */
    @SuppressWarnings("unchecked")
    private JdbcClient.StatementSpec successfulReservation() {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Integer> query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), nullable(Object.class))).thenReturn(statement);
        when(statement.query(Integer.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.of(1));
        return statement;
    }

    /**
     * 將指定角色放入目前測試執行緒。
     *
     * @param admin 是否為管理者
     */
    private void login(boolean admin) {
        AppUserPrincipal principal = new AppUserPrincipal(7L, "user@example.com", "User", null, admin);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }
}
