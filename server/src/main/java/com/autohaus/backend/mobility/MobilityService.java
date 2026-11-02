package com.autohaus.backend.mobility;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.mobility.dto.MobilityAlternativeResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MobilityService {

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public MobilityService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<MobilityAlternativeResponseDto> getMobilityAlternatives(String branchId) {
        String groupId = setupService.resolveGroupId(branchId, "default");
        List<JsonNode> alternatives = softnetApi.getMobilityAlternatives(groupId);
        return alternatives.stream()
            .map(a -> new MobilityAlternativeResponseDto(a.path("id").asText(), a.path("name").asText()))
            .toList();
    }
}
