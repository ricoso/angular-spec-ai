package com.autohaus.backend.snaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class OperationIdMapper {

    private static final Logger log = LoggerFactory.getLogger(OperationIdMapper.class);
    private static final Pattern HEX_24 = Pattern.compile("^[a-f0-9]{24}$");

    private static final Map<String, String> FRONTEND_TO_SOFTNET = Map.ofEntries(
        Map.entry("inspection", "698b34c24dd8eea4d6d4219e"),
        Map.entry("oil-change", "698b34c24dd8eea4d6d4219a"),
        Map.entry("brake-check", "698b34c24dd8eea4d6d4219b"),
        Map.entry("wiper-blades", "698b34c24dd8eea4d6d4219c"),
        Map.entry("tuv", "698b34c24dd8eea4d6d42199"),
        Map.entry("tire-change", "698b34c24dd8eea4d6d4219f"),
        Map.entry("actions-checks", "698b34c24dd8eea4d6d42197"),
        Map.entry("repair", "698b34c24dd8eea4d6d421a0"),
        Map.entry("brake-fluid", "698b34c24dd8eea4d6d4219b"),
        Map.entry("bodywork", "698b34c24dd8eea4d6d4219d"),
        Map.entry("dialog-acceptance", "698b34c24dd8eea4d6d4219e"),
        Map.entry("maintenance-contract", "698b34c24dd8eea4d6d4219e"),
        Map.entry("emergency-oil", "698b34c24dd8eea4d6d4219a"),
        Map.entry("uvv-inspection", "698b34c24dd8eea4d6d42199"),
        Map.entry("ac-cleaning", "698b34c24dd8eea4d6d4219d"),
        Map.entry("bring-own-tires", "698b34c24dd8eea4d6d4219f"),
        Map.entry("stored-tires", "698b34c24dd8eea4d6d4219f"),
        Map.entry("pickup-delivery", "698b34c24dd8eea4d6d4219f"),
        Map.entry("store-tires", "698b34c24dd8eea4d6d4219f"),
        Map.entry("warranty-check", "698b34c24dd8eea4d6d42198"),
        Map.entry("safety-check", "698b34c24dd8eea4d6d4219e"),
        Map.entry("fluid-check", "698b34c24dd8eea4d6d4219a"),
        Map.entry("battery-check", "698b34c24dd8eea4d6d4219e"),
        Map.entry("diagnostics", "698b34c24dd8eea4d6d421a0"),
        Map.entry("noises", "698b34c24dd8eea4d6d421a0"),
        Map.entry("ac-malfunction", "698b34c24dd8eea4d6d421a0"),
        Map.entry("cost-estimate", "698b34c24dd8eea4d6d4219d"),
        Map.entry("windshield-replacement", "698b34c24dd8eea4d6d4219d")
    );

    public List<String> mapToSoftnetOperations(List<String> frontendIds) {
        Set<String> softnetIds = new LinkedHashSet<>();
        for (String id : frontendIds) {
            if (HEX_24.matcher(id).matches()) {
                softnetIds.add(id);
            } else if (FRONTEND_TO_SOFTNET.containsKey(id)) {
                softnetIds.add(FRONTEND_TO_SOFTNET.get(id));
            } else {
                log.warn("Unknown frontend service ID \"{}\" — skipping", id);
            }
        }
        return new ArrayList<>(softnetIds);
    }
}
