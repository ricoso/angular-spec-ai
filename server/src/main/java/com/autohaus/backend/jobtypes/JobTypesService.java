package com.autohaus.backend.jobtypes;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class JobTypesService {

    private static final String TIRE_CHANGE_NAME_FRAGMENT = "reifen";

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public JobTypesService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<JobTypeResponseDto> getJobTypesByBranch(String branchId) {
        String groupId = setupService.resolveGroupId(branchId, "default");
        List<JsonNode> jobTypes = softnetApi.getJobTypes(groupId);
        List<JsonNode> operations = softnetApi.getOperations(groupId);

        List<JobTypeResponseDto.OperationDto> allOps = operations.stream()
            .map(op -> new JobTypeResponseDto.OperationDto(op.path("id").asText(), op.path("name").asText()))
            .toList();

        boolean tireChangeEnabled = setupService.isTireChangeEnabled();

        return jobTypes.stream()
            .filter(jt -> tireChangeEnabled || !isTireChangeJobType(jt))
            .map(jt -> new JobTypeResponseDto(
                jt.path("id").asText(),
                jt.path("name").asText(),
                jt.path("name").asText(),
                allOps
            ))
            .toList();
    }

    private boolean isTireChangeJobType(JsonNode jobType) {
        String name = jobType.path("name").asText("").toLowerCase(Locale.ROOT);
        return name.contains(TIRE_CHANGE_NAME_FRAGMENT);
    }
}
