package com.autohaus.backend.usage.insights;

import java.util.List;

/**
 * The S3 matrix location × service group for the closed month (REQ-038 §10.5, G-2/G-3) / die
 * Matrix Standort × Servicegruppe des Vormonats.
 *
 * @param columns column keys in order: the {@link ServiceGroup} keys plus {@code "mobility"}
 * @param rows one row per branch with ≥ 20 bookings plus the collective row „weitere Standorte"
 */
public record GroupMatrix(List<String> columns, List<MatrixRow> rows) {}
