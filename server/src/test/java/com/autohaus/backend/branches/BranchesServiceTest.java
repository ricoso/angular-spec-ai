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

    private BranchSetup buildSetupBranch(String id, String name, String street, String zip, String city) {
        return new BranchSetup(
            id, name,
            new BranchSetup.BranchAddressSetup(street, zip, city, "DE"),
            List.of(new GroupEntry("default", "group-1", "Group 1"))
        );
    }

    @Test
    void getBranches_returnsBranchesFromSetup() {
        BranchSetup setupBranch = buildSetupBranch("branch-1", "Filiale Mitte", "Musterstr. 1", "12345", "Berlin");

        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("BMW");

        when(setupService.getAllBranches()).thenReturn(List.of(setupBranch));
        when(softnetApi.getGroups()).thenReturn(List.of(group));

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("branch-1", result.get(0).id());
        assertEquals("Filiale Mitte", result.get(0).name());
        assertEquals("Musterstr. 1, 12345 Berlin", result.get(0).description());
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
        BranchSetup setupBranch = buildSetupBranch("b-1", "Test Branch", "Hauptstr. 5", "40231", "Düsseldorf");

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
            "branch-1", "Test",
            new BranchSetup.BranchAddressSetup("Hauptstr. 12", "40213", "Düsseldorf", "DE"),
            List.of(new GroupEntry("default", "unknown-group", "Group"))
        );

        when(setupService.getAllBranches()).thenReturn(List.of(setupBranch));
        when(softnetApi.getGroups()).thenReturn(List.of());

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("Hauptstr. 12, 40213 Düsseldorf", result.get(0).description());
        assertTrue(result.get(0).brands().isEmpty());
    }

    /** REQ-025 (i) Iter. 3: PLZ kommt aus setup.json und ist Teil der composed description. */
    @Test
    void getBranches_includesZipFromSetupJson() {
        BranchSetup branch = buildSetupBranch("b-mettmann", "Audi Mettmann",
            "Düsseldorfer Str. 189-191", "40822", "Mettmann");

        when(setupService.getAllBranches()).thenReturn(List.of(branch));
        when(softnetApi.getGroups()).thenReturn(List.of());

        BranchResponseDto dto = service.getBranches().get(0);
        assertNotNull(dto.address());
        assertEquals("Düsseldorfer Str. 189-191", dto.address().street());
        assertEquals("40822", dto.address().zip());
        assertEquals("Mettmann", dto.address().city());
        assertEquals("DE", dto.address().country());
        assertEquals("Düsseldorfer Str. 189-191, 40822 Mettmann", dto.description());
    }

    /** REQ-025 (i) Iter. 3: leere zip → description ohne PLZ. */
    @Test
    void getBranches_handlesEmptyZip() {
        BranchSetup branch = buildSetupBranch("b-x", "Test", "Straße 1", "", "Stadt");

        when(setupService.getAllBranches()).thenReturn(List.of(branch));
        when(softnetApi.getGroups()).thenReturn(List.of());

        BranchResponseDto dto = service.getBranches().get(0);
        assertEquals("Straße 1, Stadt", dto.description());
    }
}
