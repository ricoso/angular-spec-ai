package com.autohaus.backend.config.setup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class VariantNotFoundException extends RuntimeException {
    public VariantNotFoundException(String branchId, String variant, List<String> availableVariants) {
        super("Variant '" + variant + "' not supported for branch '" + branchId
            + "'. Available variants: " + availableVariants);
    }
}
