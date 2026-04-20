package com.autohaus.backend.jobtypes;

import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/jobtypes")
public class JobTypesController {

    private final JobTypesService jobTypesService;

    public JobTypesController(JobTypesService jobTypesService) {
        this.jobTypesService = jobTypesService;
    }

    @GetMapping
    public List<JobTypeResponseDto> getJobTypes(@PathVariable String branchId) {
        return jobTypesService.getJobTypesByBranch(branchId);
    }
}
