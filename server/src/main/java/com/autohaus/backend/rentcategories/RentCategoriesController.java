package com.autohaus.backend.rentcategories;

import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/branches/{branchId}/rentcategories")
public class RentCategoriesController {

    private static final Set<String> KNOWN_VARIANTS = Set.of("tireChange", "recall");

    private final RentCategoriesService service;

    public RentCategoriesController(RentCategoriesService service) {
        this.service = service;
    }

    @GetMapping
    public List<RentCategoryResponseDto> getRentCategories(@PathVariable String branchId,
                                                           @RequestParam(required = false) String variant) {
        // REQ-039 BR-M7: only the known variants reach the setup lookup (and its WARN log);
        // anything else is the default group, as SetupService would fall back to anyway.
        // REQ-041: "recall" included — snaps and booking resolve rental against the recall group,
        // so the offer shown to the customer must come from that group too.
        String safeVariant = variant != null && KNOWN_VARIANTS.contains(variant) ? variant : "default";
        return service.getRentCategories(branchId, safeVariant);
    }
}
