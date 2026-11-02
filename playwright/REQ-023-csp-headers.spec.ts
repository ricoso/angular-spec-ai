import { expect, test } from '@playwright/test';

/**
 * REQ-023 / AC-6: CSP frame-ancestors header is set correctly.
 * Backend (`SecurityConfig.java`) injects CSP from `app.frame-ancestors` property.
 * Default: 'self' + https://www.gottfried-schultz.de.
 */

test.describe('REQ-023: CSP frame-ancestors header', () => {
  test('GET / returns Content-Security-Policy header with frame-ancestors directive', async ({ request }) => {
    const response = await request.get('/');
    expect(response.ok()).toBeTruthy();

    const csp = response.headers()['content-security-policy'];
    expect(csp).toBeTruthy();
    expect(csp).toContain('frame-ancestors');
  });

  test('frame-ancestors contains self and gottfried-schultz', async ({ request }) => {
    const response = await request.get('/');
    const csp = response.headers()['content-security-policy'] ?? '';

    expect(csp).toMatch(/frame-ancestors[^;]*'self'/);
    expect(csp).toMatch(/frame-ancestors[^;]*gottfried-schultz\.de/);
  });

  test('Health-Endpoint also delivers CSP header', async ({ request }) => {
    const response = await request.get('/api/v1/health');
    expect(response.ok()).toBeTruthy();
    const csp = response.headers()['content-security-policy'];
    expect(csp).toBeTruthy();
    expect(csp).toContain('frame-ancestors');
  });
});
