package com.autohaus.backend.usage.insights;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Applies BR-9/BR-10/BR-19 and folds {@link InsightsHistory} into the display model (REQ-038
 * §10.5) / wendet die Unterdrückungsregeln an und faltet die Historie in das Anzeigemodell.
 * Package-private companion of {@link InsightsViewModel#build}. / Paket-privater Begleiter.
 *
 * <p><b>The single place of suppression (BR-9, §19):</b> every value that leaves this builder is a
 * {@link Cell} whose {@code value}/{@code ratio}/{@code percent} are {@code null} unless the cell
 * is {@code VALUE}; bar widths and heat levels are derived only from visible cells (AC-20).
 * Renderers and JSON never see {@link InsightsHistory}. The work is split into package-private
 * parts: {@link InsightsViewModelPeriods} (G-1, T-1…T-5), {@link InsightsViewModelBranches} (S2,
 * G-2/G-3, I-4), {@link InsightsViewModelFunnel} (S6) — all on top of the one engine
 * {@link InsightsViewModelSuppression}. / Einzige Stelle der Unterdrückung; Aufteilung in
 * paket-private Teile über derselben Engine.
 *
 * <p><b>Periods (BR-10):</b> {@code asOf} = today − 1. Location values only for the closed month
 * (the month before {@code today}) and the running year up to its end; global values for „aktueller
 * Monat bis gestern" and „laufendes Jahr bis gestern" — i.e. the month and year of {@code asOf}
 * (on the 1st the whole previous month). Nothing before a source's collection start is ever
 * counted, missing counters are {@code NO_DATA} („—"), never 0 (BR-19). / Nie vor dem
 * Erfassungsbeginn, fehlende Zähler nie 0.
 *
 * <p><b>Switches:</b> {@code bookingsEnabled == false} → {@code closedMonth}, {@code ranking},
 * {@code belowThreshold}, {@code month}, {@code year}, {@code matrix} and
 * {@code closedMonthOffHoursShare} are {@code null}; {@code funnelEnabled == false} →
 * {@code funnel} is {@code null}; {@code dropoffEnabled} only acts inside an active funnel
 * (AC-35). / Abgeschaltete Quellen sind {@code null}.
 *
 * <p><b>Warnings (§17, §11.5):</b> fully formatted German texts — {@link #WARN_DAY_PENDING} when
 * yesterday is not yet evaluated although it lies on or after a collection start (E3, §5.8),
 * {@link #WARN_STALE} when the latest evaluated day is more than {@value #STALE_AFTER_DAYS} days
 * before today, {@link #WARN_MONTH_PARTIAL} and {@link #WARN_MONTH_INCOMPLETE} (singular
 * {@link #WARN_MONTH_INCOMPLETE_ONE}) for the closed month. {@code WARN_HISTORY_UNAVAILABLE} is
 * appended by the scheduler only. / Fertig formatierte Warnhinweise; „Historie nicht verfügbar"
 * hängt der Scheduler an.
 */
final class InsightsViewModelBuilder {

    /** §17 {@code WARN_DAY_PENDING} — „Auswertung für {date} steht noch aus". */
    static final String WARN_DAY_PENDING = "Auswertung für %s steht noch aus";

    /** §17 {@code WARN_STALE} — „Letzte Auswertung: {date} – Daten möglicherweise veraltet". */
    static final String WARN_STALE = "Letzte Auswertung: %s – Daten möglicherweise veraltet";

    /** §17 {@code WARN_MONTH_PARTIAL} — „Monat unvollständig – Erfassung seit {date}". */
    static final String WARN_MONTH_PARTIAL = "Monat unvollständig – Erfassung seit %s";

    /** §17 {@code WARN_MONTH_INCOMPLETE}, plural — „unvollständig: {n} Tage fehlen" (n ≥ 2). */
    static final String WARN_MONTH_INCOMPLETE = "unvollständig: %d Tage fehlen";

    /** §17 {@code WARN_MONTH_INCOMPLETE}, singular — „unvollständig: 1 Tag fehlt". */
    static final String WARN_MONTH_INCOMPLETE_ONE = "unvollständig: 1 Tag fehlt";

    /** Days after which the latest evaluated day counts as stale (§9 Observability, TC-50). */
    static final int STALE_AFTER_DAYS = 3;

    /** The closed-month templates as patterns (placeholders → any text) / als Muster. */
    private static final List<java.util.regex.Pattern> CLOSED_MONTH_WARNINGS = List.of(
        template(WARN_MONTH_PARTIAL), template(WARN_MONTH_INCOMPLETE), template(WARN_MONTH_INCOMPLETE_ONE));

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.");

    private InsightsViewModelBuilder() {
    }

    /**
     * Whether a warning concerns the closed month — {@link #WARN_MONTH_PARTIAL} or
     * {@link #WARN_MONTH_INCOMPLETE} in either number (§11.5: shown in S2 and with the G-2 matrix
     * only; S4 and S6 show the current month). / Ob ein Hinweis den abgeschlossenen Monat betrifft.
     *
     * @param warning a formatted warning of {@link InsightsViewModel#warnings()}
     * @return {@code true} for the two closed-month warnings / für die zwei Vormonats-Hinweise
     */
    static boolean isClosedMonthWarning(String warning) {
        return warning != null && CLOSED_MONTH_WARNINGS.stream().anyMatch(p -> p.matcher(warning).matches());
    }

    /**
     * {@link #WARN_MONTH_INCOMPLETE} in singular or plural (§17) / Hinweis „unvollständig" in
     * Einzahl oder Mehrzahl.
     *
     * @param missingDays missing days of the compacted month, ≥ 1 / fehlende Tage
     * @return „unvollständig: 1 Tag fehlt" or „unvollständig: {n} Tage fehlen"
     */
    static String monthIncomplete(int missingDays) {
        return missingDays == 1 ? WARN_MONTH_INCOMPLETE_ONE : WARN_MONTH_INCOMPLETE.formatted(missingDays);
    }

    /** Regex of a {@code String.format} template with {@code %s}/{@code %d} placeholders. */
    private static java.util.regex.Pattern template(String format) {
        StringBuilder regex = new StringBuilder();
        java.util.regex.Matcher placeholder = java.util.regex.Pattern.compile("%[sd]").matcher(format);
        int last = 0;
        while (placeholder.find()) {
            regex.append(java.util.regex.Pattern.quote(format.substring(last, placeholder.start()))).append(".+");
            last = placeholder.end();
        }
        regex.append(java.util.regex.Pattern.quote(format.substring(last)));
        return java.util.regex.Pattern.compile(regex.toString());
    }

    static InsightsViewModel build(InsightsHistory history, Map<String, String> branchNames,
                                   LocalDate today, boolean bookingsEnabled,
                                   boolean funnelEnabled, boolean dropoffEnabled) {
        Objects.requireNonNull(today, "today");
        InsightsHistory h = normalize(history);
        Map<String, String> names = branchNames == null ? Map.of() : branchNames;
        Collator collator = Collator.getInstance(Locale.GERMAN);
        LocalDate asOf = today.minusDays(1);

        LocalDate collectingSince = effectiveSince(h.collectingSince(), h.days(), day -> day.bookings() != null);
        LocalDate funnelSince = effectiveSince(h.funnelCollectingSince(), h.days(), day -> day.funnel() != null);
        LocalDate dropoffSince = effectiveSince(h.dropoffCollectingSince(), h.days(),
            day -> day.funnel() != null && day.funnel().dropoff() != null);

        List<String> warnings = new ArrayList<>();
        pendingAndStale(h.days(), asOf, today, bookingsEnabled, collectingSince, funnelEnabled,
            funnelSince, warnings);

        InsightsViewModelBranches.Result closed = InsightsViewModelBranches.Result.none();
        Period month = null;
        Period year = null;
        if (bookingsEnabled) {
            closed = InsightsViewModelBranches.build(h, names, today, collectingSince, collator);
            month = InsightsViewModelPeriods.build(InsightsViewModelPeriods.PERIOD_MONTH,
                asOf.withDayOfMonth(1), asOf, collectingSince, h.days(), collator);
            year = InsightsViewModelPeriods.build(InsightsViewModelPeriods.PERIOD_YEAR,
                asOf.withDayOfYear(1), asOf, collectingSince, h.days(), collator);
            if (closed.partialSince() != null) {
                addOnce(warnings, WARN_MONTH_PARTIAL.formatted(DATE.format(closed.partialSince())));
            }
            if (closed.incomplete()) {
                addOnce(warnings, monthIncomplete(closed.missingDays()));
            }
        }
        FunnelView funnel = funnelEnabled
            ? InsightsViewModelFunnel.build(h.days(), asOf, funnelSince, dropoffEnabled, dropoffSince, collator)
            : null;

        return new InsightsViewModel(
            asOf,
            bookingsEnabled,
            funnelEnabled,
            collectingSince,
            funnelSince,
            bookingsEnabled ? closed.closedMonth() : null,
            bookingsEnabled ? closed.partialSince() : null,
            bookingsEnabled && closed.incomplete(),
            bookingsEnabled ? closed.missingDays() : 0,
            bookingsEnabled ? closed.ranking() : null,
            bookingsEnabled ? closed.belowThreshold() : null,
            month,
            year,
            bookingsEnabled ? closed.matrix() : null,
            bookingsEnabled ? closed.offHoursShare() : null,
            funnel,
            List.copyOf(warnings));
    }

    // ---- warnings / Warnhinweise ----

    private static void pendingAndStale(SortedMap<LocalDate, InsightsDay> days, LocalDate asOf,
                                        LocalDate today, boolean bookingsEnabled, LocalDate since,
                                        boolean funnelEnabled, LocalDate funnelSince,
                                        List<String> warnings) {
        Predicate<InsightsDay> hasBookings = day -> day.bookings() != null;
        Predicate<InsightsDay> hasFunnel = day -> day.funnel() != null;
        boolean pending = (bookingsEnabled && isPending(days, asOf, since, hasBookings))
            || (funnelEnabled && isPending(days, asOf, funnelSince, hasFunnel));
        if (pending) {
            addOnce(warnings, WARN_DAY_PENDING.formatted(DATE.format(asOf)));
        }
        LocalDate stale = null;
        if (bookingsEnabled) {
            stale = older(stale, staleDay(days, today, hasBookings));
        }
        if (funnelEnabled) {
            stale = older(stale, staleDay(days, today, hasFunnel));
        }
        if (stale != null) {
            addOnce(warnings, WARN_STALE.formatted(DATE.format(stale)));
        }
    }

    /** Yesterday lies on/after the collection start but is not evaluated yet (E3, §5.8). */
    private static boolean isPending(SortedMap<LocalDate, InsightsDay> days, LocalDate asOf,
                                     LocalDate since, Predicate<InsightsDay> present) {
        if (since == null || asOf.isBefore(since)) {
            return false;
        }
        InsightsDay day = days.get(asOf);
        return day == null || !present.test(day);
    }

    /** The latest evaluated day when it is more than 3 days old, else {@code null} (TC-50). */
    private static LocalDate staleDay(SortedMap<LocalDate, InsightsDay> days, LocalDate today,
                                      Predicate<InsightsDay> present) {
        LocalDate latest = null;
        for (Map.Entry<LocalDate, InsightsDay> entry : days.entrySet()) {
            if (entry.getValue() != null && present.test(entry.getValue())) {
                latest = entry.getKey();
            }
        }
        if (latest == null || ChronoUnit.DAYS.between(latest, today) <= STALE_AFTER_DAYS) {
            return null;
        }
        return latest;
    }

    private static LocalDate older(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isBefore(b) ? a : b;
    }

    private static void addOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    // ---- shared helpers / gemeinsame Helfer ----

    /**
     * The collection start of a source: the persisted value, or — for a history written without
     * it (seed, old file) — the first day that carries the source; {@code null} = never collected
     * (BR-19) / Erfassungsbeginn einer Quelle.
     */
    static LocalDate effectiveSince(LocalDate persisted, SortedMap<LocalDate, InsightsDay> days,
                                    Predicate<InsightsDay> present) {
        if (persisted != null) {
            return persisted;
        }
        for (Map.Entry<LocalDate, InsightsDay> entry : days.entrySet()) {
            if (entry.getValue() != null && present.test(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Range label of a period, e.g. „01.10.–14.10.2026" or „18.11.2026" for a single day /
     * formatierter Zeitraum.
     */
    static String rangeLabel(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return DATE.format(to);
        }
        if (from.getYear() == to.getYear()) {
            return DAY_MONTH.format(from) + "–" + DATE.format(to);
        }
        return DATE.format(from) + "–" + DATE.format(to);
    }

    /**
     * Tolerant, range-safe copy of a possibly partial history — no {@code null} maps, no range
     * views that could reject a {@code subMap} (a few hundred entries, built a few times a day) /
     * tolerante Kopie ohne null-Maps und ohne Bereichs-Sichten.
     */
    private static InsightsHistory normalize(InsightsHistory history) {
        if (history == null) {
            return InsightsHistory.empty();
        }
        return new InsightsHistory(history.schemaVersion(), history.updatedAt(),
            history.collectingSince(), history.funnelCollectingSince(), history.dropoffCollectingSince(),
            copy(history.days()), copy(history.branchDays()), copy(history.branchMonths()));
    }

    private static <K, V> SortedMap<K, V> copy(SortedMap<K, V> map) {
        TreeMap<K, V> copy = new TreeMap<>();
        if (map != null) {
            map.forEach((key, value) -> {
                if (key != null) {
                    copy.put(key, value);
                }
            });
        }
        return Collections.unmodifiableSortedMap(copy);
    }
}
