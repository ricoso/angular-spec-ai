import { TestBed } from '@angular/core/testing';

import { TraceService } from './trace.service';

describe('TraceService', () => {
  let service: TraceService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [TraceService] });
    service = TestBed.inject(TraceService);
  });

  it('should be created and have a sessionId', () => {
    expect(service).toBeTruthy();
    expect(typeof service.sessionId).toBe('string');
    expect(service.sessionId.length).toBeGreaterThan(0);
  });

  it('createCorrelationId increments and pads counter', () => {
    const a = service.createCorrelationId();
    const b = service.createCorrelationId();
    expect(a).toMatch(/^[a-f0-9]+-0001$/);
    expect(b).toMatch(/^[a-f0-9]+-0002$/);
  });

  it('log() does not throw, with and without data', () => {
    expect(() => service.log('Component', 'msg')).not.toThrow();
    expect(() => service.log('Component', 'msg', { foo: 'bar' })).not.toThrow();
  });

  it('error() does not throw, with and without error', () => {
    expect(() => service.error('Component', 'msg')).not.toThrow();
    expect(() => service.error('Component', 'msg', new Error('x'))).not.toThrow();
  });
});
