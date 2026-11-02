package com.autohaus.backend.config.setup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class GroupIdNotInBranchException extends RuntimeException {
    public GroupIdNotInBranchException(String branchId, String groupId) {
        super("Group-ID '" + groupId + "' is not configured for branch '" + branchId + "'.");
    }
}
