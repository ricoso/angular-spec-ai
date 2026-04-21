import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { circuitBreakerInterceptor } from './circuit-breaker.interceptor';
import { CircuitBreakerService, CircuitState } from './circuit-breaker.service';

describe('circuitBreakerInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let circuitBreaker: CircuitBreakerService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([circuitBreakerInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    circuitBreaker = TestBed.inject(CircuitBreakerService);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should pass through non-API requests', () => {
    http.get('/assets/i18n/de.json').subscribe();
    const req = httpTesting.expectOne('/assets/i18n/de.json');
    req.flush({});
    expect(circuitBreaker.getState()).toBe(CircuitState.CLOSED);
  });

  it('should record success for API calls', () => {
    http.get('/api/v1/branches').subscribe();
    const req = httpTesting.expectOne('/api/v1/branches');
    req.flush([]);
    expect(circuitBreaker.getStatus().failureCount).toBe(0);
  });

  it('should record failure on 500 errors', () => {
    http.get('/api/v1/branches').subscribe({ error: () => { /* expected */ } });
    const req = httpTesting.expectOne('/api/v1/branches');
    req.flush('error', { status: 500, statusText: 'Internal Server Error' });
    expect(circuitBreaker.getStatus().failureCount).toBe(1);
  });

  it('should NOT record failure on 4xx errors', () => {
    http.get('/api/v1/branches').subscribe({ error: () => { /* expected */ } });
    const req = httpTesting.expectOne('/api/v1/branches');
    req.flush('not found', { status: 404, statusText: 'Not Found' });
    expect(circuitBreaker.getStatus().failureCount).toBe(0);
  });

  it('should block requests when circuit is OPEN', () => {
    // Force circuit open
    for (let i = 0; i < 5; i++) {
      circuitBreaker.recordFailure();
    }
    expect(circuitBreaker.isOpen()).toBe(true);

    let errorResponse: unknown;
    http.get('/api/v1/branches').subscribe({
      error: (err: unknown) => { errorResponse = err; },
    });

    httpTesting.expectNone('/api/v1/branches');
    expect(errorResponse).toBeDefined();
  });
});
