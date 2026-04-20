package com.autohaus.backend.jobtypes;

import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTypesService {

    private final SoftnetApiClient softnetApi;

    public JobTypesService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public List<JobTypeResponseDto> getJobTypesByBranch(String branchId) {
        List<JsonNode> jobTypes = softnetApi.getJobTypes();
        List<JsonNode> operations = softnetApi.getOperations();

        List<JobTypeResponseDto.OperationDto> allOps = operations.stream()
            .map(op -> new JobTypeResponseDto.OperationDto(op.path("id").asText(), op.path("name").asText()))
            .toList();

        return jobTypes.stream()
            .map(jt -> new JobTypeResponseDto(
                jt.path("id").asText(),
                jt.path("name").asText(),
                jt.path("name").asText(),
                allOps
            ))
            .toList();
    }
}
