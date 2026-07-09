package com.autohaus.backend.reporting;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
        BookingStats s = model.stats();
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

        // Booking overview
        long total = Math.max(s.started(), 1);
        h.append("<section class=\"ov\"><h2>Buchungen (Terminabschlüsse)</h2><div class=\"stats\">");
        h.append(stat("Gestartet", s.started(), "neutral"));
        h.append(stat("Erfolgreich", s.succeeded(), "ok"));
        h.append(stat("Fehlgeschlagen", s.failed(), "bad"));
        h.append("</div><div class=\"bar\" role=\"img\" aria-label=\"")
            .append(s.succeeded()).append(" erfolgreich, ").append(s.failed()).append(" fehlgeschlagen\">")
            .append("<span class=\"b-ok\" style=\"width:").append(pct(s.succeeded(), total)).append("%\"></span>")
            .append("<span class=\"b-bad\" style=\"width:").append(pct(s.failed(), total)).append("%\"></span>")
            .append("</div></section>");

        // Error list
        List<ErrorEntry> errors = model.errors();
        h.append("<section class=\"err\"><h2>Fehler (").append(errors.size()).append(")</h2>");
        if (errors.isEmpty()) {
            h.append("<p class=\"empty\">Keine Fehler im Auswertungszeitraum.</p>");
        } else {
            for (ErrorEntry e : errors) {
                h.append("<article class=\"card\">");
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
        }
        h.append("</section>");

        h.append("<footer class=\"ft\">Automatisch generiert &middot; REQ-030 &middot; gs-terminbuchungs-app</footer>");
        h.append("</body></html>");
        return h.toString();
    }

    private static String stat(String label, long value, String cls) {
        return "<div class=\"stat stat-" + cls + "\"><span class=\"num\">" + value
            + "</span><span class=\"lbl\">" + esc(label) + "</span></div>";
    }

    private static String sevClass(String severity) {
        String v = severity == null ? "" : severity.toLowerCase(java.util.Locale.ROOT);
        if (v.contains("critical") || v.contains("high")) return "high";
        if (v.contains("medium")) return "med";
        return "low";
    }

    private static long pct(long part, long total) {
        return Math.round(100.0 * part / total);
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
            .stats{display:flex;gap:1rem;flex-wrap:wrap}.stat{flex:1;min-width:7rem;text-align:center;border:1px solid var(--line);border-radius:6px;padding:.8rem}
            .num{display:block;font-size:1.8rem;font-weight:700;font-variant-numeric:tabular-nums}.lbl{color:var(--soft);font-size:.8rem;text-transform:uppercase;letter-spacing:.05em}
            .stat-ok .num{color:var(--ok)}.stat-bad .num{color:var(--bad)}
            .bar{display:flex;height:.9rem;border-radius:4px;overflow:hidden;margin-top:.9rem;background:var(--line)}
            .b-ok{background:var(--ok)}.b-bad{background:var(--bad)}
            .card{border:1px solid var(--line);border-radius:6px;padding:.8rem 1rem;margin:.7rem 0}
            .row{display:grid;grid-template-columns:auto 1fr auto 1fr;gap:.3rem .7rem;font-size:.9rem;margin-bottom:.4rem}
            .k{color:var(--soft);font-size:.75rem;text-transform:uppercase;letter-spacing:.04em;align-self:center}
            .etype{font-weight:600;color:var(--bad)}.mono{font-family:var(--mono);font-size:.82rem;word-break:break-all}
            details{margin-top:.4rem}summary{cursor:pointer;color:var(--accent);font-size:.85rem}
            pre{background:#0e161b;color:#c9d6dd;font-family:var(--mono);font-size:.78rem;padding:.7rem;border-radius:5px;overflow-x:auto;white-space:pre-wrap;word-break:break-all}
            .empty{color:var(--soft)}.ft{color:var(--soft);font-size:.78rem;text-align:center;margin-top:1.5rem}
            @media(max-width:40em){.row{grid-template-columns:auto 1fr}}
            </style>
            """;
    }
}
