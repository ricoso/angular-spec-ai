package com.autohaus.backend.config.setup;

import com.autohaus.backend.config.FeatureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetupServiceTest {

    private static final String SAMPLE_JSON = """
        {
          "branches": [
            {
              "branchId": "branch-1",
              "name": "Filiale Eins",
              "address": "Musterstr. 1",
              "groups": [
                { "variant": "default",    "groupId": "grp-1-default",    "fullName": "Filiale Eins" },
                { "variant": "tireChange", "groupId": "grp-1-tireChange", "fullName": "Filiale Eins Radwechsel" }
              ]
            },
            {
              "branchId": "branch-2",
              "name": "Filiale Zwei",
              "address": "Musterstr. 2",
              "groups": [
                { "variant": "default", "groupId": "grp-2-default", "fullName": "Filiale Zwei" }
              ]
            }
          ]
        }
        """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SetupService newService(String json, boolean radwechselEnabled) {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(radwechselEnabled),
            json,
            "ignored-because-inline-is-set"
        );
        svc.init();
        return svc;
    }

    @Test
    void init_loadsAllBranchesWhenToggleEnabled() {
        SetupService svc = newService(SAMPLE_JSON, true);
        List<BranchSetup> all = svc.getAllBranches();
        assertEquals(2, all.size());
        assertEquals("Filiale Eins", all.get(0).name());
        assertEquals(2, all.get(0).groups().size());
    }

    @Test
    void init_filtersTireChangeWhenToggleDisabled() {
        SetupService svc = newService(SAMPLE_JSON, false);
        List<BranchSetup> all = svc.getAllBranches();
        assertEquals(2, all.size());
        BranchSetup first = all.stream().filter(b -> b.branchId().equals("branch-1")).findFirst().orElseThrow();
        assertEquals(1, first.groups().size());
        assertEquals("default", first.groups().get(0).variant());
    }

    @Test
    void init_dropsBranchesWithNoRemainingGroupsWhenToggleDisabled() {
        String onlyTireChange = """
            {
              "branches": [
                {
                  "branchId": "only-tire",
                  "name": "Nur Radwechsel",
                  "address": "Str 1",
                  "groups": [
                    { "variant": "tireChange", "groupId": "grp-tc", "fullName": "Nur Radwechsel" }
                  ]
                },
                {
                  "branchId": "mixed",
                  "name": "Gemischt",
                  "address": "Str 2",
                  "groups": [
                    { "variant": "default", "groupId": "grp-d", "fullName": "Gemischt" }
                  ]
                }
              ]
            }
            """;
        SetupService svc = newService(onlyTireChange, false);
        List<BranchSetup> all = svc.getAllBranches();
        assertEquals(1, all.size());
        assertEquals("mixed", all.get(0).branchId());
    }

    @Test
    void resolveGroupId_returnsCorrectGroupForVariant() {
        SetupService svc = newService(SAMPLE_JSON, true);
        assertEquals("grp-1-default", svc.resolveGroupId("branch-1", "default"));
        assertEquals("grp-1-tireChange", svc.resolveGroupId("branch-1", "tireChange"));
    }

    @Test
    void resolveGroupId_defaultsToDefaultVariantWhenNull() {
        SetupService svc = newService(SAMPLE_JSON, true);
        assertEquals("grp-1-default", svc.resolveGroupId("branch-1", null));
        assertEquals("grp-1-default", svc.resolveGroupId("branch-1", ""));
    }

    @Test
    void resolveGroupId_throwsBranchNotFoundForUnknownBranch() {
        SetupService svc = newService(SAMPLE_JSON, true);
        assertThrows(BranchNotFoundException.class, () -> svc.resolveGroupId("does-not-exist", "default"));
    }

    @Test
    void resolveGroupId_fallsBackToDefaultWhenVariantMissing() {
        // branch-2 hat nur 'default', keine 'tireChange'-Gruppe — darf nicht mehr werfen.
        SetupService svc = newService(SAMPLE_JSON, true);
        assertEquals("grp-2-default", svc.resolveGroupId("branch-2", "tireChange"));
    }

    @Test
    void resolveGroupId_fallsBackToDefaultWhenSeasonDisabled() {
        // branch-1 hat beide, aber Saison aus → tireChange wird gefiltert → Fallback auf default.
        SetupService svc = newService(SAMPLE_JSON, false);
        assertEquals("grp-1-default", svc.resolveGroupId("branch-1", "tireChange"));
    }

    @Test
    void resolveGroupId_throwsVariantNotFoundWhenNoDefaultExists() {
        String onlyTireChange = """
            {
              "branches": [
                {
                  "branchId": "only-tire",
                  "name": "Nur Radwechsel",
                  "address": "Str 1",
                  "groups": [
                    { "variant": "tireChange", "groupId": "grp-tc", "fullName": "Nur Radwechsel" }
                  ]
                }
              ]
            }
            """;
        SetupService svc = newService(onlyTireChange, true);
        VariantNotFoundException ex = assertThrows(
            VariantNotFoundException.class,
            () -> svc.resolveGroupId("only-tire", "unknownVariant")
        );
        assertTrue(ex.getMessage().contains("unknownVariant"));
    }

    @Test
    void findByBranchId_returnsMatch() {
        SetupService svc = newService(SAMPLE_JSON, true);
        BranchSetup b = svc.findByBranchId("branch-2");
        assertNotNull(b);
        assertEquals("Filiale Zwei", b.name());
    }

    @Test
    void findByBranchId_throwsForUnknown() {
        SetupService svc = newService(SAMPLE_JSON, true);
        assertThrows(BranchNotFoundException.class, () -> svc.findByBranchId("nope"));
    }

    @Test
    void isTireChangeEnabled_reflectsToggle() {
        assertTrue(newService(SAMPLE_JSON, true).isTireChangeEnabled());
        assertFalse(newService(SAMPLE_JSON, false).isTireChangeEnabled());
    }

    @Test
    void init_throwsWhenNoConfiguration() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "",
            "/tmp/non-existent-setup-does-not-exist.json"
        );
        assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void init_throwsWhenJsonMalformed() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "{ not valid json",
            "ignored"
        );
        assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void init_throwsWhenBranchesMissing() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "{\"otherKey\": []}",
            "ignored"
        );
        assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void init_loadsFromFileWhenInlineEmpty(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("setup.json");
        Files.writeString(file, SAMPLE_JSON);
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "",
            file.toString()
        );
        svc.init();
        assertEquals(2, svc.getAllBranches().size());
    }

    @Test
    void getAllBranches_sortedByNameIgnoreCase() {
        String json = """
            {
              "branches": [
                {
                  "branchId": "b1",
                  "name": "Zebra",
                  "address": "Str",
                  "groups": [{ "variant": "default", "groupId": "g1", "fullName": "Zebra" }]
                },
                {
                  "branchId": "b2",
                  "name": "alpha",
                  "address": "Str",
                  "groups": [{ "variant": "default", "groupId": "g2", "fullName": "alpha" }]
                }
              ]
            }
            """;
        SetupService svc = newService(json, true);
        List<BranchSetup> sorted = svc.getAllBranches();
        assertEquals("alpha", sorted.get(0).name());
        assertEquals("Zebra", sorted.get(1).name());
    }
}
