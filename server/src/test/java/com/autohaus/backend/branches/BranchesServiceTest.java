package com.autohaus.backend.branches;

import com.autohaus.backend.branches.dto.BranchResponseDto;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchesServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SetupService setupService;
    private final ObjectMapper om = new ObjectMapper();
    private BranchesService service;

    @BeforeEach
    void setUp() {
        service = new BranchesService(softnetApi, setupService);
    }

    @Test
    void getBranches_returnsBranchesFromSetup() {
        BranchSetup setupBranch = new BranchSetup(
            "branch-1",
            "Filiale Mitte",
            "Mitte",
            List.of(new GroupEntry("default", "group-1", "Group 1"))
        );

        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("BMW");

        when(setupService.getAllBranches()).thenReturn(List.of(setupBranch));
        when(softnetApi.getGroups()).thenReturn(List.of(group));

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("branch-1", result.get(0).id());
        assertEquals("Filiale Mitte", result.get(0).name());
        assertEquals("Mitte", result.get(0).description());
        assertEquals(List.of("BMW"), result.get(0).brands());
    }

    @Test
    void getBranches_emptySetup_returnsEmpty() {
        when(setupService.getAllBranches()).thenReturn(List.of());

        List<BranchResponseDto> result = service.getBranches();
        assertTrue(result.isEmpty());
    }

    @Test
    void getBranch_returnsBranch() {
        BranchSetup setupBranch = new BranchSetup(
            "b-1",
            "Test Branch",
            "Test",
            List.of(new GroupEntry("default", "group-1", "Group 1"))
        );

        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("Audi");

        when(setupService.findByBranchId("b-1")).thenReturn(setupBranch);
        when(softnetApi.getGroups()).thenReturn(List.of(group));

        BranchResponseDto result = service.getBranch("b-1");
        assertEquals("b-1", result.id());
        assertEquals("Test Branch", result.name());
    }

    @Test
    void getBranches_groupNotFound_returnsBranchWithEmptyBrands() {
        BranchSetup setupBranch = new BranchSetup(
            "branch-1",
            "Test",
            "Test GmbH",
            List.of(new GroupEntry("default", "unknown-group", "Group"))
        );

        when(setupService.getAllBranches()).thenReturn(List.of(setupBranch));
        when(softnetApi.getGroups()).thenReturn(List.of());

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("Test GmbH", result.get(0).description());
        assertTrue(result.get(0).brands().isEmpty());
    }
}
