import type { Page } from '@playwright/test';

/**
 * Shared helpers for E2E tests
 * Provides navigation and language utilities for the Angular SSR app
 */

/** Navigate to a route in the Angular app */
export async function navigateTo(page: Page, route: string): Promise<void> {
  await page.goto(route);
  await page.waitForLoadState('networkidle');
}

/** Set UI language via localStorage and reload. Navigates to app first if needed. */
export async function setLanguage(page: Page, lang: 'de' | 'en'): Promise<void> {
  // Ensure we're on a real page (not about:blank) before accessing localStorage
  const url = page.url();
  if (!url || url === 'about:blank') {
    await page.goto('/home/brand');
    await page.waitForLoadState('networkidle');
  }
  await page.evaluate((l) => localStorage.setItem('app-language', l), lang);
  await page.reload();
  await page.waitForLoadState('networkidle');
}

/**
 * Ensure DE language is active.
 * The app defaults to browser language (usually EN in test env).
 * Call this in beforeEach if tests assume German UI.
 */
export async function ensureGermanLanguage(page: Page): Promise<void> {
  await page.evaluate(() => localStorage.setItem('app-language', 'de'));
}

/** Get current URL path route */
export async function getCurrentRoute(page: Page): Promise<string> {
  const url = page.url();
  const pathname = new URL(url).pathname;
  return pathname || '/';
}

/** Wait for Angular to stabilize */
export async function waitForAngular(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  // Small extra wait for Angular change detection
  await page.waitForTimeout(300);
}

/** Take a named screenshot and save to the requirement's screenshots folder */
export async function saveScreenshot(
  page: Page,
  reqId: string,
  name: string
): Promise<void> {
  await page.screenshot({
    path: `docs/requirements/${reqId}/screenshots/${name}.png`,
    fullPage: true,
  });
}
