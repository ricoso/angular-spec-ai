package com.autohaus.backend.jobtypes;

import com.autohaus.backend.jobtypes.dto.CategoryDto;
import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import com.autohaus.backend.jobtypes.dto.OperationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches/{branchId}")
public class JobTypesController {

    private final JobTypesService jobTypesService;

    public JobTypesController(JobTypesService jobTypesService) {
        this.jobTypesService = jobTypesService;
    }

    @GetMapping("/jobtypes")
    public List<JobTypeResponseDto> getJobTypes(@PathVariable String branchId) {
        return jobTypesService.getJobTypesByBranch(branchId);
    }

    /**
     * REQ-025 (b) Iter. 3: Service-Kategorien (= Tags aus SoftNet) sortiert nach `rank`.
     */
    @GetMapping("/categories")
    public List<CategoryDto> getCategories(@PathVariable String branchId) {
        return jobTypesService.getCategoriesByBranch(branchId);
    }

    /**
     * REQ-025 (b) Iter. 3: Operations (= Services) inkl. description, tagIds, order, imageId.
     */
    @GetMapping("/operations")
    public List<OperationDto> getOperations(@PathVariable String branchId) {
        return jobTypesService.getOperationsByBranch(branchId);
    }
}
