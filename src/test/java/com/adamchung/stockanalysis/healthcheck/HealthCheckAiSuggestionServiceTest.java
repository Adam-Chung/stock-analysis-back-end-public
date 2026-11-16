package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.auth.TestSecurityContext;
import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.quota.DailyUsageService;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckAiSuggestionServiceTest {

    @Mock private HealthCheckApplicationService healthCheckService;
    @Mock private HealthCheckAiSuggestionPromptBuilder promptBuilder;
    @Mock private GroqClient groqClient;
    @Mock private UserDataStore userDataStore;
    @Mock private DailyUsageService dailyUsageService;
    private HealthCheckAiSuggestionService service;

    @BeforeEach
    void setUp() {
        TestSecurityContext.login(42L);
        service = new HealthCheckAiSuggestionService(
                healthCheckService, promptBuilder, groqClient, userDataStore, dailyUsageService);
    }

    @Test
    void generate_saves_a_personal_health_suggestion_after_reserving_quota() {
        HealthCheckResponse snapshot = org.mockito.Mockito.mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("體質結論。", "openai/gpt-oss-20b"));
        Instant updatedAt = Instant.parse("2026-08-07T10:00:00Z");
        when(userDataStore.saveHealthAiSuggestion(42L, "1216", "體質結論。", "openai/gpt-oss-20b"))
                .thenReturn(new UserDataStore.AiSuggestion("體質結論。", "openai/gpt-oss-20b", updatedAt));

        HealthCheckResponse.AiSuggestionView result = service.generate("1216");

        verify(dailyUsageService).reserveAiSuggestion();
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    /** 企業體質 AI 外部呼叫不得包在資料庫交易內。 */
    @Test
    void generate_doesNotOpenATransactionAroundGroq() throws NoSuchMethodException {
        Transactional transactional = HealthCheckAiSuggestionService.class
                .getMethod("generate", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }
}
