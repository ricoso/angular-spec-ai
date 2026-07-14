package com.autohaus.backend.reporting;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ReportModel} into a standalone HTML page (REQ-030). Served behind Basic
 * Auth. All dynamic values (including customer-data payloads) are HTML-escaped. No external
 * resources — inline CSS only.
 */
@Component
public class ReportHtmlGenerator {

    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'Uhr'").withZone(ZoneId.of("Europe/Berlin"));

    public String generate(ReportModel model) {
        AiErrorReportSummary summary = model.summary() != null ? model.summary() : AiErrorReportSummary.unavailable();
        String generated = model.generatedAt() != null ? TS.format(model.generatedAt()) : "—";

        StringBuilder h = new StringBuilder(8192);
        h.append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        h.append("<title>Fehler-Report</title>").append(css()).append("</head><body>");

        h.append("<header class=\"hd\"><h1>Fehler-Report</h1>")
            .append("<p class=\"meta\">Stand: ").append(esc(generated))
            .append(" &middot; Auswertungszeitraum: letzte 24 Stunden</p></header>");

        // AI summary
        h.append("<section class=\"ai\"><h2>KI-Zusammenfassung</h2>");
        h.append("<p class=\"headline\">").append(esc(summary.headline())).append("</p>");
        h.append("<p>").append(esc(summary.overallAssessment())).append("</p>");
        if (summary.topIssues() != null && !summary.topIssues().isEmpty()) {
            h.append("<h3>Priorisierte Probleme</h3><ul class=\"issues\">");
            for (AiErrorReportSummary.PrioritizedIssue i : summary.topIssues()) {
                h.append("<li><span class=\"sev sev-").append(esc(sevClass(i.severity()))).append("\">")
                    .append(esc(i.severity())).append("</span> <b>").append(esc(i.title())).append("</b>")
                    .append("<div class=\"hint\">Marken: ").append(esc(i.affectedBrands()))
                    .append(" &middot; Standorte: ").append(esc(i.affectedLocations())).append("</div>")
                    .append("<div class=\"hint\">Ursache: ").append(esc(i.rootCauseHypothesis())).append("</div></li>");
            }
            h.append("</ul>");
        }
        if (summary.recommendations() != null && !summary.recommendations().isEmpty()) {
            h.append("<h3>Empfehlungen</h3><ul>");
            for (String r : summary.recommendations()) {
                h.append("<li>").append(esc(r)).append("</li>");
            }
            h.append("</ul>");
        }
        h.append("</section>");

        // Category status overview (Iteration 2): traffic-light tiles per funnel stage + dimension
        h.append("<section class=\"ov\"><h2>Status-Übersicht</h2><div class=\"cats\">");
        h.append(catRate("Terminsuchen", "/snaps", model.snaps()));
        h.append(catRate("Buchungen", "/appointments", model.appointments()));
        h.append(catCount("Marken", "mit Fehlern", model.errorsByBrand()));
        h.append(catCount("Standorte", "mit Fehlern", model.errorsByLocation()));
        h.append("</div></section>");

        // Error list with CSS-only client-side filter (Iteration 3).
        h.append(errorSection(model.errors()));

        h.append("<footer class=\"ft\">Automatisch generiert &middot; REQ-030 &middot; gs-terminbuchungs-app</footer>");
        h.append("</body></html>");
        return h.toString();
    }

    /**
     * Error list with a CSS-only client-side filter (Iteration 3): hidden radios + sibling
     * selectors let the viewer filter the cards by endpoint (Terminsuchen / Buchungen) and by
     * error type — each button shows its count. No JavaScript, so it also works under a strict CSP.
     */
    private static String errorSection(List<ErrorEntry> errors) {
        StringBuilder h = new StringBuilder(4096);
        h.append("<section class=\"err\"><h2>Fehler (").append(errors.size())
            .append(") <span class=\"pii-note\">— Payloads PII-maskiert</span></h2>");
        if (errors.isEmpty()) {
            h.append("<p class=\"empty\">Keine Fehler im Auswertungszeitraum.</p></section>");
            return h.toString();
        }

        // Count by endpoint for the filter buttons (filter by endpoint only).
        Map<String, Long> epCounts = new LinkedHashMap<>();
        for (ErrorEntry e : errors) {
            String ep = epSlug(e.endpoint());
            epCounts.put(ep, epCounts.getOrDefault(ep, 0L) + 1L);
        }

        // Ordered filter list: {id, label, value}. "Alle" first, then endpoints.
        List<String[]> filters = new ArrayList<>();
        filters.add(new String[]{"flt-all", "Alle (" + errors.size() + ")", ""});
        for (String ep : List.of("snaps", "appointments", "other")) {
            Long c = epCounts.get(ep);
            if (c != null) {
                filters.add(new String[]{"flt-ep-" + ep, epLabel(ep) + " (" + c + ")", ep});
            }
        }

        // Hidden radios — must precede .filters and .err-list for the sibling selectors to reach them.
        for (String[] f : filters) {
            h.append("<input type=\"radio\" name=\"errflt\" class=\"flt-radio\" id=\"").append(f[0]).append("\"")
                .append("flt-all".equals(f[0]) ? " checked" : "").append(">");
        }
        // Dynamic rules: hide non-matching cards + highlight the active label.
        h.append("<style>");
        for (String[] f : filters) {
            if (!f[2].isEmpty()) {
                h.append("#").append(f[0]).append(":checked~.err-list .card:not([data-ep=")
                    .append(f[2]).append("]){display:none}");
            }
            h.append("#").append(f[0]).append(":checked~.filters label[for=").append(f[0])
                .append("]{background:var(--accent);color:#fff;border-color:var(--accent)}");
        }
        h.append("</style>");
        // Filter bar.
        h.append("<div class=\"filters\">");
        for (String[] f : filters) {
            h.append("<label for=\"").append(f[0]).append("\">").append(esc(f[1])).append("</label>");
        }
        h.append("</div>");
        // Cards.
        h.append("<div class=\"err-list\">");
        for (ErrorEntry e : errors) {
            h.append("<article class=\"card\" data-ep=\"").append(epSlug(e.endpoint())).append("\">");
            h.append("<div class=\"row\"><span class=\"k\">Marke</span><span>").append(esc(orDash(e.brand()))).append("</span>")
                .append("<span class=\"k\">Standort</span><span>").append(esc(orDash(e.location()))).append("</span></div>");
            h.append("<div class=\"row\"><span class=\"k\">Services</span><span>")
                .append(esc(e.operations() == null || e.operations().isEmpty() ? "—" : String.join(", ", e.operations())))
                .append("</span><span class=\"k\">Fehlertyp</span><span class=\"etype\">").append(esc(orDash(e.errorType()))).append("</span></div>");
            h.append("<div class=\"row\"><span class=\"k\">Trace-ID</span><span class=\"mono\">").append(esc(orDash(e.traceId())))
                .append("</span><span class=\"k\">Endpoint</span><span class=\"mono\">").append(esc(orDash(e.endpoint()))).append("</span></div>");
            h.append("<details><summary>Gesendet (Request)</summary><pre>").append(esc(orDash(e.requestBody()))).append("</pre></details>");
            h.append("<details><summary>Empfangen (Response)</summary><pre>").append(esc(orDash(e.responseBody()))).append("</pre></details>");
            h.append("</article>");
        }
        h.append("</div></section>");
        return h.toString();
    }

    private static String epLabel(String slug) {
        return switch (slug) {
            case "snaps" -> "Terminsuchen";
            case "appointments" -> "Buchungen";
            default -> "Sonstige";
        };
    }

    private static String epSlug(String endpoint) {
        if (endpoint == null) {
            return "other";
        }
        if (endpoint.endsWith("/snaps")) {
            return "snaps";
        }
        if (endpoint.endsWith("/appointments")) {
            return "appointments";
        }
        return "other";
    }

    /** Traffic-light tile for a funnel category (snaps / appointments): started · ok · failed. */
    private static String catRate(String name, String endpoint, BookingStats s) {
        String status = lightByRate(s.started(), s.failed());
        return "<div class=\"cat cat-" + status + "\">"
            + "<div class=\"cat-hd\"><span class=\"dot\"></span><span class=\"cat-name\">" + esc(name)
            + "</span><span class=\"cat-ep\">" + esc(endpoint) + "</span></div>"
            + "<div class=\"cat-val\">" + s.started() + " gestartet &middot; " + s.succeeded()
            + " ok &middot; <b>" + s.failed() + "</b> Fehler</div></div>";
    }

    /** Traffic-light tile for a dimension (brands / locations): count affected + top list. */
    private static String catCount(String name, String unit, Map<String, Long> byKey) {
        int affected = byKey.size();
        StringBuilder c = new StringBuilder();
        c.append("<div class=\"cat cat-").append(lightByCount(affected)).append("\">");
        c.append("<div class=\"cat-hd\"><span class=\"dot\"></span><span class=\"cat-name\">")
            .append(esc(name)).append("</span></div>");
        c.append("<div class=\"cat-val\"><b>").append(affected).append("</b> ").append(esc(unit)).append("</div>");
        if (!byKey.isEmpty()) {
            c.append("<ul class=\"toplist\">");
            int shown = 0;
            for (Map.Entry<String, Long> e : byKey.entrySet()) {
                if (shown++ >= 5) {
                    break;
                }
                c.append("<li><span>").append(esc(orDash(e.getKey()))).append("</span><b>")
                    .append(e.getValue()).append("</b></li>");
            }
            c.append("</ul>");
        }
        c.append("</div>");
        return c.toString();
    }

    /** Green 0 %, yellow < 5 %, red >= 5 % failure rate; grey when there were no requests. */
    private static String lightByRate(long started, long failed) {
        if (started <= 0) {
            return "neutral";
        }
        if (failed <= 0) {
            return "ok";
        }
        return (double) failed / started < 0.05 ? "warn" : "bad";
    }

    /** Green 0, yellow 1, red >= 2 affected values (signals how widely the errors spread). */
    private static String lightByCount(int affected) {
        if (affected <= 0) {
            return "ok";
        }
        return affected == 1 ? "warn" : "bad";
    }

    private static String sevClass(String severity) {
        String v = severity == null ? "" : severity.toLowerCase(java.util.Locale.ROOT);
        if (v.contains("critical") || v.contains("high")) return "high";
        if (v.contains("medium")) return "med";
        return "low";
    }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String css() {
        return """
            <style>
            :root{--ink:#1a2b3c;--soft:#5a6b7b;--line:#dbe3ea;--bg:#eef2f6;--surface:#fff;
            --accent:#0e6b7c;--ok:#2c7a4e;--bad:#b83a2c;--warn:#a16a12;--mono:ui-monospace,Menlo,Consolas,monospace;}
            *{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;
            color:var(--ink);background:var(--bg);line-height:1.5;padding:1.5rem;max-width:60rem;margin:0 auto}
            h1{font-size:1.6rem;margin:0}h2{font-size:1.1rem;border-bottom:2px solid var(--line);padding-bottom:.3rem}
            h3{font-size:.95rem;margin:1rem 0 .4rem}.meta{color:var(--soft);font-size:.85rem;margin:.3rem 0 0}
            section{background:var(--surface);border:1px solid var(--line);border-radius:6px;padding:1.1rem 1.3rem;margin:1rem 0}
            .ai{border-left:4px solid var(--accent)}.headline{font-weight:650;font-size:1.05rem}
            .issues{list-style:none;padding:0}.issues li{border:1px solid var(--line);border-radius:5px;padding:.6rem .8rem;margin:.4rem 0}
            .hint{color:var(--soft);font-size:.85rem;margin-top:.2rem}
            .sev{display:inline-block;font-size:.72rem;font-weight:700;text-transform:uppercase;padding:.1rem .45rem;border-radius:3px;margin-right:.4rem}
            .sev-high{background:#fae7e4;color:var(--bad)}.sev-med{background:#f7efd9;color:var(--warn)}.sev-low{background:#e7f2ec;color:var(--ok)}
            .cats{display:grid;grid-template-columns:repeat(2,1fr);gap:.8rem}
            .cat{border:1px solid var(--line);border-left-width:4px;border-radius:6px;padding:.7rem .9rem}
            .cat-ok{border-left-color:var(--ok)}.cat-warn{border-left-color:var(--warn)}.cat-bad{border-left-color:var(--bad)}.cat-neutral{border-left-color:var(--soft)}
            .cat-hd{display:flex;align-items:center;gap:.45rem;font-weight:650}
            .dot{width:.7rem;height:.7rem;border-radius:50%;flex:none;background:var(--soft)}
            .cat-ok .dot{background:var(--ok)}.cat-warn .dot{background:var(--warn)}.cat-bad .dot{background:var(--bad)}
            .cat-ep{color:var(--soft);font-size:.76rem;font-family:var(--mono);margin-left:auto}
            .cat-val{margin-top:.35rem;font-size:.92rem;font-variant-numeric:tabular-nums}
            .toplist{list-style:none;padding:0;margin:.45rem 0 0}.toplist li{display:flex;justify-content:space-between;font-size:.82rem;padding:.14rem 0;border-top:1px solid var(--line)}
            .pii-note{font-weight:400;font-size:.78rem;color:var(--soft)}
            .flt-radio{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none}
            .filters{display:flex;flex-wrap:wrap;gap:.4rem;margin:.3rem 0 .9rem}
            .filters label{cursor:pointer;font-size:.8rem;padding:.3rem .75rem;border:1px solid var(--line);border-radius:999px;background:var(--surface);color:var(--soft);user-select:none}
            .filters label:hover{border-color:var(--accent);color:var(--accent)}
            .card{border:1px solid var(--line);border-radius:6px;padding:.8rem 1rem;margin:.7rem 0}
            .row{display:grid;grid-template-columns:auto 1fr auto 1fr;gap:.3rem .7rem;font-size:.9rem;margin-bottom:.4rem}
            .k{color:var(--soft);font-size:.75rem;text-transform:uppercase;letter-spacing:.04em;align-self:center}
            .etype{font-weight:600;color:var(--bad)}.mono{font-family:var(--mono);font-size:.82rem;word-break:break-all}
            details{margin-top:.4rem}summary{cursor:pointer;color:var(--accent);font-size:.85rem}
            pre{background:#0e161b;color:#c9d6dd;font-family:var(--mono);font-size:.78rem;padding:.7rem;border-radius:5px;overflow-x:auto;white-space:pre-wrap;word-break:break-all}
            .empty{color:var(--soft)}.ft{color:var(--soft);font-size:.78rem;text-align:center;margin-top:1.5rem}
            @media(max-width:40em){.row{grid-template-columns:auto 1fr}.cats{grid-template-columns:1fr}}
            </style>
            """;
    }
}
