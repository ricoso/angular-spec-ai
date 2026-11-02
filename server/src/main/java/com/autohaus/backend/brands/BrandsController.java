package com.autohaus.backend.brands;

import com.autohaus.backend.brands.dto.BrandResponseDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/brands")
public class BrandsController {

    private final BrandsService brandsService;

    public BrandsController(BrandsService brandsService) {
        this.brandsService = brandsService;
    }

    @GetMapping
    public List<BrandResponseDto> getBrands(@PathVariable String branchId) {
        return brandsService.getBrandsByBranch(branchId);
    }
}
