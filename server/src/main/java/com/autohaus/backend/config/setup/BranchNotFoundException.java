package com.autohaus.backend.config.setup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BranchNotFoundException extends RuntimeException {
    public BranchNotFoundException(String branchId) {
        super("Branch not found in setup: " + branchId);
    }
}
