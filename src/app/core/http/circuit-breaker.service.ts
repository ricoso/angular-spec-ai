import { Injectable } from '@angular/core';

export enum CircuitState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN',
  HALF_OPEN = 'HALF_OPEN',
}

export interface CircuitBreakerStatus {
  readonly state: CircuitState;
  readonly failureCount: number;
  readonly lastFailureTime: number;
}

const FAILURE_THRESHOLD = 5;
const RESET_TIMEOUT_MS = 30_000;
const HALF_OPEN_MAX_ATTEMPTS = 2;

/**
 * Circuit Breaker for frontend API calls.
 * CLOSED  → requests pass through normally
 * OPEN    → requests rejected immediately (backend unreachable)
 * HALF_OPEN → limited probe requests to check recovery
 */
@Injectable({ providedIn: 'root' })
export class CircuitBreakerService {
  private state = CircuitState.CLOSED;
  private failureCount = 0;
  private lastFailureTime = 0;
  private halfOpenAttempts = 0;

  getState(): CircuitState {
    if (this.state === CircuitState.OPEN) {
      const elapsed = Date.now() - this.lastFailureTime;
      if (elapsed >= RESET_TIMEOUT_MS) {
        this.transitionTo(CircuitState.HALF_OPEN);
      }
    }
    return this.state;
  }

  isOpen(): boolean {
    return this.getState() === CircuitState.OPEN;
  }

  getStatus(): CircuitBreakerStatus {
    return {
      state: this.getState(),
      failureCount: this.failureCount,
      lastFailureTime: this.lastFailureTime,
    };
  }

  recordSuccess(): void {
    this.failureCount = 0;
    this.halfOpenAttempts = 0;
    this.transitionTo(CircuitState.CLOSED);
  }

  recordFailure(): void {
    this.failureCount++;
    this.lastFailureTime = Date.now();

    if (this.state === CircuitState.HALF_OPEN) {
      this.halfOpenAttempts++;
      if (this.halfOpenAttempts >= HALF_OPEN_MAX_ATTEMPTS) {
        this.transitionTo(CircuitState.OPEN);
      }
    } else if (this.failureCount >= FAILURE_THRESHOLD) {
      this.transitionTo(CircuitState.OPEN);
    }
  }

  private transitionTo(newState: CircuitState): void {
    if (this.state !== newState) {
      console.warn(`[CircuitBreaker] ${this.state} → ${newState}`);
      this.state = newState;
      if (newState === CircuitState.HALF_OPEN) {
        this.halfOpenAttempts = 0;
      }
    }
  }
}
