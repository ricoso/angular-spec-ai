import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TraceService {
  public readonly sessionId: string;
  private counter = 0;

  constructor() {
    this.sessionId = this.generateId();
    console.log(`[TRACE] Session started: sessionId=${this.sessionId}`);
  }

  public createCorrelationId(): string {
    this.counter++;
    return `${this.sessionId}-${this.counter.toString().padStart(4, '0')}`;
  }

  public log(component: string, message: string, data?: unknown): void {
    const entry = {
      timestamp: new Date().toISOString(),
      sessionId: this.sessionId,
      component,
      message,
      ...(data !== undefined ? { data } : {}),
    };
    console.log(`[TRACE] [${this.sessionId}] [${component}] ${message}`, data !== undefined ? data : '');
    return void entry;
  }

  public error(component: string, message: string, error?: unknown): void {
    console.error(`[TRACE] [${this.sessionId}] [${component}] ERROR: ${message}`, error !== undefined ? error : '');
  }

  private generateId(): string {
    const array = new Uint8Array(8);
    crypto.getRandomValues(array);
    return Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('');
  }
}
