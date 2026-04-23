package com.autohaus.backend.rentcategories;

import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rentcategories")
public class RentCategoriesController {

    private final RentCategoriesService service;

    public RentCategoriesController(RentCategoriesService service) {
        this.service = service;
    }

    @GetMapping
    public List<RentCategoryResponseDto> getRentCategories() {
        return service.getRentCategories();
    }
}
