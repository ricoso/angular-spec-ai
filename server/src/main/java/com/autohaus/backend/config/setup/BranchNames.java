package com.autohaus.backend.config.setup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Display names of the {@code setup.json} branches keyed by branchId (REQ-038 §14.1) /
 * Anzeigenamen der Standorte aus {@code setup.json}, nach branchId.
 *
 * <p>The one source for the view models of the statistics tab (controller, insights scheduler,
 * capacity scan, E2E seed loader), which previously each had their own {@code branchNames()} copy.
 * A static helper instead of a new {@link SetupService} method, so {@link SetupService} stays
 * unchanged and every test stub of {@link SetupService#getAllBranches()} keeps working. / Statischer
 * Helfer statt neuer {@code SetupService}-Methode: {@code SetupService} bleibt unverändert.
 */
public final class BranchNames {

    /**
     * Name like {@link SetupService#getAllBranches()} (case-insensitive), then branchId — equal names
     * no longer depend on the hash order of the setup map / bei gleichem Namen entscheidet die
     * branchId.
     */
    private static final Comparator<Map.Entry<String, String>> ORDER =
        Map.Entry.<String, String>comparingByValue(String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Map.Entry.comparingByKey());

    private BranchNames() {
    }

    /**
     * branchId → display name in a stable order: alphabetical by name (as
     * {@link SetupService#getAllBranches()}), equal names by branchId / Anzeigenamen in stabiler
     * Reihenfolge: nach Name, bei Gleichstand nach branchId.
     *
     * <p>{@code null} service → empty map; entries without branchId are skipped; a missing name falls
     * back to the branchId; a repeated branchId keeps its first entry. Exceptions of the service are
     * not caught — each caller keeps its own error handling. / Fehlender Name → branchId; Ausnahmen
     * behandelt der Aufrufer.
     *
     * @param setupService the setup, may be {@code null} / darf {@code null} sein
     * @return an unmodifiable, ordered map / unveränderliche, geordnete Map
     */
    public static Map<String, String> byId(SetupService setupService) {
        if (setupService == null) {
            return Map.of();
        }
        Map<String, String> byId = new LinkedHashMap<>();
        for (BranchSetup branch : setupService.getAllBranches()) {
            if (branch != null && branch.branchId() != null) {
                String name = branch.name() == null ? branch.branchId() : branch.name();
                byId.putIfAbsent(branch.branchId(), name);
            }
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(byId.entrySet());
        entries.sort(ORDER);
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            names.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(names);
    }
}
