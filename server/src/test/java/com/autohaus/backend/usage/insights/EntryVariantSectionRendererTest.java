package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-042 (G-4) — rendering of the „Einstieg" section (entry variant distribution). Anonymous
 * aggregate: counts + share of normal / Radwechsel / Rückrufaktion, same empty-state contract as
 * {@link ServiceGroupSectionRenderer}. / Rendering der Einstiegs-Variante.
 */
class EntryVariantSectionRendererTest {

    private final EntryVariantSectionRenderer renderer = new EntryVariantSectionRenderer();

    private static Cell count(long value, double percent) {
        return new Cell(value, null, percent, 0, CellState.VALUE);
    }

    private static Period month() {
        Map<String, Cell> variants = new LinkedHashMap<>();
        variants.put("default", count(200, 70.0));
        variants.put("tireChange", count(60, 21.0));
        variants.put("recall", count(26, 9.0));
        return new Period("aktueller Monat bis gestern", "01.11.–18.11.2026", Cell.of(286),
            Map.of(), Map.of(), Map.of(), Map.of(), Cell.noData(), null, null, null, variants);
    }

    private static InsightsViewModel model(boolean bookingsEnabled, Period month, List<String> warnings) {
        return new InsightsViewModel(LocalDate.of(2026, 11, 18), bookingsEnabled, false,
            LocalDate.of(2026, 9, 1), null, null, null, false, 0, List.of(), List.of(), month, null, null,
            Cell.tooFew(), null, warnings);
    }

    @Test
    @DisplayName("renders the three entry variants with counts and shares")
    void rendersVariants() {
        String html = renderer.render(model(true, month(), List.of()));
        assertTrue(html.contains("entry-variant"), "section id");
        assertTrue(html.contains(EntryVariantSectionRenderer.HEADING), "heading");
        assertTrue(html.contains("Normal"), "default label");
        assertTrue(html.contains("Radwechsel"), "tireChange label");
        assertTrue(html.contains("Rückrufaktion"), "recall label");
        assertTrue(html.contains("200") && html.contains("60") && html.contains("26"), "counts");
    }

    @Test
    @DisplayName("empty when bookings are disabled (BR-4)")
    void emptyWhenDisabled() {
        assertEquals("", renderer.render(model(false, month(), List.of())));
    }

    @Test
    @DisplayName("empty for a null view model")
    void emptyWhenNull() {
        assertEquals("", renderer.render(null));
    }

    @Test
    @DisplayName("history warning suppresses the bars but keeps the heading")
    void historyWarningKeepsHeadingOnly() {
        String html = renderer.render(model(true, month(),
            List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)));
        assertTrue(html.contains(EntryVariantSectionRenderer.HEADING), "heading stays");
        assertFalse(html.contains("<ul class=\"dev\""), "no bar list under the history warning");
    }
}
