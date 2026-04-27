/** Critical error code categories that escalate to the global fallback screen. */
export type CriticalErrorCode = 'NETWORK' | 'SERVER_5XX' | 'AUTH_LOOP' | 'UNKNOWN';

/** Top-level error payload shown by the error-fallback screen. */
export interface CriticalError {
  code: CriticalErrorCode;
  message: string;
  requestId: string | null;
  timestamp: string;
  httpStatus?: number;
}
