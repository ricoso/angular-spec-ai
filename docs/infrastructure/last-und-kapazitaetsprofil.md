# Last- und Kapazitätsprofil — Online-Terminvereinbarung (OTV)

| | |
|---|---|
| **Stand** | 30.10.2026 |
| **Messfenster** | 30.09. – 30.10.2026 (30 Tage, Produktion) |
| **Messobjekt** | Azure Container App `gs-terminbuchungs-app`, Revision `0000056` (1 Instanz, 0,5 vCPU / 1 GiB) |
| **Quellen** | Log Analytics (Konsolen-Logs der App), Azure Monitor (Container-, JVM- und Ingress-Metriken), Konfiguration der Container App |
| **Adressat** | Plattform- und Betriebsteam einer möglichen anderen Zielumgebung |

Dieses Dokument liefert die Grundlage, um die Anwendung in einer anderen Umgebung zu dimensionieren und zu betreiben: gemessene Last, Ressourcenverbrauch, Log-Volumen und die daraus folgenden Anforderungen. Messwerte stehen in den Abschnitten 3 bis 5. Empfehlungen und Planungswerte sind als solche gekennzeichnet.

**Schreibweise.** Alle Zeiten in Berlin-Ortszeit. 1 KB = 1.024 Byte, 1 MB = 1.024 KB, 1 GB = 1.024 MB. *Zugriff* = ein HTTP-Request an die App (API, Datei oder Health-Check). *API-Aufruf* = Zugriff auf `/api/…`. *Dateiabruf* = Auslieferung der Angular-Anwendung samt Bildern. *Seitenaufruf* = Abruf der Datei `styles-*.css` (genau einmal je geladener Seite). *Sitzung* = ein anonymer Browser-Besuch, der die API nutzt (Header `X-Session-Id`). Es werden weder Personen noch IP-Adressen erfasst.

---

## 1. Kurzfassung

1. **Eine Instanz trägt die gesamte Last.** 0,5 vCPU und 1 GiB reichen: CPU-Spitze 0,19 vCPU (38 % des Limits), Arbeitsspeicher bis 755 MiB (74 %). In Produktion ist **kein Autoscaling aktiv**: min = max = 1 Replikat, keine Skalierungsregel.
2. **Die Last kommt in kurzen Spitzen.** Typisch sind 5 Zugriffe pro Minute (Median der Minuten mit Zugriffen), die Spitze liegt bei **3.151 Zugriffen pro Minute** (237 pro Sekunde). Davon sind höchstens **488 pro Minute** API-Aufrufe (38 pro Sekunde).
3. **84 % aller Zugriffe sind Dateiabrufe**, weil die Anwendung ihre Dateien ohne Cache und ohne Kompression ausliefert. Die Spitzen bestehen überwiegend aus Seitenaufrufen ohne anschließende Nutzung (vermutlich automatisierte Abrufe) und aus Wellen neuer Kunden (bis 153 neue Sitzungen pro Minute).
4. **Horizontales Skalieren ist ohne Zusatzmaßnahmen nicht möglich.** Der Terminsuche-Cache und das Rate-Limit liegen im Arbeitsspeicher der einzelnen Instanz (Abschnitt 2.3).
5. **Logs:** rund 6 MB pro Tag im Normalbetrieb, rund 29 MB pro Tag seit dem 21.10., Spitzentag **66 MB** (302.000 Zeilen), Spitzenminute **711 KB** (3.178 Zeilen). Mindestens 30 Tage Aufbewahrung sind erforderlich (Abschnitt 5).

### Planungswerte

Vorschlag: das Doppelte der Messspitze, weil sich eine größere Aktion nicht ausschließen lässt. Die Messspitzen stammen aus einem Monat und von einer einzelnen Instanz.

| Kennzahl | Messspitze | Planungswert (2×) |
|---|---|---|
| Zugriffe pro Minute, gesamt | 3.151 (Ingress-Zählung: 3.333) | 6.700 |
| Zugriffe pro Sekunde, gesamt | 237 | 500 |
| API-Aufrufe pro Minute | 488 | 1.000 |
| Neue Sitzungen pro Minute | 153 | 300 |
| Gleichzeitige SoftNet-Anfragen | 28 | 60 |
| Ausgehender Datenverkehr, stärkste Minute | 214 MB (≈ 30 Mbit/s) | ≈ 60 Mbit/s |
| Log-Text pro Tag | 66 MB | 130 MB |

---

## 2. System

### 2.1 Aufbau

| Eigenschaft | Wert |
|---|---|
| Laufzeit | Spring Boot 4.1.1 auf Java 25. Image `ubi9/openjdk-25-runtime`, läuft als Nicht-Root (UID 1001), Port 3000 |
| Auslieferung | Ein Container, ein Prozess: Angular-Anwendung (statische Dateien aus `/app/dist/terminbuchungsapp/browser`, 74 Dateien, 1,7 MB roh / 0,8 MB gzip), REST-API `/api/v1/**` und interne Seiten `/internal/**` (Basic Auth: Nutzungsstatistik, Fehlerbericht) |
| Einbettung | Läuft als iframe auf `www.gottfried-schultz.de` (`frame-ancestors`; CORS-Liste per Umgebungsvariable) |
| Sitzung | Signiertes Token (HMAC-SHA256, 20 min), kein serverseitiger Sitzungsspeicher. `SESSION_SECRET` muss bei mehreren Instanzen identisch sein |
| Persistenz | Keine Datenbank, keine lokalen Dateien außer der Konfiguration. Fachdaten ausschließlich über die SoftNet-API. Einziger externer Speicher: Azure Blob (Tageswerte der Statistik, eine kleine Zeile je Tag, 24 Monate) |
| Konfiguration | Umgebungsvariablen und Secrets. `config/setup.json` (20 KB, Standorte und Services) als Secret `SETUP_JSON` oder als Datei |

### 2.2 Abhängigkeiten (ausgehend)

| Ziel | Zweck | Pflicht | Hinweis |
|---|---|---|---|
| `api.soft-nrg.com:443` | Standorte, Termine, Buchung (SoftNet) | ja | Timeout 50 s. Spitze ≈ 320 Aufrufe pro Minute, höchstens 28 gleichzeitig |
| `auth.soft-nrg.com:443` | OAuth-Token (zwei Zugangsdaten: Standard und Rückrufaktion) | ja | Token werden zwischengespeichert |
| `gateway.lhind.ai:443` | Tägliche KI-Zusammenfassung des Fehlerberichts (03:00, Modell `azure/gpt-5-mini`) | nein | Ohne `LLM_API_KEY` deaktiviert |
| `api.loganalytics.io:443`, `gsterminbuchungusage.blob.core.windows.net:443` | Nutzungsstatistik, Fehlerbericht und Kapazitätsscan: Abfragen auf die Logs, Ablage der Tageswerte | nein | Anmeldung per Azure Managed Identity (`IDENTITY_ENDPOINT`). Ohne sie zeigen die internen Seiten „keine Daten"; die Buchungsstrecke ist nicht betroffen |

### 2.3 Zustand im Arbeitsspeicher

Dieser Zustand ist der Grund, warum die Anwendung nicht frei horizontal skaliert.

| Komponente | Inhalt | Folge |
|---|---|---|
| Terminsuche-Cache (`SnapCache`) | Die zuletzt gefundenen Terminvorschläge, 30 min gültig. Die Buchung liest den gewählten Vorschlag aus diesem Cache | Nach einem Neustart oder bei einer anderen Instanz scheitert die Buchung mit HTTP 404 („Snap … not found in cache"). Im Messfenster: 651 von 7.832 Buchungsaufrufen (8 %) mit 404, unter anderem durch Neustarts und den Ablauf der 30 min |
| Rate-Limit (`RateLimitFilter`) | Zähler je Client-IP: 500 Anfragen pro 15 min auf `/api/**` (ohne Health). Die Zuordnung wird nie bereinigt (ein Eintrag je IP) | Gilt je Instanz. Nutzt `getRemoteAddr()`: ohne korrekt weitergereichte Client-IP teilen sich alle Nutzer ein einziges Limit |
| Buchungs-Puffer der Statistik (`UsageEventLogger`) | Anonymes Tagesbündel der Buchungsmerkmale. Ausgabe um 23:59 und beim Herunterfahren | Ein Absturz oder zu kurzes Beenden verliert den laufenden Tag |
| Caches (Marken, Operationen, Services, SoftNet-Token) | kurzlebig | unkritisch |

### 2.4 Betriebsverhalten

| Thema | Verhalten |
|---|---|
| Start | 5,8 s (Median), höchstens 6,9 s (Spring-Startzeit, 18 Starts in 30 Tagen) |
| Beenden | Spring-Standard „graceful" mit 30 s Wartezeit (nicht überschrieben). Eine Buchung kann bis zu 50 s dauern |
| Health | `GET /api/v1/health` ruft bei **jedem** Aufruf SoftNet an (Ping) und antwortet immer mit 200 (`healthy` oder `degraded`). Kein Actuator, kein Metrik-Endpunkt. Extern geprüft: 5 Standorte alle 5 min, das sind 1.440 Aufrufe pro Tag. In der Container App sind keine Probes konfiguriert (Plattform-Standard) |
| Geplante Jobs | Laufen in jeder Instanz, Zeitzone Europe/Berlin: alle 15 min Statistik-Aktualisierung (Log-Abfrage), 03:00 KI-Fehlerbericht (je Instanz), 03:30 und 03:45 Tages-Rollup, 22:30 Kapazitätsscan gegen SoftNet (bis 20 min), 23:59 Puffer-Ausgabe, kleine Tagesjobs um 00:05. Rollup und Scan sind idempotent bzw. einmal pro Tag abgesichert |

---

## 3. Lastprofil (gemessen)

### 3.1 Tagesmengen

*Quelle: Access-Log (Logger `HTTP`). Zugriffe enthalten rund 1.440 Health-Checks pro Tag.*

| Zeitraum | Zugriffe pro Tag | davon API | davon Dateien | Sitzungen pro Tag |
|---|---|---|---|---|
| Werktage 01.–20.10. (14 Tage) | Ø 24.462 (16.626–37.493) | Ø 5.342 (3.323–8.256) | Ø 17.671 | Ø 723 (503–1.061) |
| Wochenenden 01.–20.10. (6 Tage) | Ø 8.244 | Ø 1.567 | Ø 5.235 | Ø 220 |
| 28. und 29.10. (aktuelles Niveau) | Ø 80.045 | Ø 17.308 | Ø 61.272 | Ø 2.002 |

Seit dem 21.10. liegt die Last dauerhaft höher: Sitzungen und API-Aufrufe haben sich etwa verdreifacht. Mögliche Treiber sind die Reifenwechsel-Saison und die Rückrufaktion; die Daten erlauben keine eindeutige Zuordnung. Tage mit den höchsten Werten:

| Tag | Zugriffe | API | Dateien | Seitenaufrufe | Sitzungen | Ausgehend (GB) |
|---|---|---|---|---|---|---|
| Mi 21.10. | 79.678 | 8.319 | 69.913 | 10.401 | 1.060 | 2,7 |
| Do 22.10. | 108.596 | 10.170 | 96.982 | 15.678 | 1.488 | 3,9 |
| Fr 23.10. | 200.999 | 7.730 | 191.821 | 36.770 | 1.002 | 8,4 |
| Sa 24.10. | 77.233 | 3.199 | 72.593 | 13.797 | 479 | 3,2 |
| So 25.10. | 30.615 | 2.485 | 26.690 | 4.419 | 311 | 1,2 |
| **Mo 26.10.** | **275.508** | **18.495** | **255.555** | **44.915** | **2.628** | **10,9** |
| Di 27.10. | 102.459 | 14.912 | 86.086 | 9.308 | 1.990 | 2,9 |
| Mi 28.10. | 78.639 | 16.916 | 60.269 | 2.793 | 1.986 | 1,5 |
| Do 29.10. | 81.451 | 17.699 | 62.274 | 2.709 | 2.017 | 1,5 |

### 3.2 Spitzen

*Median und 99. Perzentil gelten für Zeitfenster mit mindestens einem Zugriff. Die Zeiten sind Berlin-Ortszeit.*

| Kennzahl | Median | 99. Perzentil | Spitze | Zeitpunkt der Spitze |
|---|---|---|---|---|
| Zugriffe pro Minute, gesamt | 5 | 316 | **3.151** (Ingress: 3.333) | 26.10., 17:25 Uhr (Ingress: 09:16 Uhr) |
| API-Aufrufe pro Minute | 2 | 41 | **488** | 26.10., 12:20 Uhr |
| Zugriffe pro Sekunde, gesamt | 2 | 64 | **237** | — |
| API-Aufrufe pro Sekunde | 1 | 4 | **38** | — |
| Terminsuche (`POST /snaps`) pro Minute | — | 17 | 62 | 30.10., 10:17 Uhr |
| Buchung (`POST /appointments`) pro Minute | — | 5 | 53 | 16.10., 08:52 Uhr |
| Übrige SoftNet-Aufrufe pro Minute (Standorte, Kataloge) | — | 29 | 313 | 26.10., 12:20 Uhr |
| Neue Sitzungen pro Minute | — | — | 153 | 26.10., 12:20 Uhr (weitere Wellen: 80 und 71 pro Minute um 09:17 und 09:18 Uhr) |
| Aktive Sitzungen je 5 min | — | — | 263 | 26.10., 12:20 Uhr |
| Gleichzeitig laufende SoftNet-Anfragen | — | 5 (99,9 %: 8) | **28** | 26.10., 09:17 Uhr |

### 3.3 Tagesverlauf

*Mittel über alle Werktage (Mo–Fr) des Messfensters, einschließlich der Tage mit erhöhter Last. Zugriffe pro Stunde ohne Health-Checks.*

| Uhrzeit | Zugriffe pro Stunde (Ø) | API-Aufrufe pro Stunde (Ø) | API-Aufrufe pro Stunde (Spitze) |
|---|---|---|---|
| 06 | 375 | 83 | 349 |
| 07 | 1.495 | 236 | 532 |
| 08 | 3.700 | 552 | 1.225 |
| 09 | 5.863 | 865 | 2.178 |
| 10 | 5.711 | 863 | 2.672 |
| 11 | 4.332 | 810 | 2.338 |
| 12 | 6.585 | 883 | 4.104 |
| 13 | 6.996 | 738 | 2.144 |
| 14 | 3.281 | 712 | 1.795 |
| 15 | 3.662 | 606 | 1.434 |
| 16 | 3.756 | 556 | 1.500 |
| 17 | 3.848 | 392 | 1.019 |
| 18 | 4.204 | 238 | 946 |
| 19 | 2.051 | 187 | 418 |
| 20–05 | 44–769 | 9–169 | höchstens 440 |

Die Nutzung liegt zwischen 07 und 19 Uhr, der Schwerpunkt zwischen 09 und 13 Uhr. Nachts (20–06 Uhr) bleiben die API-Aufrufe unter 200 pro Stunde.

### 3.4 Woraus die Last besteht

- **84 % der Zugriffe sind Dateiabrufe** (1,20 Mio. von 1,43 Mio. im Oktober), **13 % API-Aufrufe**, rund 3 % Health-Checks.
- **Keine Zwischenspeicherung, keine Kompression.** Die App liefert alle Dateien mit `Cache-Control: no-store` und ohne Kompression aus, auch die Bundles mit Hash im Namen (geprüft an der Produktion am 30.10.). Jeder Seitenaufruf lädt alles neu: zunächst 12 Dateien mit 596 KB (komprimiert wären es 163 KB), im Mittel 19 bis 23 Dateien je Seitenaufruf. Ausgehender Verkehr: im Normalbetrieb ≈ 24 KB je Zugriff.
- **Seitenaufrufe ohne Nutzung.** Vom 21. bis 27.10. gab es 4,7- bis 36,7-mal so viele Seitenaufrufe wie Sitzungen (26.10.: 44.915 Seitenaufrufe, 2.628 Sitzungen). Davor und seit dem 28.10. liegt das Verhältnis bei etwa 1,3 bis 1,4. In der stärksten Minute (26.10., 17:25 Uhr) waren es rund 600 Seitenaufrufe bei nur 76 API-Aufrufen, fast nur Desktop, immer dieselben 14 JavaScript-Dateien. Das Muster passt zu automatisierten Abrufen, etwa einer Link-Prüfung nach einem Massenversand. Die Ursache ist **nicht belegt**: IP-Adressen werden nicht protokolliert.
- **Wellen echter Kunden.** Am 26.10. um 12:20 Uhr starteten 153 neue Sitzungen in einer Minute. Das erzeugte 488 API-Aufrufe in dieser Minute (169 Token-Aufrufe, 313 Standort- und Katalog-Aufrufe an SoftNet).
- **Rate-Limit-Treffer.** Am 26.10. wurden 988 Anfragen mit HTTP 429 abgewiesen, in drei Wellen (09:18–09:19, 12:21–12:24 und 12:37–12:40 Uhr), fast alle beim Sitzungsstart (`session/token`, `branches`). Die betroffenen Nutzer kamen nicht in die Anwendung. Die Verteilung der 429 auf einzelne Endpunkte spricht für ein Limit je Client-IP, nicht für ein gemeinsames Limit; belegt ist das nicht.
- **Die App wird durch die Spitzen nicht langsam.** Dateiabrufe benötigen auch in der Spitze durchschnittlich 0,4 ms Serverzeit; die CPU lag bei 3.100 Zugriffen pro Minute unter 0,15 vCPU. Kosten verursachen die Spitzen vor allem im Datenverkehr und im Log.

### 3.5 Antwortzeiten und Fehler

*Quelle: Access-Log, 30 Tage. Die Antwortzeit wird von SoftNet bestimmt. Die Maxima von rund 30 s und rund 50 s entsprechen den SoftNet-Timeouts (aktuell 50 s, angehoben am 28.10.; vorher kürzer).*

| Endpunkt | Anzahl | p50 | p95 | p99 | Max | HTTP 5xx |
|---|---|---|---|---|---|---|
| `POST /api/v1/snaps` (Terminsuche) | 46.835 | 2,6 s | 6,3 s | 9,4 s | 50,1 s | 161 |
| `POST /api/v1/appointments` (Buchung) | 7.832 | 2,4 s | 20,1 s | 30,1 s | 50,2 s | 329 |
| `GET /api/v1/branches` | 19.992 | 0,41 s | 0,60 s | 0,99 s | 30,0 s | 50 |
| `GET /api/v1/branches/{id}` | 19.184 | 0,43 s | 0,60 s | 0,84 s | 30,0 s | 26 |
| `GET /api/v1/branches/{id}/operations` | 26.010 | 0,18 s | 0,29 s | 0,44 s | 30,0 s | 7 |
| `GET /api/v1/branches/{id}/categories` | 21.440 | 0,21 s | 0,33 s | 0,44 s | 30,0 s | 7 |
| `GET /api/v1/branches/{id}/rentcategories` | 25.473 | 0,18 s | 0,30 s | 0,41 s | 30,1 s | 3 |
| `GET /api/v1/session/token` | 31.145 | < 1 ms | 1 ms | 2 ms | 0,7 s | 0 (688 × HTTP 429) |
| Dateiabrufe | 1,20 Mio. | Ø 0,4 ms | | | | |

Ingress insgesamt (Azure-Metrik, 30 Tage): 1.477.351 × 2xx, 3.734 × 4xx, 576 × 5xx, 2.284 × „0xx" (Verbindung ohne Antwort).

---

## 4. Ressourcenverbrauch (1 Instanz, 0,5 vCPU / 1 GiB)

*Quelle: Azure Monitor, 01.–30.10., stündliche Aggregate; Minutenwerte für den 26.10.*

| Größe | Limit | Spitze | Typisch |
|---|---|---|---|
| CPU | 0,5 vCPU | 0,19 vCPU (14.10.); 0,145 vCPU in der Spitzenlast am 26.10. | Ø 0,004 vCPU |
| Arbeitsspeicher (Working Set) | 1 GiB | 755 MiB | Ø 716 MiB; unmittelbar nach dem Start ≈ 440 MiB |
| JVM-Heap | 248 MiB (Standard: 25 % des Limits, `JAVA_OPTS` ist leer) | 245 MiB belegt | Ø 146 MiB |
| JVM Non-Heap | — | 149 MiB | Ø 134 MiB |
| Threads | Tomcat: höchstens 200 | 71 | Median 45 |
| Ausgehender Datenverkehr | — | Minute: 214 MB (≈ 30 Mbit/s); Stunde: 3,9 GB (≈ 9,4 Mbit/s); Tag: 10,9 GB | Ø 1,5 GB pro Tag; 46,2 GB in 30 Tagen |
| Eingehender Datenverkehr | — | Minute: 24 MB; Tag: 1,1 GB | |
| Neustarts | — | 0 Neustarts des Containers (kein Absturz, kein Speichermangel). 18 geplante Starts durch Deployments; beim Revisionswechsel laufen kurz 2 Replikate | |

- Die **CPU ist nie der Engpass.** Auch bei 3.100 Zugriffen pro Minute lag sie unter 30 % des Limits.
- Der **Arbeitsspeicher** erreicht bis zu 74 % des Limits. Die Heap-Reserve ist gering (245 von 248 MiB belegt).
- Die **Grenzen der Instanz** liegen nicht bei CPU oder Threads, sondern bei SoftNet (Antwortzeiten, Timeout) und bei den Zuständen im Arbeitsspeicher.

---

## 5. Logs

### 5.1 Format und Quelle

- Die App schreibt **nur nach stdout** (keine Dateien). Muster: `%d{ISO8601} [%thread] %-5level %logger{36} - %X{traceId:-system}: %msg%n`.
- **Access-Zeile** (Logger `HTTP`, rund 236 Byte): `correlationId`, `sessionId`, `method`, `url`, `statusCode`, `duration`, `device` (anonyme Geräteklasse). Eine Zeile je Zugriff, auch für Dateien und Health-Checks.
- **Fehlerzeilen** (`SOFTNET_ERROR`) enthalten Request- und Response-Body und sind bis rund 5,7 KB lang. Stacktraces stehen als Folgezeilen.
- Es gibt ein Spring-Profil `json` (JSON-Zeilen). Die Statistik-Abfragen erwarten aber das Textformat (Anker `INFO  HTTP`); das Profil darf deshalb nicht aktiviert werden.

### 5.2 Volumen

*Text = Größe der Zeilen in stdout. „Abgerechnet" = Größe in Log Analytics inklusive Metadaten je Zeile (Faktor 2,8).*

| Zeitraum | Zeilen pro Tag | Text (MB pro Tag) | Abgerechnet (MB pro Tag) |
|---|---|---|---|
| 01.–20.10. (Normalbetrieb), Ø | 28.785 | 6,2 | 17,4 |
| 21.–29.10. (erhöhtes Niveau), Ø | 133.911 | 29,2 | 80,5 |
| 28./29.10. (ruhigere Tage), Ø | 110.840 | 24,0 | 65,8 |
| **Spitzentag 26.10.** | **301.996** | **65,9** | **181** |

Normalbetrieb: Werktage 5 bis 12 MB, Wochenenden rund 3 MB. Gesamt im Messfenster: 1,87 Mio. Zeilen, 406 MB Text, 1,1 GB abgerechnet.

| Pro Minute | Zeilen | KB Text |
|---|---|---|
| Median (Minuten mit Zeilen) | — | 2,4 |
| 95. Perzentil | — | 33,9 |
| 99. Perzentil | 393 | 89,7 |
| **Spitze** (26.10., 17:25 Uhr) | **3.178** | **711** (abgerechnet: 1.958) |

Bei der Spitze von 237 Zugriffen pro Sekunde entstehen rund 56 KB Log-Text pro Sekunde.

### 5.3 Zusammensetzung (30 Tage, Anteil an den Bytes)

| Anteil | Zeilen | Quelle |
|---|---|---|
| 82,7 % | 1,49 Mio. (Ø 236 Byte) | Access-Log (`HTTP`) |
| 9,1 % | 225.000 | DEBUG `SoftnetApiClient` |
| 4,1 % | 95.000 | DEBUG `SnapsService` |
| 0,8 % | 6.300 | INFO `AppointmentsService` |
| 0,6 % | 2.400 (Ø 1,1 KB) | ERROR `SoftnetApiClient` |
| 2,7 % | — | Übrige |

Etwa 70 % aller Log-Bytes sind Access-Zeilen von Dateiabrufen; DEBUG-Zeilen der App-Pakete machen zusammen rund 15 % aus.

### 5.4 Speicherbedarf

- **Aufbewahrung: mindestens 30 Tage.** Die Statistik rechnet bis zu 29 Tage zurück nach, und der tägliche Fehlerbericht liest die Logs der letzten Tage.
- **Planwert:** 130 MB Rohtext pro Tag (doppelter Spitzentag), das sind rund **4 GB je 30 Tage**. Bei Indexierung wie in Log Analytics (Faktor 2,8) rund **11 GB**.
- **Hochrechnung aus dem aktuellen Niveau:** 29 MB × 30 Tage ≈ 0,9 GB Rohtext.

---

## 6. Anforderungen an die Zielumgebung

**MUSS** = ohne das läuft die Anwendung fehlerhaft. **SOLL** = wesentlich für Stabilität oder Kosten. **KANN** = sinnvoll, nicht zwingend. Die Begründungen verweisen auf die Abschnitte oben.

### A. Betrieb und Skalierung

| Nr. | Stufe | Anforderung | Begründung |
|---|---|---|---|
| A1 | MUSS | Genau **eine aktive Instanz**. Mehr Instanzen (Hochverfügbarkeit) nur mit Affinität über den Header `X-Session-Id` (kein Cookie: die App läuft als iframe auf einer fremden Domain) oder nach Auslagerung von Terminsuche-Cache und Rate-Limit in einen gemeinsamen Speicher (nicht umgesetzt) | 2.3 |
| A2 | MUSS | Mindestens 0,5 vCPU und **1 GiB** Arbeitsspeicher. 512 MiB reichen nicht: Der Heap-Bedarf liegt bei bis zu 245 MiB (Standard-Heap bei 512 MiB: nur 128 MiB), dazu kommen rund 150 MiB Non-Heap | 4: Working Set 440–755 MiB |
| A3 | SOLL | Limit 1 vCPU und 1,5 GiB, dazu `JAVA_OPTS=-XX:MaxRAMPercentage=40` (≈ 600 MiB Heap). Heute ist `JAVA_OPTS` leer, das Heap-Maximum beträgt 248 MiB bei einer Spitze von 245 MiB | 4 |
| A4 | SOLL | Wartezeit beim Beenden ≥ 60 s und `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=60s`, damit laufende Buchungen (bis 50 s) abgeschlossen werden | 2.4 |
| A5 | SOLL | Deployments außerhalb der Geschäftszeiten (Werktage 07–19 Uhr), am besten zwischen 05 und 06 Uhr: Die Last ist dann minimal (Ø 22 API-Aufrufe pro Stunde), und der Tagespuffer der Statistik ist noch fast leer. Abends ab 20 Uhr ist die Last ebenfalls gering, aber ein Verlust des Puffers träfe einen ganzen Tag. Jeder Neustart verwirft den Terminsuche-Cache; Kunden in der Buchung müssen neu suchen | 2.3, 3.3 |
| A6 | MUSS | Startup-Probe großzügig (≥ 30 s; die App startet in 6–7 s). Liveness per TCP auf Port 3000 oder `GET /`. **Nicht** `/api/v1/health` im Sekundentakt: jeder Aufruf löst einen SoftNet-Ping aus | 2.4 |

### B. Netz und Edge

| Nr. | Stufe | Anforderung | Begründung |
|---|---|---|---|
| B1 | MUSS | Request-Timeout von Ingress oder Load Balancer ≥ 60 s. Bei kürzeren Timeouts wird die Verbindung getrennt, SoftNet legt den Termin aber trotzdem an (Doppelbuchungen) | 3.5: Buchung p99 30 s, Max 50,2 s |
| B2 | MUSS | **Client-IP durchreichen:** Der Proxy setzt `X-Forwarded-For`, und die App wertet ihn aus (`SERVER_FORWARD_HEADERS_STRATEGY=native` oder `framework`). Sonst teilen sich alle Nutzer ein Limit von 500 Anfragen pro 15 min. Abnahmetest: 600 Aufrufe von zwei Quell-IPs, nur die erste erhält HTTP 429 | 2.3 |
| B3 | SOLL | Statische Dateien am Edge **cachen und komprimieren** (gzip oder Brotli). Dateien mit Hash im Namen (`main-*.js`, `chunk-*.js`, `polyfills-*.js`, `styles-*.css`) sind unbegrenzt cachebar, `index.html` nicht. Heute: `no-store` und unkomprimiert | 3.4: 84 % der Zugriffe, ≈ 70 % der Log-Bytes; initial 596 → 163 KB |
| B4 | SOLL | Schutz vor automatisierten Abrufen (Bot- und Ratenregeln am Edge für Dateiabrufe) | 3.4: ≈ 600 Seitenaufrufe pro Minute bei 76 API-Aufrufen |
| B5 | KANN | Rate-Limit anpassen (`APP_RATE_LIMIT_CAPACITY`, `APP_RATE_LIMIT_REFILL_MINUTES`). Bei gemeinsamen Ausgangs-IPs (Mobilfunk-Gateways, Firmennetze) wirkt das Limit auf viele Kunden gleichzeitig | 3.4: 988 × HTTP 429 am 26.10. |
| B6 | MUSS | Ausgehende Freigaben: `api.soft-nrg.com:443`, `auth.soft-nrg.com:443`. Optional: `gateway.lhind.ai:443`, `api.loganalytics.io:443`, `gsterminbuchungusage.blob.core.windows.net:443` | 2.2 |
| B7 | MUSS | `APP_FRAME_ANCESTORS` und `CORS_ALLOWED_ORIGINS` auf die Domains der Zielumgebung setzen. HSTS setzt die App selbst | 2.1 |

### C. Logs und Monitoring

| Nr. | Stufe | Anforderung | Begründung |
|---|---|---|---|
| C1 | MUSS | stdout einsammeln und **mindestens 30 Tage** aufbewahren. Einplanen: 130 MB Rohtext pro Tag (≈ 4 GB je 30 Tage; ≈ 11 GB bei Indexierung) | 5.4 |
| C2 | MUSS | Log-Format unverändert lassen (Textmuster, Profil `json` nicht aktivieren) | 5.1 |
| C3 | SOLL | Volumen senken: DEBUG der App-Pakete auf INFO (`LOGGING_LEVEL_COM_AUTOHAUS_BACKEND=INFO`, ≈ −15 %); Access-Zeilen der Dateiabrufe nicht oder nur gesampelt schreiben (≈ −70 %, erfordert eine Codeänderung). Beides vorher gegen die Statistik-Abfragen prüfen: Die Server-Last-Kennzahl (`fileRequests`) beruht auf den Access-Zeilen | 5.3 |
| C4 | SOLL | Außerhalb von Azure: Nutzungsstatistik und Kapazitätsscan abschalten (`USAGE_STATS_ENABLED`, `USAGE_INSIGHTS_ENABLED`, `USAGE_FUNNEL_ENABLED`, `USAGE_DROPOFF_ENABLED`, `CAPACITY_SCAN_ENABLED` jeweils `false`) und den KI-Fehlerbericht (`LLM_API_KEY` nicht setzen) — oder eine Log-Analytics- und Blob-Anbindung per Managed Identity bereitstellen. Die Buchungsstrecke ist davon unabhängig | 2.2 |
| C5 | KANN | Externe Verfügbarkeitsprüfung wie bisher: 5 Standorte, alle 5 min gegen `/api/v1/health`, Alarm bei Ausfall an mindestens 3 Standorten über 5 min | 2.4 |

### D. Konfiguration und Geheimnisse

| Nr. | Stufe | Anforderung | Begründung |
|---|---|---|---|
| D1 | MUSS | Geheimnisse nur aus dem Secret-Store, nie im Image oder Repository: `SESSION_SECRET`, `SOFTNET_FINGERPRINT_JSON`, `RECALL_FINGERPRINT_JSON`, `SETUP_JSON` (alternativ die Datei `config/setup.json`), `REPORT_ADMIN_PASSWORD`, `LLM_API_KEY` | Anhang B |

---

## 7. Zur Frage „Autoscaler"

- **Ist-Zustand:** Es ist kein Autoscaling aktiv (min = max = 1, keine Skalierungsregel). Nur beim Revisionswechsel laufen kurz zwei Replikate.
- **CPU- und Speicherregeln würden nie auslösen** (CPU Ø 0,004 vCPU, Spitze 0,19 vCPU; Arbeitsspeicher konstant 440–755 MiB).
- **Die Spitzen dauern Minuten und kommen ohne Vorwarnung.** Eine zusätzliche Instanz braucht rund 7 s zum Start plus die Verzögerung der Plattform, und ihr Terminsuche-Cache ist leer.
- **Wenn die Plattform eine Regel verlangt:** Parallelität je Instanz als Auslöser (≥ 100 gleichzeitige Anfragen; gemessen höchstens 28 SoftNet-gebundene, Dateiabrufe dauern < 1 ms), Mindestinstanzen = 1. Eine Obergrenze über 1 nur, wenn A1 erfüllt ist.
- **Wirksamer als Scale-out gegen die Spitzen** ist ein Edge-Cache mit Kompression (B3): 84 % der Zugriffe ließen sich dort bedienen.

---

## 8. Grenzen der Messung

- **Messfenster 30 Tage** (Aufbewahrung der Logs). Der Jahresverlauf, etwa die Reifenwechsel-Saison im Frühjahr, ist nicht gemessen.
- **Eine einzelne Instanz.** Das Verhalten bei mehreren Instanzen wurde nicht gemessen.
- **Zugriffe** stammen aus dem Access-Log der App. Der Ingress zählt in der Spitzenminute rund 8 % mehr (3.333 gegenüber 3.074 in derselben Minute), weil er auch Anfragen sieht, die die App nie erreichen. Minutenwerte enthalten ≈ 1 Health-Check pro Minute.
- **Sekundenspitzen und Gleichzeitigkeit** sind aus den Zeitstempeln der Logs (Auflösung 1 s) und dem Feld `duration` rekonstruiert: Beginn = Ende − Dauer. Die Gleichzeitigkeit zählt nur SoftNet-gebundene API-Aufrufe.
- **Keine IP-Adressen im Log.** Die Herkunft der Spitzen ist deshalb nicht prüfbar.
- **Planungswerte** (Faktor 2) sind ein Vorschlag und keine Messung.

---

## 9. Fragen an das Betriebsteam

1. Welche **Log-Plattform** ist vorgesehen? Die Auswertung der Statistik und des Fehlerberichts nutzt Abfragen auf Log Analytics (KQL).
2. Gibt es **Edge, CDN oder Ingress mit Cache und Kompression** (B3)?
3. Welche **Verfügbarkeit** wird erwartet? Eine Instanz heißt: Neustart ≈ 7 s und Verlust laufender Buchungen beim Deployment. Hochverfügbarkeit mit zwei Instanzen erfordert A1.
4. Wie wird die **Client-IP** an die App weitergereicht (B2)?
5. Gibt es **Egress-Filter** (Freigabeliste für SoftNet, B6) und ein **Zeitfenster für Deployments** (A5)?

---

## Anhang A: Messmethodik

**Zugriffe pro Minute** (Access-Zeilen des Loggers `HTTP`):

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == 'gs-terminbuchungs-app' and TimeGenerated >= ago(30d)
| where Log_s has 'INFO  HTTP'
| summarize Zugriffe = count() by Minute = bin(TimeGenerated, 1m)
| top 15 by Zugriffe desc
```

**Log-Volumen je Tag:**

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == 'gs-terminbuchungs-app' and TimeGenerated >= ago(30d)
| summarize Zeilen = count(), Text = sum(strlen(Log_s)), Abgerechnet = sum(_BilledSize) by Tag = bin(TimeGenerated, 1d)
```

**Gleichzeitige SoftNet-gebundene Anfragen** (Beginn = Ende − Dauer, je Sekunde gezählt):

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == 'gs-terminbuchungs-app' and TimeGenerated >= ago(30d) and Log_s has 'INFO  HTTP'
| extend Url = extract('"url":"([^"]*)"', 1, Log_s), Dur = toint(extract('"duration":"([0-9]+)ms"', 1, Log_s))
| where Url startswith '/api/' and Url != '/api/v1/health' and Url != '/api/v1/session/token' and Dur > 0
| extend Sek = range(bin(TimeGenerated - Dur * 1ms, 1s), bin(TimeGenerated, 1s), 1s)
| mv-expand Sek to typeof(datetime)
| summarize InFlight = count() by Sek
| top 1 by InFlight desc
```

**Azure-Monitor-Metriken** der Container App: `UsageNanoCores`, `WorkingSetBytes`, `Replicas`, `RestartCount`, `TxBytes`, `RxBytes`, `Requests`, `JvmMemoryTotalUsed`, `JvmMemoryTotalCommitted`, `JvmMemoryTotalLimit`, `JvmThreadCount`.

Alle Auswertungen sind anonyme Aggregate (Zähler, Summen, Perzentile). Es wurden keine Sitzungs-IDs, Personen oder Buchungsdaten ausgewertet oder ausgegeben.

---

## Anhang B: Konfiguration (Ist, ohne Geheimnisse)

| Variable | Wert |
|---|---|
| `PORT` | `3000` |
| `SOFTNET_BASE_URL` | `https://api.soft-nrg.com` |
| `SOFTNET_AUTH_URL` | `https://auth.soft-nrg.com/oauth/token` |
| `SOFTNET_TIMEOUT` | `50000` (ms) |
| `SESSION_TTL_MINUTES` | `20` |
| `RADWECHSEL_ENABLED` | `true` |
| `CORS_ALLOWED_ORIGINS` | die öffentliche Adresse der Container App |
| `APP_FRAME_ANCESTORS` | `'self'` und `https://www.gottfried-schultz.de` (zusätzlich lokale Entwicklungsadressen) |
| `USAGE_STATS_ENABLED`, `USAGE_INSIGHTS_ENABLED`, `USAGE_FUNNEL_ENABLED`, `USAGE_DROPOFF_ENABLED` | `true` |
| `USAGE_STORAGE_ACCOUNT`, `USAGE_STORAGE_CONTAINER` | `gsterminbuchungusage`, `usage-stats` |
| `USAGE_COLLECTING_SINCE` | `2026-09-22` |
| `CAPACITY_SCAN_ENABLED`, `CAPACITY_SHOW_SLOT_COUNT` | `true` |
| `LLM_MODEL`, `LLM_BASE_URL` | `azure/gpt-5-mini`, `https://gateway.lhind.ai/v1` |
| Secrets (nur Namen) | `SESSION_SECRET`, `SOFTNET_FINGERPRINT_JSON`, `RECALL_FINGERPRINT_JSON`, `SETUP_JSON`, `REPORT_ADMIN_PASSWORD`, `LLM_API_KEY` |
| Nicht gesetzt (Standardwerte gelten) | `JAVA_OPTS` (leer), `APP_RATE_LIMIT_CAPACITY` (500), `APP_RATE_LIMIT_REFILL_MINUTES` (15), `SERVER_FORWARD_HEADERS_STRATEGY`, `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` (30 s) |
