package com.autohaus.backend.branches;

import com.autohaus.backend.branches.dto.BranchResponseDto;
import com.autohaus.backend.config.SoftnetProperties;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    private final ObjectMapper om = new ObjectMapper();
    private BranchesService service;

    @BeforeEach
    void setUp() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "/fp", 10000, "group-1", "branch-1");
        service = new BranchesService(softnetApi, props);
    }

    @Test
    void getBranches_filtersToGroupBranches() {
        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        ArrayNode branches = group.putArray("branches");
        branches.add("branch-1");
        ArrayNode brands = group.putArray("brand");
        brands.add("BMW");

        ObjectNode branch1 = om.createObjectNode();
        branch1.put("id", "branch-1");
        branch1.put("name", "Filiale Mitte");
        branch1.put("label", "Mitte");

        ObjectNode branch2 = om.createObjectNode();
        branch2.put("id", "branch-other");
        branch2.put("name", "Other");

        when(softnetApi.getGroups()).thenReturn(List.of(group));
        when(softnetApi.getBranches()).thenReturn(List.of(branch1, branch2));

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("branch-1", result.get(0).id());
        assertEquals("Filiale Mitte", result.get(0).name());
        assertEquals("Mitte", result.get(0).description());
        assertEquals(List.of("BMW"), result.get(0).brands());
    }

    @Test
    void getBranches_groupNotFound_returnsEmpty() {
        ObjectNode group = om.createObjectNode();
        group.put("id", "different-group");

        when(softnetApi.getGroups()).thenReturn(List.of(group));
        when(softnetApi.getBranches()).thenReturn(List.of());

        List<BranchResponseDto> result = service.getBranches();
        assertTrue(result.isEmpty());
    }

    @Test
    void getBranch_returnsBranch() {
        ObjectNode branch = om.createObjectNode();
        branch.put("id", "b-1");
        branch.put("name", "Test Branch");
        branch.put("label", "Test");

        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("Audi");

        when(softnetApi.getBranch("b-1")).thenReturn(branch);
        when(softnetApi.getGroups()).thenReturn(List.of(group));

        BranchResponseDto result = service.getBranch("b-1");
        assertEquals("b-1", result.id());
        assertEquals("Test Branch", result.name());
    }

    @Test
    void getBranches_handlesObjectBranchIds() {
        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        ArrayNode branches = group.putArray("branches");
        ObjectNode branchRef = om.createObjectNode();
        branchRef.put("id", "branch-1");
        branchRef.put("name", "Test");
        branches.add(branchRef);
        group.putArray("brand").add("BMW");

        ObjectNode branch1 = om.createObjectNode();
        branch1.put("id", "branch-1");
        branch1.put("name", "Test");
        branch1.put("label", "");
        branch1.put("company", "Test GmbH");

        when(softnetApi.getGroups()).thenReturn(List.of(group));
        when(softnetApi.getBranches()).thenReturn(List.of(branch1));

        List<BranchResponseDto> result = service.getBranches();
        assertEquals(1, result.size());
        assertEquals("Test GmbH", result.get(0).description());
    }
}
