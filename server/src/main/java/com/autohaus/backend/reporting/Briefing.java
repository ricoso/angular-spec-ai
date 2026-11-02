package com.autohaus.backend.reporting;

import java.util.List;

/**
 * S0 „Auf einen Blick: Handlungsimpulse" (REQ-038 BR-21, §10.5) / die Handlungsimpulse. An empty
 * list renders „Noch keine Hinweise – zu wenig Daten." (AC-18). / Leere Liste → Leertext.
 *
 * @param impulses at most one impulse per key, in the order I-1, I-2, I-3, I-4
 */
public record Briefing(List<Impulse> impulses) {

    /** No impulse at all / kein Impuls. */
    public static Briefing empty() {
        return new Briefing(List.of());
    }
}
