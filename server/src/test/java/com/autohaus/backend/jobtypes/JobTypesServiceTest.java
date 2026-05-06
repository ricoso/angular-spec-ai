package com.autohaus.backend.jobtypes;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobTypesServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SetupService setupService;
    private final ObjectMapper om = new ObjectMapper();
    private JobTypesService service;

    @BeforeEach
    void setUp() {
        lenient().when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        lenient().when(setupService.isTireChangeEnabled()).thenReturn(true);
        service = new JobTypesService(softnetApi, setupService);
    }

    @Test
    void getJobTypesByBranch_returnsJobTypesWithAllOperations() {
        ObjectNode jt = om.createObjectNode();
        jt.put("id", "jt-1");
        jt.put("name", "Inspektion");

        ObjectNode op1 = om.createObjectNode();
        op1.put("id", "op-1");
        op1.put("name", "Ölwechsel");
        ObjectNode op2 = om.createObjectNode();
        op2.put("id", "op-2");
        op2.put("name", "Bremse");

        when(softnetApi.getJobTypes("mock-group-id")).thenReturn(List.of(jt));
        when(softnetApi.getOperations("mock-group-id")).thenReturn(List.of(op1, op2));

        List<JobTypeResponseDto> result = service.getJobTypesByBranch("any-branch");
        assertEquals(1, result.size());
        assertEquals("jt-1", result.get(0).id());
        assertEquals("Inspektion", result.get(0).name());
        assertEquals("Inspektion", result.get(0).description());
        assertEquals(2, result.get(0).operations().size());
    }

    @Test
    void getJobTypesByBranch_emptyResults() {
        when(softnetApi.getJobTypes("mock-group-id")).thenReturn(List.of());
        when(softnetApi.getOperations("mock-group-id")).thenReturn(List.of());

        List<JobTypeResponseDto> result = service.getJobTypesByBranch("any");
        assertTrue(result.isEmpty());
    }

    /** REQ-025 (b) Iter. 3: Tags werden zu CategoryDto gemappt und nach rank sortiert. */
    @Test
    void getCategoriesByBranch_mapsTagsToCategoryDtoSortedByRank() {
        ObjectNode t1 = om.createObjectNode();
        t1.put("id", "tag-3");
        t1.put("name", "Räderwechsel");
        t1.put("rank", 3);
        ObjectNode t2 = om.createObjectNode();
        t2.put("id", "tag-0");
        t2.put("name", "Inspektion");
        t2.put("rank", 0);

        when(softnetApi.getList("/api/planning/tags", "mock-group-id"))
            .thenReturn(List.of(t1, t2));

        var result = service.getCategoriesByBranch("any-branch");
        assertEquals(2, result.size());
        // Sortiert nach rank ascending
        assertEquals("Inspektion", result.get(0).name());
        assertEquals(0, result.get(0).rank());
        assertEquals("Räderwechsel", result.get(1).name());
        assertEquals(3, result.get(1).rank());
    }

    /** REQ-025 (b) Iter. 3: Operations enthalten description, tagIds, order, imageId. */
    @Test
    void getOperationsByBranch_includesDescriptionAndTagsAndOrder() {
        ObjectNode op1 = om.createObjectNode();
        op1.put("id", "op-1");
        op1.put("name", "Inspektion");
        op1.put("description", "Bitte bringen Sie Ihr Serviceheft mit.");
        op1.put("image", "img-1");
        op1.put("order", 1.1);
        op1.putArray("tags").add("tag-0").add("tag-3");

        ObjectNode op2 = om.createObjectNode();
        op2.put("id", "op-2");
        op2.put("name", "Ölwechsel");
        op2.put("description", "");  // leer → nonBlankOrNull → null
        op2.put("order", 1.2);
        op2.putArray("tags").add("tag-0");

        when(softnetApi.getOperations("mock-group-id")).thenReturn(List.of(op2, op1));  // unsortiert

        var result = service.getOperationsByBranch("any-branch");
        assertEquals(2, result.size());
        // Sortiert nach order ascending
        assertEquals("op-1", result.get(0).id());
        assertEquals("Bitte bringen Sie Ihr Serviceheft mit.", result.get(0).description());
        assertEquals(List.of("tag-0", "tag-3"), result.get(0).tagIds());
        assertEquals(1.1, result.get(0).order());
        assertEquals("img-1", result.get(0).imageId());
        // op-2: leere description → null
        assertNull(result.get(1).description());
    }

    /**
     * Deep-Link-Override: wenn das Frontend die `tireChange`-groupId mitgibt,
     * wird {@link SetupService#validateGroupIdForBranch} verwendet (statt
     * resolveGroupId default), und SoftNet wird mit der Override-Group abgefragt.
     */
    @Test
    void getCategoriesByBranch_usesGroupIdOverrideViaValidation() {
        when(setupService.validateGroupIdForBranch("any-branch", "tirechange-grp"))
            .thenReturn("tirechange-grp");
        when(softnetApi.getList("/api/planning/tags", "tirechange-grp"))
            .thenReturn(List.of());

        var result = service.getCategoriesByBranch("any-branch", "tirechange-grp");
        assertTrue(result.isEmpty());
    }

    @Test
    void getOperationsByBranch_usesGroupIdOverrideViaValidation() {
        when(setupService.validateGroupIdForBranch("any-branch", "tirechange-grp"))
            .thenReturn("tirechange-grp");
        when(softnetApi.getOperations("tirechange-grp"))
            .thenReturn(List.of());

        var result = service.getOperationsByBranch("any-branch", "tirechange-grp");
        assertTrue(result.isEmpty());
    }

    @Test
    void getCategoriesByBranch_blankGroupIdOverrideUsesDefault() {
        when(softnetApi.getList("/api/planning/tags", "mock-group-id"))
            .thenReturn(List.of());

        var result = service.getCategoriesByBranch("any-branch", "");
        assertTrue(result.isEmpty());
    }

    /** REQ-025 (b) Iter. 3: Operations ohne order-Feld landen am Ende. */
    @Test
    void getOperationsByBranch_handlesMissingOrder() {
        ObjectNode op1 = om.createObjectNode();
        op1.put("id", "op-no-order");
        op1.put("name", "Ohne Order");
        op1.putArray("tags");

        when(softnetApi.getOperations("mock-group-id")).thenReturn(List.of(op1));

        var result = service.getOperationsByBranch("any-branch");
        assertEquals(1, result.size());
        assertNull(result.get(0).order());
    }
}
