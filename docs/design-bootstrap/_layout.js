/**
 * Layout-Layer für Bootstrap-Design-Demo:
 *  1) Accessibility-Offcanvas (Schriftgröße, Sprache, Kontrast, Animation)
 *  2) Warenkorb-Offcanvas (zeigt aktuellen Wizard-Stand)
 *
 * Wird auf jeder Page als <script src="_layout.js"></script> eingebunden,
 * NACH bootstrap.bundle.min.js.
 */
(function () {
  'use strict';

  // ----------------------------------------------------------
  // 1. Aktuelle Page erkennen → Cart "fortgeschritten bis Step X"
  // ----------------------------------------------------------
  const page = (window.location.pathname.split('/').pop() || '').toLowerCase();
  const stepIdx = {
    '01-location.html': 0,
    '02-brand.html': 1,
    '03-services.html': 2,
    '04-notes.html': 3,
    '05-appointment.html': 4,
    '06-overview.html': 5
  }[page];
  const reachedIdx = stepIdx === undefined ? -1 : stepIdx;

  // Demo-Cart-Daten (statisch, nur für Mockup)
  const STEPS = [
    { key: 'branch',      label: 'Filiale',          icon: 'geo-alt-fill',         value: 'Audi Mettmann',                   sub: 'Düsseldorfer Str. 189-191, 40822 Mettmann', edit: '01-location.html' },
    { key: 'brand',       label: 'Marke',            icon: 'tag-fill',             value: 'ŠKODA',                           sub: null,                                         edit: '02-brand.html' },
    { key: 'services',    label: 'Services',         icon: 'tools',                value: '2 Services',                      sub: 'Dialogannahme · Inspektion',                 edit: '03-services.html' },
    { key: 'extras',      label: 'Hinweise',         icon: 'car-front-fill',       value: 'Mietwagen Kleinwagen',            sub: 'Egal · Kein Rückruf',                        edit: '04-notes.html' },
    { key: 'appointment', label: 'Termin',           icon: 'calendar-check-fill',  value: 'Mi, 6. Mai 2026 — 10:30',         sub: null,                                         edit: '05-appointment.html' }
  ];

  const cartItems = STEPS.map((s, i) => {
    const reached = i <= reachedIdx;
    if (!reached) {
      return `
        <div class="cart-item">
          <span class="cart-item__icon cart-item__icon--empty"><i class="bi bi-${s.icon}"></i></span>
          <div class="flex-grow-1">
            <p class="cart-item__label mb-0">${s.label}</p>
            <p class="cart-item__value cart-item__value--empty mb-0">Noch nicht ausgewählt</p>
          </div>
        </div>`;
    }
    return `
      <div class="cart-item">
        <span class="cart-item__icon"><i class="bi bi-${s.icon}"></i></span>
        <div class="flex-grow-1">
          <p class="cart-item__label mb-0">${s.label}</p>
          <p class="cart-item__value mb-0">${s.value}</p>
          ${s.sub ? `<p class="cart-item__sub mb-0">${s.sub}</p>` : ''}
        </div>
        <a href="${s.edit}" class="btn btn-link btn-sm p-0 small text-decoration-none">Ändern</a>
      </div>`;
  }).join('');

  const filledCount = reachedIdx + 1;
  const cartBadge = filledCount > 0 ? filledCount : '';

  // ----------------------------------------------------------
  // 2. Cart-Offcanvas in DOM injizieren
  // ----------------------------------------------------------
  const cartOC = document.createElement('div');
  cartOC.className = 'offcanvas offcanvas-end';
  cartOC.id = 'cartPanel';
  cartOC.setAttribute('tabindex', '-1');
  cartOC.setAttribute('aria-labelledby', 'cartPanelTitle');
  cartOC.innerHTML = `
    <div class="offcanvas-header">
      <h5 class="offcanvas-title" id="cartPanelTitle"><i class="bi bi-bag-fill me-2"></i>Ihre Auswahl</h5>
      <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Schließen"></button>
    </div>
    <div class="offcanvas-body">
      ${cartItems}
      <div class="d-grid gap-2 mt-3">
        ${reachedIdx >= 4
          ? `<a href="06-overview.html" class="btn btn-primary"><i class="bi bi-check-circle me-2"></i>Zur Buchungs-Übersicht</a>`
          : `<a href="${STEPS[Math.min(reachedIdx + 1, STEPS.length - 1)].edit}" class="btn btn-primary">Weiter zum nächsten Schritt <i class="bi bi-arrow-right ms-1"></i></a>`
        }
      </div>
    </div>`;
  document.body.appendChild(cartOC);

  // Existierende .cart-fab → Offcanvas-Trigger + Badge
  document.querySelectorAll('.cart-fab').forEach(fab => {
    fab.setAttribute('data-bs-toggle', 'offcanvas');
    fab.setAttribute('data-bs-target', '#cartPanel');
    fab.setAttribute('aria-controls', 'cartPanel');
    if (cartBadge && !fab.querySelector('.badge')) {
      const b = document.createElement('span');
      b.className = 'badge bg-light text-dark position-absolute';
      b.style.cssText = 'top:-0.3em;right:-0.3em;font-size:0.7em';
      b.textContent = cartBadge;
      fab.prepend(b);
    }
  });

  // ----------------------------------------------------------
  // 3. Accessibility-FAB + Offcanvas
  // ----------------------------------------------------------
  const a11yBtn = document.createElement('button');
  a11yBtn.type = 'button';
  a11yBtn.className = 'a11y-fab';
  a11yBtn.setAttribute('aria-label', 'Zugänglichkeitsmenü öffnen');
  a11yBtn.setAttribute('title', 'Zugänglichkeit');
  a11yBtn.setAttribute('data-bs-toggle', 'offcanvas');
  a11yBtn.setAttribute('data-bs-target', '#a11yPanel');
  a11yBtn.setAttribute('aria-controls', 'a11yPanel');
  a11yBtn.innerHTML = '<i class="bi bi-universal-access" aria-hidden="true"></i>';
  document.body.appendChild(a11yBtn);

  const a11yOC = document.createElement('div');
  a11yOC.className = 'offcanvas offcanvas-start';
  a11yOC.id = 'a11yPanel';
  a11yOC.setAttribute('tabindex', '-1');
  a11yOC.setAttribute('aria-labelledby', 'a11yPanelTitle');
  a11yOC.innerHTML = `
    <div class="offcanvas-header">
      <h5 class="offcanvas-title" id="a11yPanelTitle"><i class="bi bi-universal-access me-2"></i>Zugänglichkeit</h5>
      <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Schließen"></button>
    </div>
    <div class="offcanvas-body">

      <h6 class="text-uppercase text-muted small mb-2">Schriftgröße</h6>
      <div class="pill-group mb-4" role="radiogroup" aria-label="Schriftgröße">
        <input type="radio" name="a11yFont" id="a11yFontS" value="small">
        <label for="a11yFontS"><span style="font-size:0.8em">A</span></label>
        <input type="radio" name="a11yFont" id="a11yFontM" value="medium" checked>
        <label for="a11yFontM"><span>A</span></label>
        <input type="radio" name="a11yFont" id="a11yFontL" value="large">
        <label for="a11yFontL"><span style="font-size:1.15em">A</span></label>
        <input type="radio" name="a11yFont" id="a11yFontXL" value="xlarge">
        <label for="a11yFontXL"><span style="font-size:1.3em">A</span></label>
      </div>

      <h6 class="text-uppercase text-muted small mb-2">Sprache</h6>
      <div class="pill-group mb-4" role="radiogroup" aria-label="Sprache">
        <input type="radio" name="a11yLang" id="a11yLangDe" value="de" checked>
        <label for="a11yLangDe">🇩🇪 Deutsch</label>
        <input type="radio" name="a11yLang" id="a11yLangEn" value="en">
        <label for="a11yLangEn">🇬🇧 English</label>
      </div>

      <h6 class="text-uppercase text-muted small mb-2">Hoher Kontrast</h6>
      <div class="form-check form-switch mb-4">
        <input class="form-check-input" type="checkbox" id="a11yHC">
        <label class="form-check-label" for="a11yHC">Schwarz/Weiß für mehr Lesbarkeit</label>
      </div>

      <h6 class="text-uppercase text-muted small mb-2">Bewegungen reduzieren</h6>
      <div class="form-check form-switch mb-4">
        <input class="form-check-input" type="checkbox" id="a11yRM">
        <label class="form-check-label" for="a11yRM">Animationen ausschalten</label>
      </div>

      <hr>
      <p class="small text-muted mb-0">
        <i class="bi bi-info-circle me-1"></i>Einstellungen gelten nur für diese Sitzung.
      </p>
    </div>`;
  document.body.appendChild(a11yOC);

  // ----------------------------------------------------------
  // 4. A11y-Handler
  // ----------------------------------------------------------
  a11yOC.querySelectorAll('input[name="a11yFont"]').forEach(el => {
    el.addEventListener('change', () => {
      document.body.classList.remove('font-small', 'font-large', 'font-xlarge');
      if (el.value === 'small')  document.body.classList.add('font-small');
      if (el.value === 'large')  document.body.classList.add('font-large');
      if (el.value === 'xlarge') document.body.classList.add('font-xlarge');
    });
  });

  document.getElementById('a11yHC').addEventListener('change', e => {
    document.body.classList.toggle('high-contrast', e.target.checked);
  });

  document.getElementById('a11yRM').addEventListener('change', e => {
    document.body.classList.toggle('reduce-motion', e.target.checked);
  });

  // Sprach-Pills (rein optisch — Translation würde hier einhaken)
  a11yOC.querySelectorAll('input[name="a11yLang"]').forEach(el => {
    el.addEventListener('change', () => {
      document.documentElement.lang = el.value;
    });
  });
})();
