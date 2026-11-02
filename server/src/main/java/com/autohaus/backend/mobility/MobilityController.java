package com.autohaus.backend.mobility;

import com.autohaus.backend.mobility.dto.MobilityAlternativeResponseDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/mobility-alternatives")
public class MobilityController {

    private final MobilityService mobilityService;

    public MobilityController(MobilityService mobilityService) {
        this.mobilityService = mobilityService;
    }

    @GetMapping
    public List<MobilityAlternativeResponseDto> getMobilityAlternatives(@PathVariable String branchId) {
        return mobilityService.getMobilityAlternatives(branchId);
    }
}
