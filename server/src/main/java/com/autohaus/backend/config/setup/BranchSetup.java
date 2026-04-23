package com.autohaus.backend.config.setup;

import java.util.List;

public record BranchSetup(
    String branchId,
    String name,
    String address,
    List<GroupEntry> groups
) {}
