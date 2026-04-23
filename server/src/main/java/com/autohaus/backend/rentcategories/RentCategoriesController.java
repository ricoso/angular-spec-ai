package com.autohaus.backend.rentcategories;

import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/rentcategories")
public class RentCategoriesController {

    private final RentCategoriesService service;

    public RentCategoriesController(RentCategoriesService service) {
        this.service = service;
    }

    @GetMapping
    public List<RentCategoryResponseDto> getRentCategories(@PathVariable String branchId) {
        return service.getRentCategories(branchId);
    }
}
