import { HttpClient, HttpHeaders,provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppErrorStore } from './app-error.store';
import { _resetAuthLoopCounter,errorFallbackInterceptor } from './error-fallback.interceptor';

describe('errorFallbackInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let store: InstanceType<typeof AppErrorStore>;

  beforeEach(() => {
    _resetAuthLoopCounter();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorFallbackInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AppErrorStore);
  });

  afterEach(() => { httpMock.verify(); });

  it('should escalate 503 to SERVER_5XX', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('down', { status: 503, statusText: 'Service Unavailable' });
    const err = store.criticalError();
    expect(err?.code).toBe('SERVER_5XX');
    expect(err?.httpStatus).toBe(503);
  });

  it('should escalate status 0 to NETWORK', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown' });
    expect(store.criticalError()?.code).toBe('NETWORK');
  });

  it('should NOT escalate single 401', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('unauth', { status: 401, statusText: 'Unauthorized' });
    expect(store.criticalError()).toBeNull();
  });

  it('should escalate after 3 consecutive 401 responses', () => {
    for (let i = 0; i < 3; i++) {
      http.get('/api/test').subscribe({ error: () => undefined });
      httpMock.expectOne('/api/test').flush('unauth', { status: 401, statusText: 'Unauthorized' });
    }
    expect(store.criticalError()?.code).toBe('AUTH_LOOP');
  });

  it('should reset 401 counter on non-401 response', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('unauth', { status: 401, statusText: 'Unauthorized' });

    http.get('/api/ok').subscribe();
    httpMock.expectOne('/api/ok').flush({ ok: true });

    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('unauth', { status: 401, statusText: 'Unauthorized' });
    expect(store.criticalError()).toBeNull();
  });

  it('should NOT escalate 4xx (other than 401)', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('not found', { status: 404, statusText: 'Not Found' });
    expect(store.criticalError()).toBeNull();
  });

  it('should pick up requestId from headers', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush('down', {
      status: 503,
      statusText: 'down',
      headers: new HttpHeaders({ 'X-Request-Id': 'abc-123' })
    });
    expect(store.criticalError()?.requestId).toBe('abc-123');
  });

  it('should pick up requestId from JSON body when header missing', () => {
    http.get('/api/test').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/test').flush(
      { requestId: 'body-456' },
      { status: 503, statusText: 'down' }
    );
    expect(store.criticalError()?.requestId).toBe('body-456');
  });
});
