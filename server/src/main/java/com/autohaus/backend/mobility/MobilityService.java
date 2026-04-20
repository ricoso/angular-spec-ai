package com.autohaus.backend.mobility;

import com.autohaus.backend.mobility.dto.MobilityAlternativeResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MobilityService {

    private final SoftnetApiClient softnetApi;

    public MobilityService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public List<MobilityAlternativeResponseDto> getMobilityAlternatives(String branchId) {
        List<JsonNode> alternatives = softnetApi.getMobilityAlternatives();
        return alternatives.stream()
            .map(a -> new MobilityAlternativeResponseDto(a.path("id").asText(), a.path("name").asText()))
            .toList();
    }
}
