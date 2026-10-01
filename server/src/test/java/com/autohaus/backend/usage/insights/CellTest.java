package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.JSON;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.assertNoHiddenValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link Cell} invariant — second line of defence for AC-20 (REQ-038 §10.5, BR-9 Nr. 5,
 * TC-57) / die Zell-Invariante als zweite Schutzlinie. Whoever constructs a cell, a hidden state
 * never carries a number or a heat level, a missing state is „keine Daten" and heat stays in
 * {@code 0..4}. / Wer auch immer eine Zelle baut: ausgeblendet heißt ohne Zahl und ohne Heat.
 */
class CellTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CellState.class, names = "VALUE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("AC-20: a hidden state drops value, ratio, percent and heat — even from a broken call")
    void hiddenStatesCarryNoNumber(CellState state) {
        Cell broken = new Cell(3L, 1.5, 12.0, 3, state);

        assertNull(broken.value());
        assertNull(broken.ratio());
        assertNull(broken.percent());
        assertEquals(0, broken.heat());
        assertEquals(state, broken.state());
        assertEquals(InsightsViewModelSuppression.hiddenCell(state), broken, "equals the value-free factory");
    }

    @Test
    @DisplayName("BR-19: a missing state becomes NO_DATA without any number")
    void missingStateIsNoData() {
        Cell cell = new Cell(5L, 2.0, 50.0, 4, null);

        assertEquals(CellState.NO_DATA, cell.state());
        assertEquals(Cell.noData(), cell);
    }

    @Test
    @DisplayName("§10.5: a visible cell keeps its numbers; heat is clamped to 0–4")
    void visibleCellKeepsNumbersAndClampsHeat() {
        Cell cell = new Cell(12L, 1.5, 38.0, 3, CellState.VALUE);

        assertEquals(12L, cell.value());
        assertEquals(1.5, cell.ratio());
        assertEquals(38.0, cell.percent());
        assertEquals(3, cell.heat());
        assertEquals(Cell.MAX_HEAT, Cell.of(40, 9).heat(), "above 4 → 4");
        assertEquals(0, Cell.percentOf(20.0, -2).heat(), "below 0 → 0");
        assertEquals(Cell.MAX_HEAT, Cell.percentOf(45.0, Cell.MAX_HEAT).heat());
        assertEquals(0, Cell.of(7).heat());
    }

    @Test
    @DisplayName("the factories are unchanged: one number per kind, hidden factories value-free")
    void factories() {
        assertEquals(new Cell(7L, null, null, 0, CellState.VALUE), Cell.of(7));
        assertEquals(new Cell(null, null, 19.0, 0, CellState.VALUE), Cell.percentOf(19.0));
        assertEquals(new Cell(null, 14.1, null, 0, CellState.VALUE), Cell.ratioOf(14.1));
        assertEquals(new Cell(null, null, null, 0, CellState.SUPPRESSED), Cell.suppressed());
        assertEquals(new Cell(null, null, null, 0, CellState.WITHHELD), Cell.withheld());
        assertEquals(new Cell(null, null, null, 0, CellState.TOO_FEW), Cell.tooFew());
        assertEquals(new Cell(null, null, null, 0, CellState.NO_DATA), Cell.noData());
    }

    @Test
    @DisplayName("TC-57 JSON: a broken hidden cell is serialised with null numbers and heat 0")
    void brokenCellSerialisesWithoutValue() throws Exception {
        Cell broken = new Cell(3L, 1.5, 12.0, 3, CellState.SUPPRESSED);

        JsonNode json = JSON.valueToTree(broken);
        assertTrue(json.get("value").isNull());
        assertTrue(json.get("ratio").isNull());
        assertTrue(json.get("percent").isNull());
        assertEquals(0, json.get("heat").asInt());
        assertEquals("SUPPRESSED", json.get("state").asText());
        assertEquals(1, assertNoHiddenValues(broken));
    }
}
