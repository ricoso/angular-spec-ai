import { CircuitBreakerService, CircuitState } from './circuit-breaker.service';

describe('CircuitBreakerService', () => {
  let service: CircuitBreakerService;

  beforeEach(() => {
    service = new CircuitBreakerService();
  });

  it('should start in CLOSED state', () => {
    expect(service.getState()).toBe(CircuitState.CLOSED);
    expect(service.isOpen()).toBe(false);
  });

  it('should stay CLOSED on success', () => {
    service.recordSuccess();
    expect(service.getState()).toBe(CircuitState.CLOSED);
  });

  it('should stay CLOSED below failure threshold', () => {
    for (let i = 0; i < 4; i++) {
      service.recordFailure();
    }
    expect(service.getState()).toBe(CircuitState.CLOSED);
    expect(service.isOpen()).toBe(false);
  });

  it('should transition to OPEN after 5 failures', () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailure();
    }
    expect(service.getState()).toBe(CircuitState.OPEN);
    expect(service.isOpen()).toBe(true);
  });

  it('should transition to HALF_OPEN after reset timeout', () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailure();
    }
    expect(service.getState()).toBe(CircuitState.OPEN);

    // Simulate time passing (30s)
    jest.spyOn(Date, 'now').mockReturnValue(Date.now() + 31_000);
    expect(service.getState()).toBe(CircuitState.HALF_OPEN);
  });

  it('should return to CLOSED on success in HALF_OPEN', () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailure();
    }
    jest.spyOn(Date, 'now').mockReturnValue(Date.now() + 31_000);
    service.getState(); // trigger transition to HALF_OPEN

    service.recordSuccess();
    expect(service.getState()).toBe(CircuitState.CLOSED);
  });

  it('should reopen after max half-open attempts fail', () => {
    for (let i = 0; i < 5; i++) {
      service.recordFailure();
    }

    const futureTime = Date.now() + 31_000;
    jest.spyOn(Date, 'now').mockReturnValue(futureTime);
    service.getState(); // trigger HALF_OPEN

    service.recordFailure();
    service.recordFailure();
    expect(service.getState()).toBe(CircuitState.OPEN);
  });

  it('should reset failure count on success', () => {
    service.recordFailure();
    service.recordFailure();
    service.recordSuccess();

    const status = service.getStatus();
    expect(status.failureCount).toBe(0);
    expect(status.state).toBe(CircuitState.CLOSED);
  });

  it('should return status with all fields', () => {
    const status = service.getStatus();
    expect(status).toEqual({
      state: CircuitState.CLOSED,
      failureCount: 0,
      lastFailureTime: 0,
    });
  });
});
