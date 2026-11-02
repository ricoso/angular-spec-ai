package com.autohaus.backend.branches;

import com.autohaus.backend.branches.dto.BranchResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchesController {

    private final BranchesService branchesService;

    public BranchesController(BranchesService branchesService) {
        this.branchesService = branchesService;
    }

    @GetMapping
    public List<BranchResponseDto> getBranches() {
        return branchesService.getBranches();
    }

    @GetMapping("/{branchId}")
    public BranchResponseDto getBranch(@PathVariable String branchId) {
        return branchesService.getBranch(branchId);
    }
}
