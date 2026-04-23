package com.autohaus.backend.config.setup;

import com.autohaus.backend.config.FeatureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke-test against the real {@code server/config/setup.json} to verify that the
 * committed branch catalog can actually be loaded and all 27 branches resolve correctly.
 */
class SetupServiceIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path resolveSetupJsonPath() {
        // Test runs from /server/ working dir when invoked via gradle
        Path candidate1 = Path.of("config", "setup.json").toAbsolutePath();
        if (Files.exists(candidate1)) return candidate1;
        Path candidate2 = Path.of("server", "config", "setup.json").toAbsolutePath();
        if (Files.exists(candidate2)) return candidate2;
        Path candidate3 = Path.of("..", "server", "config", "setup.json").toAbsolutePath().normalize();
        return candidate3;
    }

    @Test
    void realSetupJson_loadsWithAllBranches() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "",
            resolveSetupJsonPath().toString()
        );
        svc.init();

        assertEquals(27, svc.getAllBranches().size(), "Expected 27 branches from Names_and_IDs.xlsx");
        svc.getAllBranches().forEach(b -> {
            assertNotNull(b.branchId(), "branchId must be set for " + b.name());
            assertFalse(b.branchId().isBlank());
            assertEquals(2, b.groups().size(),
                "Expected default + tireChange variants for " + b.name());
        });
    }

    @Test
    void realSetupJson_filtersTireChangeWhenDisabled() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(false),
            "",
            resolveSetupJsonPath().toString()
        );
        svc.init();

        svc.getAllBranches().forEach(b -> {
            assertEquals(1, b.groups().size(), "Only default variant expected for " + b.name());
            assertEquals("default", b.groups().get(0).variant());
        });
    }

    @Test
    void realSetupJson_resolvesKnownNeussBranch() {
        SetupService svc = new SetupService(
            objectMapper,
            new FeatureProperties(true),
            "",
            resolveSetupJsonPath().toString()
        );
        svc.init();

        // From Names_and_IDs.xlsx: Audi Zentrum Neuss
        String branchId = "68d25d008d2bb4389eb9d11a";
        assertEquals("68d27df7bc2f5a9f555f8f72", svc.resolveGroupId(branchId, "default"));
        assertEquals("68d27e02794a7b452c5227ad", svc.resolveGroupId(branchId, "tireChange"));
    }
}
