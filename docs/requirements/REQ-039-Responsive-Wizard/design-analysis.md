# REQ-039 — Designanalyse der Desktop-Referenzen und Bestandsimplementierung

**Datum:** 2026-10-08  
**Status:** Referenzanalyse zum Draft, keine visuelle oder funktionale Abnahme  
**Bearbeitung:** Agent 2 (Requirement)  
**Verknüpftes Requirement:** [requirement.md](./requirement.md)

## 1. Quellen, Vorgehen und Entscheidungsrangfolge

Alle elf Dateien in [references](./references/) wurden einzeln als Bilder geöffnet und visuell geprüft. Die Dateiabmessungen wurden zusätzlich aus den PNG-Dateien gelesen. Die Bilder sind Desktop-Referenzen; es gibt unter diesen elf Quellen keine bereits freigegebene Mobile-Vorlage. Die sechs Mobile-Ansichten werden aus derselben Informationshierarchie abgeleitet.

Vor der Dokumentation wurden [VISION](../../VISION.md), [REQ-TEMPLATE](../REQ-TEMPLATE.md), [Create-Workflow](../../../.claude/commands/create-requirement.md), [Check-Workflow](../../../.claude/commands/check-requirement.md), [Registry](../REQUIREMENTS.md), [Angular-Architektur](../../../.claude/skills/angular-architecture.md), [Routing-Patterns](../../../.claude/skills/routing-patterns.md), die bestehenden Anforderungen sowie die tatsächlichen Booking-Komponenten, Modelle, Services, Guards, Resolver und Übersetzungen konsultiert.

**Verbindliche Rangfolge:**

1. Aktuelle ausdrückliche Nutzeranweisung: vorhandene Referenzen vollständig berücksichtigen, Farben/Farbnuancen exakt übernehmen, sechs Desktop- und sechs Mobile-Ansichten, vorhandene Navigation wiederverwenden.
2. Aktuell implementierte Fach-/Navigationsregeln: sieben konfigurierte Schritte, sechs sichtbare Schritte im bereits vorhandenen Deep-Link-Kontext, rückwärts erlaubter Punktklick, bestehende Reset-/Guard-/Resolverlogik.
3. Explizite Farbtabelle in [farben-rules.png](./references/farben-rules.png) als Farbwertquelle; übrige Screenshots als Struktur-/Zustandsreferenzen.
4. Aktuelle Bootstrap-Komponenten, Systemschrift, Marken-Assets und zentrale Tokens als technische Umsetzungsgrundlage.
5. Ältere Requirements und Workflow-Beispiele als Historie. Anweisungen wie „keine Farben aus Bildern“, Material-Komponenten, immer gefüllte Zurück-Buttons, erfundener GS-Header, HashLocation oder Click-Dummy-APIs gelten hier nicht gegen Nutzeranweisung und verifizierten Bestand.

**Zeitlicher Hinweis:** Die Ist-Abweichungen in Abschnitt 6 beschreiben die Aufnahme zu Beginn des Auftrags, vor den parallel laufenden REQ-039-Codeänderungen. Die konkret abgestimmte gemeinsame Header-Lösung ist in Abschnitt 7 dokumentiert. Vorhandener geänderter Code oder erstellte HTML-Dateien sind noch kein Nachweis für erfolgreich gerenderte PNGs, bestandene Tests oder WCAG-/Lighthouse-Ziele.

## 2. Vollständiges Inventar: 11 von 11 Dateien

| Nr. | Datei | PNG-Abmessungen | Schritt / Variante | Sichtbarer Befund |
|---|---|---|---|---|
| R-01 | [Werkstatttermin_Marke.png](./references/Werkstatttermin_Marke.png) | 948 × 665 | Marke, erster sichtbarer Deep-Link-Schritt | Standortbanner; zentrierte Frage und Untertitel; sechs Marken-Kacheln; Volkswagen mit primärfarbigem Rahmen ausgewählt; Referenz-Weiter rechts unten |
| R-02 | [Werkstatttermin_Service.png](./references/Werkstatttermin_Service.png) | 948 × 872 | Service | Marke mit grünem Häkchen, Service aktiv; acht sichtbare Kategorien als umbrechende Chips mit Icon/Zählstand; Inspektions-Detailkarte mit Checkboxen und Beschreibungen; grüne Auswahlzusammenfassung |
| R-03 | [Werkstatttermin_Hinweise.png](./references/Werkstatttermin_Hinweise.png) | 948 × 703 | Hinweise | Marke/Service abgeschlossen; Karten für Mietwagen-Kategorie, bevorzugte Tageszeit und E-Mail/SMS; Informationsfläche für servicebezogene Hinweise; Zurück/Weiter |
| R-04 | [Werkstatttermin_Termin.png](./references/Werkstatttermin_Termin.png) | 948 × 728 | Termin | Drei Schritte abgeschlossen; Monats-/Datumsbereich mit ausgewähltem Datum und weiteren Kacheln; Kalenderlink; Vormittags-Slot; grüne Terminbestätigung; Zurück/Weiter |
| R-05 | [Werkstatttermin_Kontakt.png](./references/Werkstatttermin_Kontakt.png) | 948 × 857 | Kontakt ohne VIN | Vier Schritte abgeschlossen; Kundendatenkarte mit E-Mail, Anrede, Namen und Adresse; Fahrzeugkarte mit Kennzeichen/Kilometerstand; Einwilligung; „Zur Buchungsübersicht“ |
| R-06 | [Werkstatttermin_Kontakt_mitVIN.png](./references/Werkstatttermin_Kontakt_mitVIN.png) | 948 × 924 | Kontakt mit VIN | Gleiche Struktur und derselbe aktive Schritt wie R-05; zusätzliche VIN-Zeile mit Pflichtstern in der Fahrzeugkarte; größere Inhaltshöhe, keine zusätzliche Wizard-Position |
| R-07 | [Werkstatttermin_Uebersicht.png](./references/Werkstatttermin_Uebersicht.png) | 948 × 900 | Übersicht vor Anfrage, ohne VIN | Fünf grüne Häkchen, Übersicht aktiv; Notizfeld; zweispaltige Zusammenfassung plus Anfragebereich; „Weitere Services hinzufügen“ und „Termin anfragen“; Zurück |
| R-08 | [Werkstatttermin_Uebersicht_Vin.png](./references/Werkstatttermin_Uebersicht_Vin.png) | 948 × 918 | Übersicht vor Anfrage, mit VIN | Struktur wie R-07, zusätzlich VIN in den Fahrzeugdetails; gleicher Status und gleiche Anfrageaktion |
| R-09 | [Werkstatttermin_Uebersicht2.png](./references/Werkstatttermin_Uebersicht2.png) | 948 × 857 | Übersicht nach Erfolg, ohne VIN | Notiz mit Beispielinhalt; rechts grüne Nachricht „Ihre Terminanfrage wurde erfolgreich gesendet“; Hinweis auf Rückmeldung über gewählten Kontaktweg; „Fertig“ rechts unten; Übersicht bleibt aktiv |
| R-10 | [Werkstatttermin_Uebersicht2_Vin.png](./references/Werkstatttermin_Uebersicht2_Vin.png) | 948 × 875 | Übersicht nach Erfolg, mit VIN | Erfolgsdarstellung wie R-09 und VIN-Zeile in der Fahrzeugzusammenfassung; kein neuer Schritt und keine weitere Fortschrittsposition |
| R-11 | [farben-rules.png](./references/farben-rules.png) | 1024 × 1080 | Standard-/Kontrastpalette | Zehn Verwendungszeilen für Primary, Secondary, Text, Links, Borders, Success, Modal/Header, aktive Tags, Icons und Disabled-Zustände; maßgebliche exakte Farbwerte |

Die Reihenfolge des Dateiinventars ist analytisch, nicht eine zweite Wizard-Konfiguration. Die unterschiedlichen Bildhöhen zeigen längere Form-/Übersichtsvarianten, keine unterschiedlichen Designsysteme.

## 3. Gemeinsame Struktur und visuelle Sprache

### 3.1 Rahmen, Host und Wizard-Inhalt

Alle zehn Oberflächenreferenzen zeigen eine helle, abgerundete Fläche mit äußerem Schatten. Oben befindet sich eine Kopfzeile mit „Werkstatttermin“, roten Accessibility-/Taschen-Symbolen und einem Schließen-Symbol. Darunter liegt der helle Fortschrittsstreifen; dann folgen Hauptinhalt und Aktionsleiste mit Trennlinie.

Dieser äußere Modal-/Toolbar-Rahmen ist **nicht automatisch ein vorhandener Angular-App-Header**. In [app.component.html](../../../src/app/app.component.html) wurden Header und Footer ausdrücklich entfernt; die aktive Shell enthält Skip-Link, Hauptinhalt/Router und `FloatingOverlayComponent`. Deshalb:

- Eine statische Referenzvorschau darf den Rahmen als **Host-Kontext** zeigen und ihn entsprechend kennzeichnen.
- Die produktive Umsetzung ergänzt nicht heimlich einen GS-Logo-/Firmenheader, Material-Toolbar oder Host-Schließen-Verhalten.
- Die rote Host-Symbolfarbe wird nicht zur neuen Wizard-Aktivfarbe. Fortschritt und primäre Aktionen folgen der blauen Referenzpalette.
- Die Außenhöhe ist nicht zu fixieren: längere Formulare, Fehlermeldungen und Schriftvergrößerung benötigen vertikalen Platz.

### 3.2 Fortschrittsnavigation

Sichtbare Reihenfolge in allen Oberflächenreferenzen:

**Marke → Service → Hinweise → Termin → Kontakt- & Fahrzeugdaten → Übersicht**

- Erledigt: grüne gefüllte Kreise mit Häkchen; vorangehende Positionen sind klar unterscheidbar.
- Aktiv: primärfarbiger Ring/Punkt und hervorgehobenes Label.
- Ausstehend: helle neutrale Kreise mit grauem Label.
- Verbindungen: feine horizontale Linien, optisch zurückhaltend.
- Alle Labels bleiben im Desktop sichtbar, einschließlich der langen Kontaktbezeichnung.
- Die Erfolgsbilder R-09/R-10 verändern nicht die Anzahl der Schritte: Übersicht bleibt aktiv.

Die Bilder zeigen Positionen und Zustände, aber keine ausführbaren Navigationsregeln. Zulässige Sprünge und Reset-Wirkungen werden ausschließlich aus dem Code übernommen; ein grünes Häkchen ist hier keine zusätzliche dauerhafte Daten-/Validierungsmarkierung.

### 3.3 Typografie, Icons, Abstände

| Merkmal | Sichtbarer Befund | Technische Ableitung ohne neue Designsprache |
|---|---|---|
| Schriftfamilie | Sachliche serifenlose UI-Schrift; aus gerasterten Bildern kein sicherer konkreter Font nachweisbar | Vorhandener System-Stack aus [styles.scss](../../../src/styles.scss): `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `Roboto`, `Helvetica Neue`, `Arial`, `sans-serif`; keine neue Webfont |
| Hierarchie | Deutlich größere/fettere Seitenüberschrift; grauer Untertitel; kleinere fett hervorgehobene Abschnitts-/Feldlabels | Bestehende `--font-size-*` und `--line-height-*` nutzen, statt eine neue lokale Größenskala zu verteilen |
| Ausrichtung | Marke mit zentrierter Frage; Service/Hinweise/Termin/Kontakt/Übersicht inhaltlich überwiegend linksbündig | Seitenspezifische Informationshierarchie erhalten; Mobile-Titel des Wizards unabhängig davon zentrieren |
| Icons | Kleine konsistente Symbole bei Kategorien, Feldern und Zusammenfassungen; runde Statusmarken | Bestehende Bootstrap Icons und Marken-Assets einsetzen; kein Material-CDN, keine neuen GS-/Markenlogos |
| Abstände | Wiederkehrender Innenabstand, klare Kartengruppen, gleichmäßige Lücken zwischen Labels/Controls | Vorhandene `--spacing-xs/sm/md/lg/xl` verwenden; bei schmaler Breite nur erforderlichen Außenabstand reduzieren |
| Ecken/Rahmen | Leicht abgerundete Felder, Karten und Buttons; aktive Auswahl stärker umrandet | Bestehende `--radius-*`, semantische Border-/Primary-Tokens; kein neues Schatten-/Gradientensystem |
| Aktionsleiste | Primäraktion blau gefüllt, Zurück weiß mit farbigem Rahmen | Bestehendes Bootstrap-Pattern `btn-primary` / `btn-outline-secondary` zentral auf die Referenzpalette abbilden |

Exakte Fontmetrik, CSS-Abstände oder ursprüngliche DOM-Struktur können aus einem PNG nicht vollständig rekonstruiert werden. Solche Werte werden nicht als gemessen behauptet. Die belastbare Quelle für konkrete Farbwerte ist R-11; die vorhandenen Tokens und der visuelle Vergleich bestimmen die übrige Umsetzung.

## 4. Inhaltliche Analyse je Schritt und Variantenzuordnung

### 4.1 Marke — R-01

- Das Standortbanner steht vor der Markenauswahl, zeigt ein Standorticon, „Ihr Standort“ und eine vollständige Adresse.
- Die Referenz enthält Cupra, Hyundai, Seat, Volkswagen, VW Nutzfahrzeuge und Škoda. Volkswagen ist ausgewählt; spätere Referenzen zeigen beispielhaft Cupra. Die Referenzen sind also kein durchgängig identischer fachlicher Datensatz.
- Produktiv bleiben Branch-Marken und [BRAND_LOGO_MAP](../../../src/app/features/booking/models/branch-config.model.ts) maßgeblich; keine feste Sechs-Marken-Regel aus dem Bild ableiten.
- Mobile: Bannertext umbrechen, gleich große Logo-Kacheln in ein passendes Raster umbrechen; kein horizontaler Scrollstreifen.
- **Wichtige Bestandsabweichung:** [onBrandSelect](../../../src/app/features/booking/components/brand-selection/brand-selection-container.component.ts) setzt die Marke und navigiert sofort nach `/home/services`. Es existiert kein produktiver Weiter-Button auf Marke. Der gezeichnete Referenz-Weiter-Button darf in der statischen Vorschau vorkommen, wird aber nicht als Auftrag zu neuer Zwischenspeicherung oder zweiter Marken-Navigation verstanden.

### 4.2 Service — R-02

- Kompakte Kategorien liegen in mehreren Zeilen: Inspektion, TÜV & AU, Bremsflüssigkeitswechsel, Räderwechsel, Aktionen/Checks, Reparatur/Beanstandung, Karosserie/Frontscheibenwechsel sowie Hol & Bringservice.
- Icons, Zähler und Auswahlanteile bleiben sichtbar; die aktive Kategorie ist hervorgehoben. Das Detailpanel gruppiert Operations mit Checkbox, Haupttext, optional Preis und Beschreibung.
- Die grüne Zusammenfassung trennt ausgewählte Leistungen semantisch vom Detailpanel und steht vor der Navigation.
- Produktiv kommen Kategorien/Operations aus dem bestehenden Store; virtuelle Hol-/Bring-Kategorie und tireChange-Filter werden nicht mit Screenshot-Konstanten ersetzt. Die Referenzpreise sind Beispielwerte, kein Preisberechnungsauftrag.
- Mobile: Kategorien/Labels umbrechen, Detailbereich unterhalb, Checkboxen mitsamt Beschriftung ausreichend bedienbar. Der bestehende horizontale Service-Track ist ein expliziter Reflow-Prüfpunkt; das Verkleinern der Browserbreite allein beseitigt ihn nicht.

### 4.3 Hinweise — R-03

- Mietwagen-Kategorie als Karte mit Switch, Tageszeit als drei Pills, Benachrichtigung per E-Mail/SMS als zweifache Auswahl; darunter servicebezogene Hinweise.
- Produktiv besitzen [NotesContainerComponent](../../../src/app/features/booking/components/notes/notes-container.component.ts) und seine Children bereits Mietwagen-ID, `notesExtras`, optionalen Notiztext, Rückruf und dynamische Hints.
- Das Bild zeigt nicht sämtliche heute vorhandenen Felder. REQ-039 entfernt weder Rückruf noch Notiz und verschiebt die Notiz nicht eigenmächtig in einen anderen Store-/Submit-Pfad.
- `DEFAULT_NOTES_EXTRAS` bleibt Tageszeit `anytime`, Rückruf `no`, Kanal `email`; Mietwagen-ID bleibt ein separates Feld. Die Darstellung der Controls ändert diesen Vertrag nicht.
- Mobile: eine Karte pro Zeile, Pills mit Wrap, Switch und Beschriftung ohne Überlagerung. Der jetzige Notes-Form-Längenvalidator bleibt bestehen; kein neues Zeichenlimit aus einer Beispielnachricht.

### 4.4 Termin — R-04

- Monatskopf, Datumskacheln mit Wochentag/Tag/Monat, ein weiterer Kalenderzugang; darunter Tageszeitgruppe und auswählbarer Zeitslot.
- Die Referenz zeigt September 2026 einschließlich wiederholter Datumsanzeige. Das ist Anschauungsmaterial, kein Auftrag, in der Anwendung vergangene oder doppelte Slots fest zu erzeugen.
- Bestands-Schnellauswahl und Werkstattkalender verwenden gemeinsam `activeStepIndex = 4`; die Uhrzeitanzeige bleibt an die vorhandene Berlin-Zeitdarstellung gebunden.
- Mobile: Datumskacheln/Slotraster umbrechen, Kalenderzugang erreichbar halten, ausgewählten Termin als lesbare Statusfläche zeigen. Auch `.date-strip` besitzt im Bestand horizontales Scrolling und muss gesondert auf die geforderte scrollfreie Variante geprüft werden.
- HTTP-410-Konfliktbehandlung und frisches Laden der Termine bleiben der vorhandene besondere Fehlerpfad, kein Breadcrumb-Reset.

### 4.5 Kontakt — R-05 und R-06

- Gemeinsame Struktur: Überschrift, Kundendatenkarte, Fahrzeugkarte, Einwilligung, Navigation. Mehrspaltige Zeilen ordnen zusammengehörige Felder auf Desktop; Mobile stapelt sie.
- Die Fahrzeugdatenkarte in R-06 ergänzt nur eine VIN-Zeile. Es bleibt derselbe fünfte sichtbare Wizard-Schritt; die Bildhöhe wächst.
- **Bestandsabweichungen:** Das aktuelle [Kontaktformular](../../../src/app/features/booking/components/carinformation/carinformation-container.component.ts) enthält die erforderliche Mobilnummer und eine verpflichtende Anrede; die Referenzen zeigen keine Mobilnummer und keinen Anrede-Pflichtstern. Produktive Pflichtregeln bleiben unverändert.
- Das produktive Fahrzeugformular enthält Kennzeichen und ganzzahligen Kilometerstand ab 10, aber kein VIN-Control. [VehicleInfo](../../../src/app/features/booking/models/customer.model.ts) und der API-Vertrag erlauben optional `vin`; daraus folgt weder dessen Sichtbarkeit noch eine Pflicht.
- Der VIN-Beispielwert im Bild ist kein belastbarer Validator-Testfall. Für statische Varianten werden nur synthetische Werte verwendet und die Variante als Referenzzustand gekennzeichnet; keine neue Datenerfassung.
- Eingaben, Einwilligung und Fehlermeldungen dürfen beim Umbrechen nicht doppelt im DOM/State aufgebaut werden; dieselben FormGroups und `form`-Inputs bleiben erhalten.

### 4.6 Übersicht — R-07 bis R-10

| Variante | Unterschied | Konsequenz |
|---|---|---|
| R-07 vor Anfrage ohne VIN | Notizfeld, Services ändern, Anfrage-Button, Zusammenfassung | Basislayout derselben sechsten Seite; vorhandene Submit-Aktion bleibt maßgeblich |
| R-08 vor Anfrage mit VIN | Zusätzlich eine VIN-Zeile | Nur statische Referenzvariante; keine neue Pflicht-/Datenerhaltungslogik |
| R-09 Erfolg ohne VIN | Grüne Erfolgsmeldung statt Anfrageaktion, „Fertig“ in der Leiste | Erfolg bleibt Übersicht; keine neue Route, kein neuer Abschluss-Handler |
| R-10 Erfolg mit VIN | Erfolg plus VIN-Zeile | Kombination der beiden Varianten, weiterhin dieselbe Wizard-Position |

- Desktop gruppiert die große Zusammenfassung links und einen schmaleren Anfrage-/Statusbereich rechts. Mobile bringt beide Bereiche untereinander in den Dokumentfluss; lange Adressen und Services dürfen nicht die Seitenbreite vergrößern.
- Die Referenz unterscheidet „Anfrage gesendet“ von endgültiger Terminbestätigung. Der aktuelle [Overview-Code](../../../src/app/features/booking/components/booking-overview/booking-overview-container.component.html) zeigt bereits eine Bestätigung über `hasSubmitted()`/`bookingOverview.confirmation.message`, einen deaktivierten bestehenden Submit-Button und Zurück. Der aktuelle Text spricht von einem reservierten Termin. Dieser Textunterschied wird als Befund dokumentiert; REQ-039 verändert keine Buchungszusage oder API-Semantik.
- Der Referenz-Notizeditor und „Weitere Services hinzufügen“ sind nicht Belege für bereits vorhandene Handler. Vorhandene Notiz-/Navigationspfade werden wiederverwendet, nicht durch eine reine Layoutaufgabe neu erfunden.
- „Fertig“ im Referenzrahmen ist keine Erlaubnis für eine neue Host-Weiterleitung. Eine statische Rückkehr zur Galerie ist nur Mockup-Navigation.

## 5. Exakte Referenzpalette und zentrales Tokenmapping

### 5.1 Abgelesene Werte aus R-11

Die Hexwerte in diesem Abschnitt sind **Referenzbefunde**, keine Aufforderung zum Hardcoding in einzelnen Komponenten. Im [Requirement](./requirement.md) stehen ausschließlich Tokenreferenzen. Farbnamen ohne festgelegten Hexwert werden nicht anhand antialiaster Schriftkanten als neue Palette erfunden.

| Verwendung laut R-11 | Standard | Kontrast | Zentrale Umsetzung |
|---|---|---|---|
| Primary Button/Text: Hintergrund / Text | `#1E40AF` / `#FFFFFF` | `#003366` / `#FFFFFF` | `--color-primary` / `--color-text-on-primary` |
| Secondary Button: Border / Text, Hintergrund weiß | `#1E40AF` / `#0F172A` | `#003366` / `#000000` | Rahmen `--color-primary`, Text `--color-text-primary`, Fläche `--color-background-surface` |
| Haupttext / Sekundärtext | `#0F172A` / `#4B5563` | `#000000` / `#374151` | `--color-text-primary` / `--color-text-secondary`; `--color-text-muted` als Alias prüfen |
| Links | `#1E40AF` | `#003366` | `--color-primary`; Bootstrap-Linkvariablen anbinden |
| Borders | `#999999` | `#4B5563` | `--color-border` |
| Success Info: Hintergrund / Text & Icon | `#EDF6F0` / `#14532D` | `#DCFCE7` / `#14532D` | `--color-success-bg` / `--color-success` |
| Modalfläche / Hintergrund Header | `#FFFFFF` / `#F8FAFC` | `#FFFFFF` / `#E5E7EB` | `--color-background-surface` / `--color-background-header` |
| Tag aktiv: Hintergrund / Text | `#1E40AF` / `#FFFFFF` | `#003366` / `#FFFFFF` | `--color-primary` / `--color-text-on-primary` |
| Icons | `#1E40AF` | `#003366` | `--color-primary`; Statusicons verwenden jeweilige Zustandssemantik |
| Disabled: Border/Hintergrund/Icons / Text | `#E5E7EB` / `#0F172A` | `#D1D5DB` / `#000000` | `--color-disabled` / `--color-text-primary` |

**Fortschrittszuordnung:** Abgeschlossene Kreise und ihre Verbindungen verwenden `--color-success`, Checkmark darauf `--color-text-on-primary`; aktive Ziele `--color-primary`; zukünftige Ziele die neutralen Disabled-/Sekundärtext-Tokens. Der aktuelle Titel verwendet die aktive Primärfarbe. Der dunkle Grünton für erledigte Schritte ist die übertragene Success-Farbe, kein frei gewähltes „Bootstrap-Grün“.

### 5.2 Integration in vorhandene zentrale Styles

- Quelle bleibt [src/styles/_variables.scss](../../../src/styles/_variables.scss). Die zusätzlich benötigten semantischen Slots heißen `--color-background-header`, `--color-success-bg` und `--color-disabled`; keine seitenlokalen Primär-/Erfolgsfarben.
- [src/styles/_bootstrap-theme.scss](../../../src/styles/_bootstrap-theme.scss) bildet `--bs-primary`, Link-/Button-/Success-Variablen und benötigte RGB-Gegenstücke auf dieselbe Palette ab. Eine bloße Änderung von `--color-primary` reicht nicht, solange Bootstrap-Komponenten noch andere Werte verwenden.
- Kontrastmodus bleibt am vorhandenen `html[data-high-contrast="true"]`-Mechanismus; er überschreibt zentral die Werte, nicht jede der sechs Seiten separat.
- Primärfarbiger Fokus und sekundäre Outline-Aktionen sollen dasselbe Token-System nutzen. Hover-/Pressed-Effekte dürfen keinen neuen Farbton hinzufügen; ein vorhandener Referenzton darf zentral wiederverwendet werden, seine Kontrastwirkung bleibt zu messen.
- Der helle Standort-/Informationsbereich darf nicht den bisherigen dunklen Gradient behalten. Wo R-11 keinen separaten Info-Flächenwert nennt, werden vorhandene semantische Surface-/Header-/Primary-Tokens verwendet und visuell abgeglichen, nicht aus einer Schattenkante ein neuer verbindlicher Hexwert abgeleitet.
- Die Referenz enthält feine, teilweise sehr helle Trennlinien. Funktionsrelevante Control-Rahmen sind mit dem expliziten Border-Token zu prüfen; rein dekorative Divider dürfen über die zentral dokumentierte Neutralzuordnung laufen.
- Fehler-/Warn-/Host-Akzentfarben sind nicht vollständig in R-11 spezifiziert. Bestehende fachliche Fehler-/Warnzustände bleiben erhalten; daraus entsteht kein zusätzlicher allgemeiner Wizard-Akzent und kein pauschaler globaler Rebrand anderer App-Funktionen.

### 5.3 Verifikation statt Konformitätsbehauptung

Referenzfarbe, berechnete Browserfarbe und Kontrastmessung sind drei getrennte Nachweise. Geprüft werden müssen Text-/Hintergrundpaare, Bedienrahmen, Fokus, aktive/abgeschlossene/neutrale Zustände und der Kontrastmodus. Das bloße Vorhandensein dunkler Farben oder eines Kommentars „WCAG geprüft“ in alten Styles belegt keine bestandene Accessibility-Abnahme. Lighthouse-Werte, Screenreader-Verhalten und Tastaturbedienung sind erst nach tatsächlicher Messung zu berichten.

## 6. Abweichungen des Ausgangscodes und ihre Behandlung

| Ausgangsbefund | Quelle | Konsequenz für REQ-039 |
|---|---|---|
| `WIZARD_STEPS` besitzt sieben Einträge einschließlich Standort; Bilder zeigen sechs | [wizard-steps.config.ts](../../../src/app/shared/components/wizard-breadcrumb/wizard-steps.config.ts) | Bestehende Deep-Link-Filterung nutzen, niemals Standort global löschen oder Indices neu nummerieren |
| Mobile blendet `.wizard__step-label` aus; nur das Label abgeschlossener Schritte war ein Link, Icon lag außerhalb | [Breadcrumb-Template](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.html), [SCSS](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.scss) | Root Cause: gemeinsames Icon-/Label-Bedienelement; bloß ein neues mobile Label reicht nicht |
| Aktiver Titel fehlte; Navigationsname war hartcodiert deutsch | [Breadcrumb-Komponente](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.ts), [Template](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.html) | `activeStep` aus vorhandener Liste ableiten; i18n und Statussemantik ergänzen |
| `booking.wizard.vehicle` hieß nur „Fahrzeug“ / „Vehicle“ | [translations.ts](../../../src/app/core/i18n/translations.ts) | Vorhandenen Key zu „Kontakt- & Fahrzeugdaten“ / „Contact & vehicle details“ erweitern |
| Alte zentrale Primary `#667EEA`, Success `#2E7D32`, Border `#E0E0E0`; Bootstrap separat `#003D6B` und Link `#00558E` | [Variablen](../../../src/styles/_variables.scss), [Bootstrap-Theme](../../../src/styles/_bootstrap-theme.scss) | Gemeinsame Referenzwerte und Bootstrap-Aliase zentral abgleichen; nicht nur Breadcrumb umfärben |
| Standortbanner hatte dunklen Gradient; Referenz ist hell und primärfarbig beschriftet | [Bootstrap-Theme](../../../src/styles/_bootstrap-theme.scss), [Marken-Template](../../../src/app/features/booking/components/brand-selection/brand-selection-container.component.html) | Helle zentrale Flächen-/Texttokens anwenden, Inhalt weiter aus Branch-Daten |
| Feste Breadcrumb-Höhe und `white-space: nowrap`; Labelanzeige allein ab Viewport-Tabletgrenze | [Breadcrumb-SCSS](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.scss) | Inhalt darf wachsen/umbrechen; Labelmodus nach verfügbarer Containerbreite, nicht allein nach Viewport |
| `--touch-target-min` war em-basiert; tatsächlicher kleiner Link/Icon erfüllte 44 × 44 nicht verlässlich | [Variablen](../../../src/styles/_variables.scss), [Breadcrumb-SCSS](../../../src/app/shared/components/wizard-breadcrumb/wizard-breadcrumb.component.scss) | Native gemeinsame Ziele mit rem-Minimum; echte Messung bei kleinem Body-Font |
| App-Main und Header-Innenabstände nehmen bei 320 Pixeln Platz für sieben Ziele weg | [app.component.scss](../../../src/app/app.component.scss) | Mobiles Inline-Padding gezielt reduzieren; keine überlappenden Ziele und kein Verstecken des Standort-Einstiegs |
| Service-Kategorien und Datumsstreifen verwendeten `overflow-x: auto` | [Bootstrap-Theme](../../../src/styles/_bootstrap-theme.scss) | Beide Inhaltsgruppen ausdrücklich auf umbrechende, ohne horizontalen Scroll erreichbare Darstellung prüfen; kein `overflow-x: hidden` als bloßes Kaschieren |
| Einzelne mobile Aktionsleisten besitzen bestehende Primary-first-Styles | [styles.scss](../../../src/styles.scss), [Bootstrap-Theme](../../../src/styles/_bootstrap-theme.scss) | Stilregeln konsistent nutzen; gleiche Handler/Controls behalten, keinen neuen mobilen Aktionsbaum |
| App-Header/Footer wurden entfernt; Floating Overlay übernimmt A11y/Cart | [app.component.html](../../../src/app/app.component.html), [REQ-015](../REQ-015-API-Data-Integration/requirement.md), [REQ-026](../REQ-026-Bootstrap-UI-Migration/requirement.md) | Host-Rahmen nur als gekennzeichnete Referenz, nicht als neue App-Shell implementieren |
| Markenwahl navigiert unmittelbar weiter, obwohl R-01 Weiter zeichnet | [BrandSelectionContainerComponent](../../../src/app/features/booking/components/brand-selection/brand-selection-container.component.ts) | Auto-Navigation beibehalten, statischen Referenzbutton nicht mit erfundener Produktivlogik gleichsetzen |
| VIN fehlt im aktiven Formular; Mobilnummer und aktuelle Validierung sind im Screenshot nicht vollständig dargestellt | [CarinformationContainerComponent](../../../src/app/features/booking/components/carinformation/carinformation-container.component.ts) | Bestand funktional vollständig zeigen; VIN als Referenzvariante, nicht als neue Pflicht |
| Übersicht hat bereits Submit-Sperre und eigenen Bestätigungstext, aber keinen „Fertig“-Handler | [BookingOverviewContainerComponent](../../../src/app/features/booking/components/booking-overview/booking-overview-container.component.ts), [Template](../../../src/app/features/booking/components/booking-overview/booking-overview-container.component.html) | Erfolgsdarstellung an vorhandenen Zustand binden; keine neue Abschlussaktion oder Fachzusage |

Die Tabelle ist eine Arbeits-/Prüfliste, keine Behauptung, dass sämtliche Punkte bereits in Code und Browser nachgewiesen erledigt sind.

## 7. Verifizierter Navigationsvertrag und abgestimmte Header-Lösung

### 7.1 Eine Liste, originale Indices, kein zweiter Store

| Sichtbarer Deep-Link-Schritt | Originalindex | Bestehende Route | Aktiver Containerindex |
|---|---|---|---|
| Marke | 1 | `/home/brand` | 1 |
| Service | 2 | `/home/services` | 2 |
| Hinweise | 3 | `/home/notes` | 3 |
| Termin | 4 | `/home/appointment`; Kalenderalternative `/home/workshop-calendar` | 4 in beiden Ansichten |
| Kontakt- & Fahrzeugdaten | 5 | `/home/carinformation` | 5 |
| Übersicht | 6 | `/home/booking-overview` | 6 |

Standort bleibt Originalindex 0 mit `/home/location` im normalen Flow. `stepsWithStatus` filtert nur bei `isDeepLinked`, berechnet `done`/`active`/`future` anhand `activeIndex` und führt den ursprünglichen Index weiter. Es gibt keinen persistenten „alle schon besuchten Schritte“-Status. Ein vorher besuchter späterer Schritt kann nach Rücksprung wieder ausstehend sein.

Der vorhandene `onStepClick(event, originalIndex)` verhindert Standardnavigation, erlaubt ausschließlich frühere Originalindices, sperrt Standort im Deep-Link-Kontext, ruft `store.resetFromStep(originalIndex)` auf und navigiert zur existierenden Route. Aktive/zukünftige Ziele bleiben No-op. Zusätzliche mobile Sprungerlaubnisse oder eine neue Validierungs-/Zustandsmaschine sind ausgeschlossen.

### 7.2 Reset einschließlich Zielschritt

Die vollständige Matrix steht in [requirement.md, Abschnitt 7](./requirement.md#72-exakte-reset-matrix-für-direkte-breadcrumb-rücknavigation). Maßgeblich ist [BookingStore.resetFromStep](../../../src/app/features/booking/stores/booking.store.ts), nicht die verkürzte Formulierung „nur nachfolgende Schritte löschen“:

- Ziel 0: Branch/Standort und alle nachfolgend aufgeführten Auswahlgruppen zurücksetzen.
- Ziel 1: Marke, Services, Extras und Termin/Kalender zurücksetzen.
- Ziel 2: Services, Extras und Termin/Kalender zurücksetzen.
- Ziel 3: Extras und Termin/Kalender zurücksetzen.
- Ziel 4: Termin/Kalender zurücksetzen.
- Ziel 5: Die Methode ändert keine Felder.

Insbesondere **nicht** durch diese Methode zurückgesetzt werden `bookingNote`, `selectedRentCategoryId`, `customerInfo`, `vehicleInfo`, `privacyConsent`, Submit-/Fehlerfelder und Deep-Link-Kontext. Die vorhandenen Zurück-Buttons besitzen eigene Wirkungen, etwa Hinweise-Zurück mit Löschung von Notiz/Extras/Mietwagen-ID. Unterschiedliche Bestandsaktionen werden nicht durch einen pauschalen neuen Reset gleichgeschaltet.

### 7.3 Konkrete gemeinsame responsive Darstellung

- Genau **ein nativer Button pro Schritt**, mit Icon und sichtbarem/verborgenem Label im selben Element, `type="button"` und dem vorhandenen Klickhandler. Alle sechs sichtbaren Ziele bleiben fokussierbar und anklickbar, auch wenn die kompakte Optik nicht wie eine klassische Schaltfläche aussieht.
- Aktive/zukünftige Buttons tragen `aria-disabled="true"`, **kein natives `disabled`**. Ihre Fokus-/Touch-Ziele bleiben bestehen; der gemeinsame Handler verhindert weiterhin jede Navigation. Der aktive Button erhält `aria-current="step"`.
- `activeStep` ist ein lesendes `computed`, das den aktiven Eintrag in `stepsWithStatus` findet. Der sichtbare Titel übersetzt dessen `label`; keine separat beschreibbare Mobile-Titelvariable.
- `wizard__current-title` bildet eine eigene, zum gesamten Header zentrierte und umbrechbare Zeile mit `role="status"`, `aria-live="polite"` und `aria-atomic="true"`.
- Benannter Inline-Size-Container `wizard`; **ab verfügbarer Containerbreite `56em`** erscheinen vollständige Labels, darunter kompakter Header. Das ist bewusst keine reine Viewport-Mediaquery. Tablet, iFrame und vergrößerte Schrift können kompakt bleiben.
- Trefferflächen mindestens `2.75rem` in Inline- und Blockrichtung, entsprechend 44 CSS-Pixeln bei 16-Pixel-Root. Das vorhandene `small`-Preset ändert Body-Schrift und verkleinert diese rem-Minima nicht. Mindestgröße trotzdem im Browser messen; bei verändertem Root gelten die realen Maße.
- Mobiles `app-main`-Inline-Padding wird mit `--spacing-xs` bemessen, damit auch sieben normale Ziele bei 320 CSS-Pixeln Platz haben. Sechs Ziele benötigen 264, sieben 308 CSS-Pixel allein für ihre Mindestbreiten; zusätzliche feste Lücken dürfen nicht zu Overflow führen.
- Lokalisierte Keys: `booking.wizard.progressLabel`, `completed`, `current`, `upcoming`; Fahrzeuglabel erweitert im vorhandenen Key `vehicle`. Alle fünf bestehenden Sprachobjekte erhalten die gleiche Struktur. Der öffentliche Selektor bleibt gemäß Bestand auf Deutsch begrenzt.

### 7.4 Guards, Resolver und API bleiben Ground Truth

Die bestehenden [Booking-Routen](../../../src/app/features/booking/booking.routes.ts) und [Guards](../../../src/app/features/booking/guards/) prüfen in Reihenfolge Standort, Marke, Services und im Kontakt-Guard zusätzlich Termin; Overview verwendet `isBookingComplete()`. Keiner dieser bestehenden allgemeinen Guards fordert einen bloßen Besuch aller früheren Seiten oder pauschal `notesExtras`.

Der Standort-Deep-Link-Guard benötigt derzeit `branchId` **und** `groupId`, setzt nach erfolgreichem Branch-Lookup `isDeepLinked` und leitet nach Marke um. Unvollständige/ungültige Parameter sind kein Auftrag, den Flow umzubauen. Die App nutzt aktuell Router-Pfade ohne HashLocation. Resolver laden die bisherigen Branch-/Marken-/Service-/Mietwagen-/Termin-Daten. Header und Resize rufen keine neuen API-Endpunkte auf.

## 8. Mockup-Zuordnung und Nachweisplan

| Ansichtsseite | Verwendete Referenzen | Desktop-/Mobile-Nachweis |
|---|---|---|
| [01-marke.html](./mockups/01-marke.html) | R-01, R-11 | `mockup-01-marke-desktop.png`, `mockup-01-marke-mobile.png` |
| [02-service.html](./mockups/02-service.html) | R-02, R-11 | `mockup-02-service-desktop.png`, `mockup-02-service-mobile.png` |
| [03-hinweise.html](./mockups/03-hinweise.html) | R-03, R-11 | `mockup-03-hinweise-desktop.png`, `mockup-03-hinweise-mobile.png` |
| [04-termin.html](./mockups/04-termin.html) | R-04, R-11 | `mockup-04-termin-desktop.png`, `mockup-04-termin-mobile.png` |
| [05-kontakt.html](./mockups/05-kontakt.html) | R-05/R-06, R-11 | `mockup-05-kontakt-desktop.png`, `mockup-05-kontakt-mobile.png` |
| [06-uebersicht.html](./mockups/06-uebersicht.html) | R-07/R-08/R-09/R-10, R-11 | `mockup-06-uebersicht-desktop.png`, `mockup-06-uebersicht-mobile.png` |

Die zwölf PNGs sind im Unterordner `screenshots` vorgesehen, zusätzlich `mockup.png` als Einstieg. [mockup.html](./mockup.html) verlinkt die sechs Ansichten; VIN-/Erfolgsvarianten bleiben Varianten derselben Seite und erhöhen die Anzahl der Wizard-Schritte nicht. Die Mockups dürfen per statischem Link zwischen vorgegebenen Zuständen wechseln; sie haben keinen Store, keinen Router, keine JavaScript-Validierung und keinen echten Booking-Submit.

**Render-/Abnahmestatus dieses Dokuments:** Die Mockup-Erstellung liegt bei Agent 1, Rendering/Bestätigung und Code-/Testprüfung bei der Integration. Agent 2 hat Referenzen und Code analysiert, aber die zwölf erzeugten Ansichten nicht als bestanden zertifiziert. Die Screenshots werden nach tatsächlicher Erstellung separat mit Viewport und Skalierung belegt.

**Verbindliche Prüfpunkte:**

- 320 / 375 / 390 / 768 / 1024 / 1280 CSS-Pixel; zusätzlich schmaler Header-Container auf breitem Viewport, Grenzprüfung um `56em`.
- Alle sechs Seiten; normaler Sieben-Schritte-Einstieg und Werkstattkalender als Regressionen.
- Kleine bis sehr große Schrift, 200-%-Textvergrößerung, 400-%-Zoom/Reflow, DE/EN und RTL.
- Sichtbare und echte mindestens 44 × 44 CSS-Pixel große Fokus-/Touch-Ziele; Status mit und ohne Farbe verständlich.
- Kein horizontaler Scrollzwang auch bei langen Kategorien/Datumsreihen; keine Overlay-/Sticky-Verdeckung.
- Erfolgs-/Fehler-/Loading-Zustände sowie besonders HTTP-410-Redirect; keine neue Datenerhaltsgarantie bei Breadcrumb-Rücksprung.
- Standard-/Kontrastpalette anhand berechneter Werte vergleichen; Accessibility und VISION-Ziele nur nach tatsächlicher Messung als bestanden ausweisen.

## 9. Konsultierte Anforderungen und Geltungsgrenzen

Die nötigen Inhalte wurden in begrenzten Reads aus Overview, Scope, Akzeptanzkriterien sowie einschlägigen Architektur-/Modell-/Business-Rule-/i18n-Abschnitten geprüft. Historische Angaben zu Material, Header, API-Dummies oder Schrittnummern wurden gegen die aktuelle Implementierung abgeglichen, nicht ungeprüft übernommen.

| Konsultierte Dokumente | Für diese Analyse relevanter Zusammenhang |
|---|---|
| [REQ-001 Header](../REQ-001-Header/requirement.md) | Historische A11y-/Sprachvorgaben; kein Auftrag zur Wiedereinführung des Headers |
| [REQ-002 Marke](../REQ-002-Markenauswahl/requirement.md), [REQ-003 Standort](../REQ-003-Standortwahl/requirement.md) | Ursprüngliche Auswahlseiten; Reihenfolge später durch REQ-013 geändert |
| [REQ-004 Service](../REQ-004-Serviceauswahl/requirement.md), [REQ-005 Hinweise](../REQ-005-Hinweisfenster/requirement.md), [REQ-006 Termin](../REQ-006-Terminauswahl/requirement.md) | Frühe Fach-/Layoutmuster; heutige Datenquellen und Handler gehen vor |
| [REQ-007 State-Sync](../REQ-007-WizardStateSync/requirement.md), [REQ-008 Kalender](../REQ-008-Werkstattkalender/requirement.md) | Reset und alternativer Terminpfad |
| [REQ-009 Kontakt](../REQ-009-carinformation/requirement.md), [REQ-010 Übersicht](../REQ-010-Buchungsübersicht/requirement.md) | Formulare, Einwilligung, Zusammenfassung und Anfrage |
| [REQ-011 Service erweitert](../REQ-011-Serviceauswahl-Erweitert/requirement.md), [REQ-012 Hinweise erweitert](../REQ-012-Hinweisseite-Erweitert/requirement.md) | Kategorien/Unteroptionen und Extras |
| [REQ-013 Branch/Marke](../REQ-013-Branch-Marke-Tausch/requirement.md) | Sieben-Schritte-Konfiguration, echter Standort-Einstieg, Breadcrumb-Reset, frühere VIN-Entfernung |
| [REQ-014 NestJS](../REQ-014-NestJS-Backend/requirement.md), [REQ-015 API-Integration](../REQ-015-API-Data-Integration/requirement.md) | Backend-Historie, Live-Daten, bewusst entfernter App-Header/Footer |
| [REQ-016 Lookup](../REQ-016-User-Lookup/requirement.md), [REQ-017 Loading](../REQ-017-Loading-Spinner/requirement.md) | Verhindert erfundene Lookup-/Ladefunktionalität aus alten Entwürfen; vorhandenes Fehler-/Spinnerverhalten beachten |
| [REQ-018 Spring Boot](../REQ-018-Spring-Boot-Migration/requirement.md), [REQ-019 Deployment](../REQ-019-Deployment/requirement.md), [REQ-020 Azure POC](../REQ-020-VorlaeufigDeployment/requirement.md) | Tatsächliche Backend-/Deployment-Abgrenzung; keine Infrastrukturänderung |
| [REQ-021 Multi-Branch](../REQ-021-Multi-Branch-Setup/requirement.md), [REQ-022 Azure-Alternative](../REQ-022-Azure-Alternative/requirement.md) | Branch-/Group-Kontext, mobile Navigation und Hosting-Abgrenzung |
| [REQ-023 E2E](../REQ-023-E2E-Test-Coverage/requirement.md), [REQ-024 Fehlerseite](../REQ-024-Error-Fallback-Screen/requirement.md) | Testfälle, Deep-Link-/Embedding-/Error-Regressionen |
| [REQ-025 API-Content](../REQ-025-API-Driven-Content/requirement.md), [REQ-026 Bootstrap](../REQ-026-Bootstrap-UI-Migration/requirement.md) | Maßgeblicher aktueller UI-Stack, Categories/Operations, kein Material-Rückbau |
| [REQ-027 Benachrichtigung](../REQ-027-Notification-Channel-Toggle/requirement.md), [REQ-028 Bestätigung](../REQ-028-Buchungsbestaetigung/requirement.md) | Notes-Kanal und bestehender Erfolg auf Übersicht |
| [REQ-029 Backend-Logging](../REQ-029-Backend-Error-Logging/requirement.md), [REQ-030 Fehlerreport](../REQ-030-AI-Error-Report/requirement.md) | Kein zusätzlicher Logging-/Reporting-Auftrag durch responsive Darstellung |
| [REQ-031 Booking-Bugfixes](../REQ-031-Booking-Bugfixes/requirement.md) | HTTP 410, Kilometerstand ab 10, Kalender-/Zeitzonenregression |
| [REQ-033 Telefonnummer](../REQ-033-Telefonnummer-Normalisierung/requirement.md), [REQ-034 Datensparsames Logging](../REQ-034-Datensparsames-Logging/requirement.md) | Bestehende Telefonvalidierung und Datenschutz unberührt |
| [REQ-035 Radwechsel](../REQ-035-Radwechsel-Terminsuche/spec.md), [REQ-036 Mietwagen-Feld](../REQ-036-Mietwagen-Kategorie-Feld/spec.md) | Beide vorhandenen `spec.md` konsultiert; keine Rückänderung an Varianten-/Mietwagen-Mapping |
| [REQ-037 Nutzungsstatistik](../REQ-037-Nutzungsstatistik/requirement.md), [REQ-038 Statistik-Dashboard](../REQ-038-Statistik-Dashboard/requirement.md) | Abgrenzung zur internen Statusseite; keine neue Nutzer-/Schrittverfolgung |

**REQ-032:** [REQ-032-AKS-Migration](../REQ-032-AKS-Migration/) existiert als leerer Ordner; zum Analysezeitpunkt ist darin kein Requirement-/Spec-Inhalt vorhanden, der gelesen oder als Abhängigkeit übernommen werden könnte.

**Archiv zusätzlich konsultiert:** [Ablaufkomplett](../Ablaufkomplett/requirement.md) sowie dessen [Service](../Ablaufkomplett/REQ-004-Serviceauswahl/requirement.md), [Termin](../Ablaufkomplett/REQ-005-Terminauswahl/requirement.md), [Kundendaten](../Ablaufkomplett/REQ-006-Kundendaten/requirement.md), [Bemerkungen](../Ablaufkomplett/REQ-007-Bemerkungen/requirement.md), [Übersicht](../Ablaufkomplett/REQ-008-Uebersicht/requirement.md) und [Datenschutz](../Ablaufkomplett/REQ-009-Datenschutz/requirement.md). Diese sieben historischen Dokumente beschreiben frühere Click-Dummy-/Acht-Schritte-Flows und sind nicht die Grundlage für eine neue Schrittfolge.

**Registry-Befund:** [REQUIREMENTS.md](../REQUIREMENTS.md) führt einen älteren Stand bis REQ-022 und teils veraltete Design-/Header-/Code-Sprachhinweise. Die Liste wurde konsultiert, aber nicht geändert. Die Board-Synchronisation und fachliche Freigabe bleiben außerhalb des exklusiven Dokumentations-Schreibbereichs von Agent 2.
