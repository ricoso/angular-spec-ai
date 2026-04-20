import type { Config } from 'jest';

const config: Config = {
  preset: 'jest-preset-angular',
  testEnvironment: 'jest-preset-angular/environments/jest-jsdom-env',
  setupFilesAfterEnv: ['<rootDir>/src/setup-jest.ts'],
  testPathIgnorePatterns: ['/node_modules/', '/dist/', '/playwright/'],
  coverageDirectory: 'coverage',
  collectCoverageFrom: [
    'src/app/**/*.ts',
    '!src/app/**/*.spec.ts',
    '!src/app/**/*.module.ts',
    '!src/app/**/*.routes.ts',
    '!src/app/**/models/**',
  ],
  coverageThreshold: {
    global: {
      statements: 80,
      branches: 75,
      functions: 75,
      lines: 80,
    },
  },
  moduleNameMapper: {
    '^@angular/common/http/testing$': '<rootDir>/node_modules/@angular/common/fesm2022/http-testing.mjs',
    '^@angular/common/http$': '<rootDir>/node_modules/@angular/common/fesm2022/http.mjs',
    '^@angular/platform-browser/animations/async$': '<rootDir>/node_modules/@angular/platform-browser/fesm2022/animations-async.mjs',
    '^@angular/platform-browser/animations$': '<rootDir>/node_modules/@angular/platform-browser/fesm2022/animations.mjs',
    '^@angular/material/(.+)$': '<rootDir>/node_modules/@angular/material/fesm2022/$1.mjs',
    '^@ngrx/signals/rxjs-interop$': '<rootDir>/node_modules/@ngrx/signals/fesm2022/ngrx-signals-rxjs-interop.mjs',
    '^@app/(.*)$': '<rootDir>/src/app/$1',
    '^@core/(.*)$': '<rootDir>/src/app/core/$1',
    '^@shared/(.*)$': '<rootDir>/src/app/shared/$1',
    '^@features/(.*)$': '<rootDir>/src/app/features/$1',
    '^@layouts/(.*)$': '<rootDir>/src/app/layouts/$1',
    '^@environments/(.*)$': '<rootDir>/src/environments/$1',
  },
  testMatch: ['**/*.spec.ts'],
  transform: {
    '^.+\\.(ts|js|mjs|html)$': [
      'jest-preset-angular',
      {
        tsconfig: '<rootDir>/tsconfig.spec.json',
        stringifyContentPathRegex: '\\.html$',
      },
    ],
  },
};

export default config;
