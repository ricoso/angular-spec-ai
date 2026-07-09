package com.autohaus.backend.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSummaryServiceTest {

    private ReportModel model() {
        ErrorEntry e = new ErrorEntry(
            Instant.parse("2026-07-09T02:00:00Z"), "t1", "volkswagen", "Essen",
            List.of("Inspektion"), "No match (Operation/Marke)", "/api/planning/snaps", "{}", "{}");
        return new ReportModel(Instant.parse("2026-07-09T03:00:00Z"),
            new BookingStats(10, 4, 6), List.of(e), null);
    }

    @Test
    void returnsTypedSummaryFromLlm() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AiErrorReportSummary expected =
            new AiErrorReportSummary("Headline", "Assessment", List.of(), List.of());
        when(chatClient.prompt().user(anyString()).call().entity(AiErrorReportSummary.class))
            .thenReturn(expected);

        AiErrorReportSummary result = new LlmSummaryService(chatClient).summarize(model());
        assertEquals("Headline", result.headline());
    }

    @Test
    void fallsBackToNeutralSummaryWhenLlmFails() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().entity(AiErrorReportSummary.class))
            .thenThrow(new RuntimeException("model unavailable"));

        AiErrorReportSummary result = new LlmSummaryService(chatClient).summarize(model());
        assertEquals("KI-Zusammenfassung nicht verfügbar", result.headline());
        assertNotNull(result.topIssues());
    }

    @Test
    void fallsBackWhenLlmReturnsNull() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().entity(AiErrorReportSummary.class))
            .thenReturn(null);

        AiErrorReportSummary result = new LlmSummaryService(chatClient).summarize(model());
        assertEquals("KI-Zusammenfassung nicht verfügbar", result.headline());
    }
}
