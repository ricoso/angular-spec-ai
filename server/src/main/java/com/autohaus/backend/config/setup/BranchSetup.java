package com.autohaus.backend.config.setup;

import java.util.List;

public record BranchSetup(
    String branchId,
    String name,
    BranchAddressSetup address,
    List<GroupEntry> groups
) {
    /**
     * REQ-025 (i) Iter. 3: Strukturierte Adresse pro Branch.
     * Quelle der Wahrheit für street/zip/city, da SoftNet-API diese Felder nicht liefert.
     */
    public record BranchAddressSetup(
        String street,
        String zip,
        String city,
        String country
    ) {}
}
