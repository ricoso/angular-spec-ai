# Feature-Dokumentation: Telefonnummer-Normalisierung (SMS-Format)

**Erstellt:** 2026-08-03
**Requirement:** REQ-033-Telefonnummer-Normalisierung
**Sprache:** DE
**Status:** Implementiert

---

## Übersicht

Kunden geben im Buchungs-Wizard (Wizard-Schritt 6, `/home/carinformation`) ihre Mobilfunknummer entweder mit führender `0` (z. B. `017012345678`) oder bereits mit `+49`-Präfix (z. B. `+491701234567`) ein — beide Formate werden vom Formular akzeptiert. Bislang wurde diese Rohform unverändert bis zu SoftNet durchgereicht. SoftNet — der externe Dienstleister, der u. a. den SMS-Versand der Terminbestätigung übernimmt — verarbeitet Nummern mit führender `0` für den SMS-Versand jedoch nicht zuverlässig. Das Ergebnis war ein **silent failure**: kein Fehler, keine Exception, aber die SMS kam beim Kunden nicht an.

Dieses Feature führt eine rein interne, backend-seitige Normalisierung ein: Bevor die Nummer an SoftNet gesendet wird, wird eine führende `0` durch `+49` ersetzt. Beginnt die Nummer bereits mit `+`, bleibt sie unverändert (kein Doppel-Prefixing). Der Benutzer sieht im Formular weiterhin exakt das, was er eingegeben hat — es gibt keine sichtbare UI-Änderung, keine neue Fehlermeldung und keine Änderung an der bestehenden Frontend-Validierung.

---

## Benutzerführung

n/a — reine Backend-Änderung ohne sichtbare UI-Änderung. Das Mockup (`mockup.html`/`mockup.png`) zeigt zur Einordnung den bestehenden Hint-Text am Mobilfunknummer-Feld, der beide Formate erklärt — an dieser Anzeige ändert sich durch dieses Requirement nichts.

---

## Funktionsweise (System-Flow)

### Schritt 1: Kunde gibt Mobilfunknummer ein

**System:** Das Formular validiert wie bisher gegen `^(\+|0)[0-9\s]+$` — unverändert.

### Schritt 2: Buchung wird abgesendet

**System:** `BookingStore.submitBooking()` übernimmt `mobile`/`phone` unverändert aus `customerInfo` in den Request an das Backend — keine Normalisierung im Frontend.

### Schritt 3 (NEU): Backend normalisiert vor dem SoftNet-Call

**System:** `AppointmentsService.patchSnapWithBookingData()` ruft für `mobile` und `phone` jeweils `PhoneNumberNormalizer.toSoftnetFormat(...)` auf, **bevor** die Werte in `wishlist.client.mobile`/`.phone` geschrieben werden.

**Ergebnis:** SoftNet erhält die Nummer stets im Format `+49…`, unabhängig vom Eingabeformat des Kunden.

### Schritt 4: SoftNet verarbeitet die Nummer zuverlässig

**Ergebnis:** Der SMS-Versand funktioniert wie erwartet — ohne dass der Kunde etwas davon bemerkt.

### Normalisierungsregel (exakt)

| Eingabe (Beispiel) | Ausgabe an SoftNet | Regel |
|---------------------|---------------------|-------|
| `017012345678` | `+4917012345678` | Führende `0` → `+49`, Rest unverändert |
| `0170 123 45678` | `+49170 123 45678` | Nur die führende `0` wird ersetzt — enthaltene Leerzeichen bleiben erhalten |
| `+491701234567` | `+491701234567` | Bereits `+`-Präfix → unverändert (kein Doppel-Prefixing) |
| `null` / leer / kein `0`/`+`-Präfix | unverändert zurückgegeben | Defensiv — kein Crash, keine Exception |

---

## Responsive Ansichten

n/a — Backend-Feature ohne UI-Änderung.

---

## Barrierefreiheit

n/a — Backend-Feature ohne UI-Änderung.

---

## Technische Details

| Eigenschaft | Wert |
|-------------|------|
| Route | n/a — keine Frontend-Änderung |
| Container Component | n/a — keine Frontend-Änderung |
| Store | n/a — keine Frontend-Änderung |
| API Service | n/a — kein Frontend-Service betroffen |

**Neue/geänderte Backend-Klassen** (`server/src/main/java/com/autohaus/backend/`):

| Klasse/Datei | Änderung |
|--------------|----------|
| `common/util/PhoneNumberNormalizer.java` | **Neu** — statische Methode `toSoftnetFormat(String)`: führende `0` → `+49`, `+`-Präfix unverändert, sonst defensiv unverändert |
| `appointments/AppointmentsService.java` | `patchSnapWithBookingData()`: `client.mobile`/`client.phone` werden vor dem Schreiben normalisiert |
| `customers/CustomersService.java` | `createCustomer()`: `data.mobilePhone` wird vor dem Schreiben normalisiert (Konsistenz — `vaultitems`-Endpunkt aktuell nicht im aktiven Buchungsfluss genutzt) |

**Verwandte Dokumente:**
- Requirement: [`requirement.md`](./requirement.md)
- Qualitätsbericht: [`qualitaets.md`](./qualitaets.md)
