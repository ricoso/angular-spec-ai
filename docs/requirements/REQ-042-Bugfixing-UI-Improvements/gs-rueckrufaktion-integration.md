# Rückrufaktion – Einbindung in die GS-Website

**Für:** Webentwicklung/Webmaster Gottfried Schultz
**Betrifft:** Online-Terminvereinbarung (OTV), Einstieg „Rückrufaktion"
**Stand:** 2026-10-14

---

## Worum es geht

Wenn auf gottfried-schultz.de per FIN-Prüfung festgestellt wird, dass ein Fahrzeug von einer
Rückrufaktion betroffen ist, soll der Kunde direkt einen Werkstatttermin buchen können. Dazu
verlinkt ihr auf die Online-Terminvereinbarung mit zwei URL-Parametern. Die Terminvereinbarung
erkennt daran den Rückruf-Modus und führt den Kunden durch den passenden, verkürzten Ablauf.

**Euer Teil:** nur den Link mit der FIN erzeugen. Alles Weitere (passender Service, Ablauf,
Übernahme der FIN in die Buchung) übernimmt die Terminvereinbarung.

---

## Die URL

```
https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?recall=true&vin=<FIN>
```

### Parameter

| Parameter | Pflicht | Wert | Beschreibung |
|-----------|---------|------|--------------|
| `recall`  | ja | `true` | Aktiviert den Rückruf-Modus. Ohne diesen Parameter startet der normale Ablauf. |
| `vin`     | ja | 17-stellige FIN | Die Fahrgestellnummer des betroffenen Fahrzeugs. Wird im Buchungsformular vorbefüllt und mit der Buchung übergeben. |

**Hinweise zur FIN:**
- Genau **17 Zeichen**, nur Buchstaben und Ziffern.
- In der URL **URL-kodiert** anhängen (bei einer reinen FIN ohne Sonderzeichen unverändert).
- Ist die FIN ungültig oder fehlt sie, startet der Rückruf-Modus trotzdem; der Kunde gibt die FIN
  dann im Formular selbst ein (Pflichtfeld).

---

## Beispiel

FIN `WVWZZZ1KZAW000123`:

```
https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?recall=true&vin=WVWZZZ1KZAW000123
```

Als Button auf eurer „betroffen"-Seite:

```html
<a
  href="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?recall=true&vin=WVWZZZ1KZAW000123"
  class="button">
  Jetzt Termin für die Rückrufaktion buchen
</a>
```

Die FIN setzt ihr pro Fahrzeug dynamisch ein (Serverseite oder JavaScript), z. B.:

```js
const vin = encodeURIComponent(fahrzeug.fin);
const url = `https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?recall=true&vin=${vin}`;
```

---

## Was der Kunde dann sieht (zur Einordnung)

1. **Standort wählen** – mit Hinweis-Banner „Rückrufaktion" und der erkannten FIN. Standortsuche
   nach Stadt ist vorhanden.
2. **Marke wählen.**
3. **Service entfällt** – der Rückruf-Service ist fest hinterlegt und wird automatisch gesetzt.
4. **Hinweise** (Freitext/Optionen), **Termin wählen**, **Kundendaten** (FIN vorbefüllt + Pflicht),
   **Übersicht & Buchung**.

---

## Nicht Teil dieser Einbindung

- Die **FIN-Betroffenheitsprüfung** selbst (euer Formular/Backend) – die bleibt bei euch.
- Es werden **keine** weiteren Parameter oder Zugangsdaten benötigt. Der Link ist der komplette
  Übergabepunkt.
