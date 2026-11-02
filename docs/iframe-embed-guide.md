# iframe-Embed-Guide für Partner-Webseiten (TYPO3, etc.)

**Stand:** 2026-04-29
**App-URL (Prod):** `https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io`

Dieser Guide beschreibt, wie ein Autohaus die Termin-Buchungs-App in seine eigene Webseite (TYPO3, WordPress, statisch) per `<iframe>` einbettet.

---

## 1. URL-Schema

Die App unterstützt **Deep-Link-Parameter** als Query-String, damit der iframe direkt zur richtigen Filiale + Marke springt:

| Parameter | Pflicht | Wert |
|-----------|---------|------|
| `branchId` | optional | SoftNet-Branch-ID (z. B. `68f606798d2bb4389ed1af51`) |
| `groupId` | optional | SoftNet-Group-ID der gewünschten Marke / Variant |
| `lang` | optional | `de` oder `en` (Standard: `de`) |

**Beispiele:**

```
# Audi Mettmann (default)
https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?branchId=68f606798d2bb4389ed1af51&groupId=6908807454f4bb2b69b03fde

# Volkswagen Mettmann (default)
https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?branchId=68f606f38d2bb4389ed2f0ba&groupId=690880ab23dcaf88065a037e
```

Eine vollständige Liste aller `branchId`/`groupId`-Kombinationen findet sich in
[Names_and_IDs.xlsx](../Names_and_IDs.xlsx) (Sheet `Sheet1`).

---

## 2. Minimal-Embed (HTML)

```html
<iframe
  src="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?branchId=68f606798d2bb4389ed1af51&groupId=6908807454f4bb2b69b03fde"
  title="Termin-Buchung"
  width="100%"
  style="border: 0; min-height: 900px;"
  allow="clipboard-write"
  loading="lazy"
></iframe>
```

**Wichtig:**
- `min-height: 900px` — die App enthält den kompletten Wizard, kürzer wird das Layout abgeschnitten.
- `width: 100%` — die App ist responsive, passt sich der Container-Breite an.
- `allow="clipboard-write"` — wird gebraucht falls der User die Termin-Bestätigung kopieren möchte.

---

## 3. Sandbox-Empfehlung

**Empfohlen** — moderne Browser-Sandboxing aktivieren ohne die App zu brechen:

```html
<iframe
  src="..."
  sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
  ...
></iframe>
```

| Token | Warum |
|-------|-------|
| `allow-scripts` | Pflicht — Angular braucht JS |
| `allow-same-origin` | Pflicht — sonst kein localStorage / sessionStorage / API-Calls |
| `allow-forms` | Pflicht — der Wizard nutzt Reactive Forms |
| `allow-popups` | optional — falls Bestätigungs-Mail-Links geöffnet werden |

**Nicht setzen:** `allow-top-navigation` (sicherheitskritisch, App braucht es nicht).

---

## 4. CSP-Whitelist auf der App-Seite

Die App setzt einen **`Content-Security-Policy: frame-ancestors`** Header. Nur die dort eingetragenen Hosts dürfen die App per iframe einbetten — alle anderen sehen nur einen leeren Bereich + Console-Error im Browser.

**Aktuelle Default-Whitelist:**

```
'self' https://www.gottfried-schultz.de https://gottfried-schultz.de
```

**Neuen Kunden-Host hinzufügen:** Env-Variable `APP_FRAME_ANCESTORS` im Container-App setzen — Werte werden mit Leerzeichen oder Komma getrennt:

```bash
# Beispiel: Schultz + ein TYPO3-Kunden-Host
az containerapp update \
  -n gs-terminbuchungs-app \
  -g DefaultResourceGroup-DEWC \
  --set-env-vars "APP_FRAME_ANCESTORS='self' https://www.gottfried-schultz.de https://gottfried-schultz.de https://www.kundenautohaus.de"
```

Wildcard-Subdomains werden unterstützt:

```
APP_FRAME_ANCESTORS="'self' https://*.kundenautohaus.de https://*.partner-typo3.de"
```

---

## 5. TYPO3-Plugin-Snippet

Wenn eine TYPO3-Custom-Extension den iframe einbettet (z. B. `tx_cardealermanagement_pi2`), sollte der Fluid-Template-Output etwa so aussehen:

```html
<f:if condition="{branch.softnetBranchId}">
  <div class="termin-buchung" style="margin: 2em 0;">
    <iframe
      src="https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/home/location?branchId={branch.softnetBranchId}&groupId={branch.softnetGroupId}"
      title="Termin online buchen"
      width="100%"
      style="border: 0; min-height: 900px; display: block;"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      loading="lazy"
    ></iframe>
  </div>
</f:if>
```

**Wichtig:** Die `softnetBranchId` und `softnetGroupId` müssen im TYPO3-Backend pro Filiale gepflegt werden. ID-Liste siehe [`Names_and_IDs.xlsx`](../Names_and_IDs.xlsx).

---

## 6. Troubleshooting

### Iframe ist leer / grau

**Ursache:** CSP `frame-ancestors` blockt den Host.

**Diagnose:** Browser-DevTools (F12) → Console → suche nach:

```
Refused to frame 'https://gs-terminbuchungs-app...' because an ancestor violates the Content Security Policy directive: "frame-ancestors 'self' https://www.gottfried-schultz.de"
```

**Fix:** `APP_FRAME_ANCESTORS` Env-Var um den Kunden-Host erweitern (siehe §4).

### API-Calls schlagen fehl mit 403

**Ursache:** Origin-Filter blockt API-Requests.

**Diagnose:** F12 → Network-Tab → `/api/v1/...` Requests → Response zeigt `error.unauthorizedOrigin`.

**Fix:** `CORS_ALLOWED_ORIGINS` Env-Var um den Kunden-Host erweitern:

```bash
az containerapp update \
  -n gs-terminbuchungs-app \
  -g DefaultResourceGroup-DEWC \
  --set-env-vars "CORS_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:3000,https://www.kundenautohaus.de"
```

### Iframe wird abgeschnitten

**Ursache:** TYPO3-Container hat eine feste max-height oder overflow:hidden.

**Fix:** Der iframe-Container braucht `min-height: 900px;` und kein `overflow:hidden`. Der iframe selbst sollte `height: auto` oder eine fixe `min-height` haben.

### LocalStorage funktioniert nicht im iframe

**Ursache:** Browser blockiert Third-Party-Storage (Safari, Brave, Firefox-Strict).

**Fix:** Sandbox-Attribut `allow-same-origin` setzen. Falls Browser trotzdem blockt, kann der Nutzer die Site als "vertrauenswürdig" markieren.

### Mixed Content Warning

**Ursache:** TYPO3-Seite läuft über HTTP, App-iframe über HTTPS — Browser blockiert.

**Fix:** TYPO3-Seite muss zwingend HTTPS verwenden. HTTP-Embed funktioniert nicht.

---

## 7. Checkliste für neuen Kunden

Beim Onboarding eines neuen Autohaus-Kunden mit eigener TYPO3-Seite:

- [ ] Kunden-Host(s) sammeln (z. B. `https://www.kundenautohaus.de`, ggf. Staging)
- [ ] `APP_FRAME_ANCESTORS` Env-Var um neue Hosts erweitern
- [ ] `CORS_ALLOWED_ORIGINS` Env-Var um neue Hosts erweitern
- [ ] Container-App restart (`az containerapp update --revision-suffix ...`)
- [ ] `branchId` + `groupId` aus `Names_and_IDs.xlsx` ans Kunden-Team weitergeben
- [ ] Test-Embed bauen (siehe `docs/iframe-embed-test.html` als Vorlage)
- [ ] Mit Kunde DevTools-Console gemeinsam durchgehen → CSP/CORS-Fehler abnehmen

---

## 8. Multi-Tenant-Isolation

Mehrere Kunden können dieselbe App-Instanz parallel nutzen, ohne dass sich Daten vermischen:

| Mechanismus | Wie isoliert |
|-------------|--------------|
| **Wizard-State** (selectedBranch, selectedServices, ...) | Liegt nur im **RAM** des `BookingStore` Signal-Stores. Beim Page-Reload wird auf `/home/location` zurückgesetzt (`AppComponent.ngOnInit`). |
| **Backend** | Komplett **stateless** — keine Sessions, nur kurzlebige Auth-Tokens (max 20 min). |
| **Branch-Daten** | Cards/Operations/Hinweise/Mietwagen-Preise werden pro Request aus SoftNet geladen, basierend auf `branchId`/`groupId` aus dem Deep-Link. |
| **localStorage** | Enthält nur **User-Settings** (Sprache, Schriftgröße, Kontrast) — sind global gewollt, nicht tenant-spezifisch. |
| **Token-Auth** | Via Header `X-Session-Token`, nicht Cookie → keine Cross-Site-Cookie-Probleme. |
| **CSP-Nonce** | Jeder HTML-Response bekommt eine eigene `ngCspNonce` für inline-Scripts (anti-XSS). |

Cross-Site-iframe-Sicherheit:
- Browser blockiert Third-Party-Cookies → wir nutzen keine.
- localStorage ist same-origin-scoped → Parent-TYPO3-Seite kann nicht auf iframe-Daten zugreifen.
- Sandbox-Attribute (siehe §3) verhindern `top-navigation` und unerlaubte Pointer-Events.

**Branch-spezifische Persistierung in Zukunft?** Falls später z. B. „letzte Auswahl merken pro Filiale" gefragt ist, müsste der `STORAGE_KEY` mit einem `branchId`-Prefix versehen werden (z. B. `cart-state__68f6...`). Aktuell **nicht nötig**, da nichts branch-spezifisch persistiert wird.
