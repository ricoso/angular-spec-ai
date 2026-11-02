import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import type { HttpTestingController } from '@angular/common/http/testing';
import { HttpTestingController as Controller,provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { sessionTokenInterceptor } from './session-token.interceptor';
import { SessionTokenService } from './session-token.service';
import { TraceService } from './trace.service';

describe('sessionTokenInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let sessionTokenService: jest.Mocked<SessionTokenService>;

  beforeEach(() => {
    sessionTokenService = {
      ensureToken: jest.fn().mockResolvedValue('test-token'),
      getToken: jest.fn().mockReturnValue('test-token'),
      clearToken: jest.fn()
    } as unknown as jest.Mocked<SessionTokenService>;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionTokenInterceptor])),
        provideHttpClientTesting(),
        TraceService,
        { provide: SessionTokenService, useValue: sessionTokenService }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(Controller);
  });

  afterEach(() => { httpMock.verify(); });

  it('should pass through non-/api/ URLs without token header', () => {
    http.get('/static/foo.png').subscribe();
    const req = httpMock.expectOne('/static/foo.png');
    expect(req.request.headers.get('X-Session-Token')).toBeNull();
    expect(sessionTokenService.ensureToken).not.toHaveBeenCalled();
    req.flush({});
  });

  it('should skip token-fetch endpoint to avoid recursion', () => {
    http.get('/api/v1/session/token').subscribe();
    const req = httpMock.expectOne('/api/v1/session/token');
    expect(sessionTokenService.ensureToken).not.toHaveBeenCalled();
    req.flush({});
  });

  it('should attach X-Session-Token, X-Correlation-Id, X-Session-Id headers on /api/ calls', async () => {
    const promise = new Promise<void>(resolve => {
      http.get('/api/v1/branches').subscribe(() => { resolve(); });
    });
    // Wait for token-promise to resolve
    await Promise.resolve();
    await Promise.resolve();
    const req = httpMock.expectOne('/api/v1/branches');
    expect(req.request.headers.get('X-Session-Token')).toBe('test-token');
    expect(req.request.headers.get('X-Correlation-Id')).toMatch(/^[a-f0-9]+-/);
    expect(req.request.headers.get('X-Session-Id')).toBeTruthy();
    req.flush({});
    await promise;
  });

  it('should propagate request errors with trace logging', async () => {
    const subPromise = new Promise<unknown>(resolve => {
      http.get('/api/v1/fail').subscribe({ error: e => { resolve(e); } });
    });
    await Promise.resolve();
    await Promise.resolve();
    const req = httpMock.expectOne('/api/v1/fail');
    req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    const err = await subPromise;
    expect(err).toBeDefined();
  });
});
