package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthAiSuggestionEntity;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthAiSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckAiSuggestionServiceTest {

    @Mock
    private HealthCheckApplicationService healthCheckService;
    @Mock
    private HealthCheckAiSuggestionPromptBuilder promptBuilder;
    @Mock
    private GroqClient groqClient;
    @Mock
    private StockHealthAiSuggestionRepository repository;

    private HealthCheckAiSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new HealthCheckAiSuggestionService(healthCheckService, promptBuilder, groqClient, repository);
    }

    @Test
    void generate_uses_the_persisted_screen_data_and_saves_a_valid_suggestion() {
        HealthCheckResponse snapshot = mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(new GroqClient.GeneratedSuggestion(
                " 不達標原因。  \n  值不值得投資的結論。\n\n財報體質的結論。僅供研究參考，非投資建議 ",
                "groq/compound-mini"));
        when(repository.findById("1216")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(StockHealthAiSuggestionEntity.class))).thenAnswer(invocation -> {
            StockHealthAiSuggestionEntity saved = invocation.getArgument(0);
            saved.setUpdatedAt(Instant.parse("2026-08-07T10:00:00Z"));
            return saved;
        });

        HealthCheckResponse.AiSuggestionView result = service.generate("1216");

        ArgumentCaptor<StockHealthAiSuggestionEntity> captor = ArgumentCaptor.forClass(StockHealthAiSuggestionEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        // DEC-083：normalize() 改成保留段落換行（單一或連續換行都視為段落分隔，段落內部
        // 才壓平空白），不能再像舊版一樣把所有換行壓成一個空格，否則 prompt 要求的
        // 「分成三段」形同虛設。
        assertThat(captor.getValue().getSuggestionText())
                .isEqualTo("不達標原因。\n\n值不值得投資的結論。\n\n財報體質的結論。僅供研究參考，非投資建議");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(AuditActors.HEALTH_CHECK_AI_SUGGESTION);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(AuditActors.HEALTH_CHECK_AI_SUGGESTION);
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-08-07T10:00:00Z"));
        verify(healthCheckService).get("1216");
    }

    @Test
    void generate_updates_an_existing_cached_row_instead_of_inserting_a_duplicate() {
        HealthCheckResponse snapshot = mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("新的建議內容", "groq/compound-mini"));
        StockHealthAiSuggestionEntity existing = new StockHealthAiSuggestionEntity();
        existing.setStockId("1216");
        existing.setSuggestionText("舊的建議內容");
        when(repository.findById("1216")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(StockHealthAiSuggestionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate("1216");

        ArgumentCaptor<StockHealthAiSuggestionEntity> captor = ArgumentCaptor.forClass(StockHealthAiSuggestionEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getSuggestionText()).isEqualTo("新的建議內容");
    }

    @Test
    void generate_does_not_overwrite_cache_when_response_exceeds_400_characters() {
        HealthCheckResponse snapshot = mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("建".repeat(401), "groq/compound-mini"));

        assertThatThrownBy(() -> service.generate("1216"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("400");
        verify(repository, never()).findById("1216");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void generate_does_not_overwrite_cache_when_response_is_blank() {
        HealthCheckResponse snapshot = mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("   ", "groq/compound-mini"));

        assertThatThrownBy(() -> service.generate("1216")).isInstanceOf(GroqClientException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void generate_does_not_overwrite_cache_when_model_name_is_missing() {
        HealthCheckResponse snapshot = mock(HealthCheckResponse.class);
        when(healthCheckService.get("1216")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(new GroqClient.GeneratedSuggestion("建議內容", " "));

        assertThatThrownBy(() -> service.generate("1216")).isInstanceOf(GroqClientException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
