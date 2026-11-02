package com.autohaus.backend.config.setup;

import com.autohaus.backend.config.FeatureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one branchId → display name map of the statistics tab (REQ-038 §14.1) / die eine
 * Standortnamen-Map des Statistik-Tabs.
 */
class BranchNamesTest {

    private static final String SETUP_JSON = """
        {
          "branches": [
            { "branchId": "b-zeta", "name": "Zeta Nord",
              "groups": [{ "variant": "default", "groupId": "g-zeta", "fullName": "Zeta Nord" }] },
            { "branchId": "b-alpha", "name": "alpha Süd",
              "groups": [{ "variant": "default", "groupId": "g-alpha", "fullName": "alpha Süd" }] },
            { "branchId": "b-mitte", "name": "Mitte",
              "groups": [{ "variant": "default", "groupId": "g-mitte", "fullName": "Mitte" }] }
          ]
        }
        """;

    @Test
    @DisplayName("real setup: alphabetical by name like getAllBranches()")
    void keepsTheOrderOfTheSetup() {
        SetupService setup = new SetupService(new ObjectMapper(), new FeatureProperties(true), SETUP_JSON,
            "ignored-because-inline-is-set");
        setup.init();

        Map<String, String> names = BranchNames.byId(setup);

        assertEquals(List.of("b-alpha", "b-mitte", "b-zeta"), List.copyOf(names.keySet()));
        assertEquals(List.of("alpha Süd", "Mitte", "Zeta Nord"), List.copyOf(names.values()));
        assertEquals(List.copyOf(names.keySet()),
            setup.getAllBranches().stream().map(BranchSetup::branchId).toList(), "same order as the setup");
    }

    @Test
    @DisplayName("stable order: equal names (ignoring case) are ordered by branchId, whatever the input order")
    void ordersEqualNamesByBranchId() {
        SetupService forward = mock(SetupService.class);
        when(forward.getAllBranches()).thenReturn(List.of(
            new BranchSetup("b-2", "Nord", null, List.of()),
            new BranchSetup("b-1", "nord", null, List.of()),
            new BranchSetup("b-0", "Ahaus", null, List.of())));
        SetupService backward = mock(SetupService.class);
        when(backward.getAllBranches()).thenReturn(List.of(
            new BranchSetup("b-1", "nord", null, List.of()),
            new BranchSetup("b-0", "Ahaus", null, List.of()),
            new BranchSetup("b-2", "Nord", null, List.of())));

        assertEquals(List.of("b-0", "b-1", "b-2"), List.copyOf(BranchNames.byId(forward).keySet()));
        assertEquals(List.of("b-0", "b-1", "b-2"), List.copyOf(BranchNames.byId(backward).keySet()));
    }

    @Test
    @DisplayName("null service → empty; no branchId → skipped; no name → branchId; first id wins")
    void isNullSafe() {
        assertTrue(BranchNames.byId(null).isEmpty());

        SetupService setup = mock(SetupService.class);
        when(setup.getAllBranches()).thenReturn(Arrays.asList(
            new BranchSetup("b-1", "Eins", null, List.of()),
            null,
            new BranchSetup(null, "ohne ID", null, List.of()),
            new BranchSetup("b-2", null, null, List.of()),
            new BranchSetup("b-1", "Eins doppelt", null, List.of())));

        Map<String, String> names = BranchNames.byId(setup);

        assertEquals(Map.of("b-1", "Eins", "b-2", "b-2"), names);
        assertEquals(List.of("b-2", "b-1"), List.copyOf(names.keySet()), "\"b-2\" sorts before \"Eins\"");
    }

    @Test
    @DisplayName("the map is unmodifiable and exceptions of the service reach the caller")
    void isUnmodifiableAndDoesNotSwallowErrors() {
        SetupService setup = mock(SetupService.class);
        when(setup.getAllBranches()).thenReturn(List.of(new BranchSetup("b-1", "Eins", null, List.of())));
        Map<String, String> names = BranchNames.byId(setup);
        assertThrows(UnsupportedOperationException.class, () -> names.put("b-2", "Zwei"));

        SetupService broken = mock(SetupService.class);
        when(broken.getAllBranches()).thenThrow(new IllegalStateException("setup not loaded"));
        assertThrows(IllegalStateException.class, () -> BranchNames.byId(broken));
    }
}
