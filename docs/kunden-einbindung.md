# Einbindung der Terminbuchung auf Ihrer Webseite

Die Terminbuchung wird per `<iframe>` auf einer beliebigen Seite Ihrer Webseite eingebettet. Der Kunde bleibt im Look-and-Feel Ihrer Seite; die Buchung selbst läuft auf unserem Server.

---

## 1. Voraussetzung: Ihre Domain ist freigegeben

Wir erlauben das Einbetten nur von vorab freigegebenen Domains (Schutz vor Clickjacking, CSP `frame-ancestors`). Aktuell freigegeben:

- `https://www.gottfried-schultz.de`

Soll die Terminbuchung auf einer anderen Domain (z. B. Subdomain, Filial-Seite, Staging) eingebettet werden, bitte bei uns melden — wir ergänzen die Domain in der Konfiguration.

---

## 2. Minimales iframe-Snippet

Volle Buchungsstrecke mit Filialauswahl zu Beginn:

```html
<iframe
  src="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location"
  title="Termin buchen"
  style="width: 100%; height: 900px; border: 0;"
  loading="lazy">
</iframe>
```

- `width: 100%` — passt sich Ihrem Container an
- `height: 900px` — reicht für alle Schritte ohne inneres Scrollen (bei Desktop); auf Mobile scrollt der iframe
- `loading="lazy"` — lädt den iframe erst, wenn er in den Viewport kommt (Performance)

---

## 3. Filiale direkt vorwählen (Deep-Link)

Wenn die Einbettung auf einer filialspezifischen Unterseite erfolgt, kann die Filiale per URL-Parameter direkt gesetzt werden. Der Kunde überspringt dann die Filialauswahl und startet bei der Markenauswahl.

**Es werden zwei Parameter benötigt: `branchId` und `groupId`** — der Deep-Link-Guard validiert beide gegen das Backend, bevor er die Filialauswahl überspringt. Pro Filiale gibt es zwei Group-IDs (Standard-Service + Reifenwechsel) — beide finden Sie in der mitgelieferten Excel-Tabelle `Names_and_IDs.xlsx`, jeweils zusammen mit der fertigen iframe-URL in der Spalte `iframe-URL`.

```html
<!-- Beispiel: Audi Mettmann (Standard-Service) -->
<iframe
  src="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?branchId=68f606798d2bb4389ed1af51&groupId=6908807454f4bb2b69b03fde"
  title="Termin Audi Mettmann"
  style="width: 100%; height: 900px; border: 0;"
  loading="lazy">
</iframe>
```

Pro Filiale also bis zu zwei iframe-URLs in der Excel: eine für Standard, eine für Reifenwechsel. Beide haben die selbe `branchId`, aber unterschiedliche `groupId`.

---

## 4. Responsive Höhe (empfohlen)

Damit der iframe auf Mobile nicht zu kurz und auf Desktop nicht zu hoch ist:

```html
<style>
  .booking-frame {
    width: 100%;
    border: 0;
    display: block;
    height: 900px;            /* Desktop */
  }
  @media (max-width: 768px) {
    .booking-frame { height: 1200px; }   /* Mobile: mehr Höhe, da Elemente gestapelt */
  }
</style>

<iframe
  class="booking-frame"
  src="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location"
  title="Termin buchen"
  loading="lazy">
</iframe>
```

---

## 5. Als Overlay / Modal (optional)

Wenn die Buchung nicht auf der Seite selbst, sondern in einem Overlay (Lightbox) geöffnet werden soll, kann ein einfacher „Termin buchen"-Button gesetzt werden, der den iframe in einem modalen Dialog zeigt. Ein Beispiel-HTML dafür liegt als `iframe-embed-test.html` in unserem Repo und kann auf Wunsch angepasst werden.

---

## 6. Was der iframe mitbringt

- Responsives Layout (Mobile + Desktop)
- Schritt-für-Schritt-Wizard: Filiale → Marke → Leistungen → Hinweise → Termin → Kundendaten → Übersicht
- Validierte Formulare (Mobilnummer `+49…` und `017…`, E-Mail, Postleitzahl etc.)
- Buchung läuft direkt gegen das SoftNet-Backend

---

## 7. Kontakt bei Problemen

Bei Problemen mit der Einbettung (CSP-Fehler, Layout, Filial-IDs):
- Exakter Fehlertext aus der Browser-Console
- Screenshot des iframe-Containers
- URL der einbettenden Seite

an das Entwicklungsteam senden.
