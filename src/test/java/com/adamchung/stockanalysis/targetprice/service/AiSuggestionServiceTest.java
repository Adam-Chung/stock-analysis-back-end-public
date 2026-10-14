package com.adamchung.stockanalysis.targetprice.service;

import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.targetprice.AiSuggestionPromptBuilder;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.entity.StockAiSuggestionEntity;
import com.adamchung.stockanalysis.targetprice.repository.StockAiSuggestionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    @Mock
    private TargetPriceApplicationService targetPriceService;
    @Mock
    private AiSuggestionPromptBuilder promptBuilder;
    @Mock
    private GroqClient groqClient;
    @Mock
    private StockAiSuggestionRepository repository;

    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new AiSuggestionService(targetPriceService, promptBuilder, groqClient, repository);
    }

    @Test
    void generate_uses_persisted_snapshot_and_saves_valid_suggestion() {
        TargetPriceResponse snapshot = org.mockito.Mockito.mock(TargetPriceResponse.class);
        when(targetPriceService.getPersisted("2330")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(new GroqClient.GeneratedSuggestion(
                " 可能機會的說明。  \n  主要風險的說明。僅供研究參考，非投資建議 ",
                "groq/compound-mini"));
        when(repository.findById("2330")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(StockAiSuggestionEntity.class))).thenAnswer(invocation -> {
            StockAiSuggestionEntity saved = invocation.getArgument(0);
            saved.setUpdatedAt(Instant.parse("2026-07-29T10:00:00Z"));
            return saved;
        });

        TargetPriceResponse.AiSuggestionView result = service.generate("2330");

        ArgumentCaptor<StockAiSuggestionEntity> captor = ArgumentCaptor.forClass(StockAiSuggestionEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        // DEC-083：normalize() 改成保留段落換行（單一或連續換行都視為段落分隔，段落內部
        // 才壓平空白），不能再像舊版一樣把所有換行壓成一個空格，否則 prompt 要求的
        // 「分成兩段」形同虛設。
        assertThat(captor.getValue().getSuggestionText())
                .isEqualTo("可能機會的說明。\n\n主要風險的說明。僅供研究參考，非投資建議");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("groq-suggestion");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo("groq-suggestion");
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-07-29T10:00:00Z"));
        verify(targetPriceService).getPersisted("2330");
    }

    @Test
    void generate_does_not_overwrite_cache_when_response_exceeds_200_characters() {
        TargetPriceResponse snapshot = org.mockito.Mockito.mock(TargetPriceResponse.class);
        when(targetPriceService.getPersisted("2330")).thenReturn(snapshot);
        when(promptBuilder.build(snapshot)).thenReturn("prompt");
        when(groqClient.generate("prompt")).thenReturn(
                new GroqClient.GeneratedSuggestion("建".repeat(201), "groq/compound-mini"));

        assertThatThrownBy(() -> service.generate("2330"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("200");
        verify(repository, never()).findById("2330");
        verify(repository, never()).saveAndFlush(any());
    }
}
