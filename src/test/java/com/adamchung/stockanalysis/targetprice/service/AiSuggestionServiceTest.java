package com.adamchung.stockanalysis.targetprice.service;

import com.adamchung.stockanalysis.auth.TestSecurityContext;
import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.quota.DailyUsageService;
import com.adamchung.stockanalysis.targetprice.AiSuggestionPromptBuilder;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    @Mock private TargetPriceApplicationService targetPriceService;
    @Mock private AiSuggestionPromptBuilder promptBuilder;
    @Mock private GroqClient groqClient;
    @Mock private UserDataStore userDataStore;
    @Mock private DailyUsageService dailyUsageService;
    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        TestSecurityContext.login(42L);
        service = new AiSuggestionService(
                targetPriceService, promptBuilder, groqClient, userDataStore, dailyUsageService);
    }

    @Test
    void generate_reserves_quota_and_saves_the_normalized_result_for_current_user() {
        TargetPriceResponse snapshot = org.mockito.Mockito.mock(TargetPriceResponse.class);
        when(targetPriceService.getPersisted("2330")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(new GroqClient.GeneratedSuggestion(
                " 機會。\n 風險。僅供研究參考，非投資建議 ", "openai/gpt-oss-20b"));
        Instant updatedAt = Instant.parse("2026-07-29T10:00:00Z");
        when(userDataStore.saveTargetAiSuggestion(
                42L, "2330", "機會。\n\n風險。僅供研究參考，非投資建議", "openai/gpt-oss-20b"))
                .thenReturn(new UserDataStore.AiSuggestion("機會。\n\n風險。僅供研究參考，非投資建議",
                        "openai/gpt-oss-20b", updatedAt));

        TargetPriceResponse.AiSuggestionView result = service.generate("2330");

        verify(dailyUsageService).reserveAiSuggestion();
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void invalid_model_output_does_not_overwrite_personal_cache() {
        TargetPriceResponse snapshot = org.mockito.Mockito.mock(TargetPriceResponse.class);
        when(targetPriceService.getPersisted("2330")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("建".repeat(801), "openai/gpt-oss-20b"));

        assertThatThrownBy(() -> service.generate("2330")).isInstanceOf(GroqClientException.class);
        verify(userDataStore, never()).saveTargetAiSuggestion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /** AI 外部呼叫不得包在資料庫交易內，避免等待 Groq 時占用 connection。 */
    @Test
    void generate_doesNotOpenATransactionAroundGroq() throws NoSuchMethodException {
        Transactional transactional = AiSuggestionService.class
                .getMethod("generate", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }
}
