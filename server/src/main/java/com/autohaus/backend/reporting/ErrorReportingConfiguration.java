package com.autohaus.backend.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Activates the daily AI error report only when an LLM api-key AND model are configured
 * (REQ-030). Without the secrets none of these beans exist, so the scheduler never runs
 * and the app still boots cleanly. The {@code ChatClient} is pre-wired with a
 * {@code StructuredOutputValidationAdvisor} — the LLM's structured answer is validated
 * against {@link AiErrorReportSummary} and re-issued (self-correcting) on schema violations.
 */
@Configuration
@ConditionalOnExpression("'${LLM_API_KEY:}' != '' and '${LLM_MODEL:}' != ''")
public class ErrorReportingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportingConfiguration.class);

    @Bean
    public ChatClient reportChatClient(ChatClient.Builder builder) {
        log.info("REQ-030: LLM configured — wiring ChatClient with PII redaction + structured-output validation (retry=3) + guardrail");
        return builder
            .defaultAdvisors(
                // PII redaction runs first (highest precedence) so no clear-text names,
                // e-mails, phone numbers, licence plates or VINs ever reach the model.
                // Spring AI 2.0 has no built-in DataMinimizationAdvisor — this is our own.
                new PiiRedactionAdvisor(Ordered.HIGHEST_PRECEDENCE + 100),
                StructuredOutputValidationAdvisor.builder()
                    .outputType(AiErrorReportSummary.class)
                    .maxRepeatAttempts(3)
                    .build(),
                // Guardrail: block the summary if it would ever leak secret markers.
                SafeGuardAdvisor.builder()
                    .sensitiveWords(List.of("password", "secret", "api-key", "apikey", "token", "credential"))
                    .build())
            .build();
    }

    @Bean
    public LlmSummaryService llmSummaryService(ChatClient reportChatClient) {
        return new LlmSummaryService(reportChatClient);
    }

    @Bean
    public ErrorReportScheduler errorReportScheduler(ReportAggregator aggregator,
                                                     LlmSummaryService llmSummaryService,
                                                     ReportHtmlGenerator htmlGenerator,
                                                     ReportStore store) {
        return new ErrorReportScheduler(aggregator, llmSummaryService, htmlGenerator, store);
    }
}
