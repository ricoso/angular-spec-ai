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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobTypesServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SetupService setupService;
    private final ObjectMapper om = new ObjectMapper();
    private JobTypesService service;

    @BeforeEach
    void setUp() {
        when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        when(setupService.isTireChangeEnabled()).thenReturn(true);
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
}
