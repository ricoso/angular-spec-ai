import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for E2E tests WITH Spring Boot backend.
 * Backend serves Angular app on :3000 + proxies to SoftNet API.
 * Use: npm run e2e:backend
 */
export default defineConfig({
  testDir: './playwright',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright/reports' }],
    ['list']
  ],
  outputDir: 'playwright/test-results',

  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium-desktop',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 720 },
      },
    },
    {
      name: 'chromium-tablet',
      use: {
        ...devices['iPad (gen 7)'],
        viewport: { width: 768, height: 1024 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        ...devices['iPhone 13'],
        viewport: { width: 375, height: 667 },
      },
    },
  ],

  webServer: {
    command: 'cd server && ./gradlew bootRun',
    // REQ-037: fixed local credentials for the Basic-Auth protected status page
    // (/internal/report). Local/test only — production uses the Azure secret store.
    env: {
      REPORT_ADMIN_PASSWORD: process.env['REPORT_ADMIN_PASSWORD'] ?? 'e2e-local-secret',
    },
    url: 'http://localhost:3000/api/v1/health',
    reuseExistingServer: !process.env['CI'],
    timeout: 60_000,
  },
});
