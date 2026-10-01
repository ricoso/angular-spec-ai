package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * One row of the S3 matrix (REQ-038 §10.5) / eine Zeile der Servicegruppen-Matrix.
 *
 * @param branchName display name, or the label of the collective row / Anzeigename
 * @param bookings bookings of the branch in the closed month / Buchungen
 * @param cells share cells keyed like {@link GroupMatrix#columns()} / Zellen je Spalte
 * @param otherBranches whether this is the collective row „weitere Standorte" (BR-9 Nr. 3)
 */
public record MatrixRow(String branchName, Cell bookings, Map<String, Cell> cells,
                        boolean otherBranches) {}
