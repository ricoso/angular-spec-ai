package com.autohaus.backend.reporting;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Spring AI advisor that strips PII from the user message before it reaches the LLM
 * (REQ-030, Iteration 2). Second layer of defense-in-depth: the report prompt already
 * contains only aggregates (no raw payloads), but this advisor guarantees that even if a
 * clear-text e-mail, phone number, licence plate or VIN ever leaked into an aggregate it is
 * masked before the outbound call.
 *
 * <p>Zweite Schutzschicht auf dem LLM-Pfad: maskiert E-Mail, Telefonnummer, Kennzeichen und
 * VIN in der User-Message, bevor der Call rausgeht. Spring AI 2.0 bringt (noch) keinen
 * eingebauten {@code DataMinimizationAdvisor} mit, daher dieser eigene {@link BaseAdvisor}.
 */
public class PiiRedactionAdvisor implements BaseAdvisor {

    private static final String MASK = "***";

    private static final List<Pattern> PATTERNS = List.of(
        // e-mail addresses / E-Mail-Adressen
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        // German licence plates / KFZ-Kennzeichen, e.g. "M-AB 1234E"
        Pattern.compile("\\b[A-ZÄÖÜ]{1,3}-[A-Z]{1,2}\\s?\\d{1,4}[EH]?\\b"),
        // vehicle identification number / Fahrgestellnummer (17 chars, no I/O/Q)
        Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b"),
        // phone numbers / Telefonnummern (>= 9 digits, optional +, spaces, dashes, parens)
        Pattern.compile("(?<![\\dA-Za-z])\\+?\\d[\\d /()-]{7,}\\d"));

    private final int order;

    public PiiRedactionAdvisor(int order) {
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Prompt masked = request.prompt()
            .augmentUserMessage(userMessage -> userMessage.mutate()
                .text(mask(userMessage.getText()))
                .build());
        return request.mutate().prompt(masked).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // No response-side transformation — this advisor only sanitizes the outbound prompt.
        return response;
    }

    @Override
    public int getOrder() {
        return order;
    }

    private static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = text;
        for (Pattern pattern : PATTERNS) {
            out = pattern.matcher(out).replaceAll(MASK);
        }
        return out;
    }
}
