import { provideHttpClient } from '@angular/common/http';
import type { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClientTesting, HttpTestingController as Controller } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionTokenService } from './session-token.service';

describe('SessionTokenService', () => {
  let service: SessionTokenService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionTokenService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(SessionTokenService);
    httpMock = TestBed.inject(Controller);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getToken returns null initially and triggers refresh', () => {
    const result = service.getToken();
    expect(result).toBeNull();
    const req = httpMock.expectOne('/api/v1/session/token');
    expect(req.request.method).toBe('GET');
    req.flush({
      data: { token: 'abc', expiresAt: new Date(Date.now() + 60_000).toISOString() },
      meta: { timestamp: '', requestId: '' }
    });
  });

  it('ensureToken resolves with cached token if still valid', async () => {
    const expires = new Date(Date.now() + 60_000).toISOString();
    const promise = service.ensureToken();
    const req = httpMock.expectOne('/api/v1/session/token');
    req.flush({
      data: { token: 'first', expiresAt: expires },
      meta: { timestamp: '', requestId: '' }
    });
    await expect(promise).resolves.toBe('first');

    // Second call should NOT trigger another request
    await expect(service.ensureToken()).resolves.toBe('first');
    httpMock.expectNone('/api/v1/session/token');
  });

  it('clearToken removes the cached token', async () => {
    const expires = new Date(Date.now() + 60_000).toISOString();
    const promise = service.ensureToken();
    httpMock.expectOne('/api/v1/session/token').flush({
      data: { token: 't1', expiresAt: expires },
      meta: { timestamp: '', requestId: '' }
    });
    await promise;

    service.clearToken();

    // Trigger fetch again — token should be requested
    const promise2 = service.ensureToken();
    httpMock.expectOne('/api/v1/session/token').flush({
      data: { token: 't2', expiresAt: expires },
      meta: { timestamp: '', requestId: '' }
    });
    await expect(promise2).resolves.toBe('t2');
  });

  it('refreshToken rejects on HTTP error', async () => {
    const promise = service.ensureToken();
    httpMock.expectOne('/api/v1/session/token').error(
      new ProgressEvent('error'),
      { status: 500, statusText: 'Server Error' }
    );
    await expect(promise).rejects.toBeDefined();
  });
});
