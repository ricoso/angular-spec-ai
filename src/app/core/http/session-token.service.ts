import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

interface SessionTokenApiResponse {
  data: {
    token: string;
    expiresAt: string;
  };
  meta: {
    timestamp: string;
    requestId: string;
  };
}

@Injectable({ providedIn: 'root' })
export class SessionTokenService {
  private readonly http = inject(HttpClient);
  private token: string | null = null;
  private expiresAt: Date | null = null;
  private fetchPromise: Promise<string> | null = null;

  getToken(): string | null {
    if (this.token && this.expiresAt && this.expiresAt > new Date()) {
      return this.token;
    }
    void this.refreshToken();
    return this.token;
  }

  async ensureToken(): Promise<string> {
    if (this.token && this.expiresAt && this.expiresAt > new Date()) {
      return this.token;
    }
    return this.refreshToken();
  }

  private async refreshToken(): Promise<string> {
    if (this.fetchPromise) {
      return this.fetchPromise;
    }

    this.fetchPromise = new Promise<string>((resolve, reject) => {
      this.http.get<SessionTokenApiResponse>('/api/v1/session/token').subscribe({
        next: (response) => {
          this.token = response.data.token;
          this.expiresAt = new Date(response.data.expiresAt);
          console.log(`[TRACE] [SessionTokenService] Token received, expires=${response.data.expiresAt}`);
          this.fetchPromise = null;
          resolve(response.data.token);
        },
        error: (err: unknown) => {
          console.error('[TRACE] [SessionTokenService] Token fetch failed', err);
          this.fetchPromise = null;
          reject(err instanceof Error ? err : new Error(String(err)));
        },
      });
    });

    return this.fetchPromise;
  }

  clearToken(): void {
    this.token = null;
    this.expiresAt = null;
  }
}
