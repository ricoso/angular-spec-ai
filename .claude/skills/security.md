# Security (COMPACT)

## Projekt-Vorgabe
- Typ: Click-Dummy (Static Hosting)
- Keine Server-Side Security
- localStorage OK für Demo-Daten

**Wann:** Bei ALLEN Features - PFLICHT vor jedem Commit!

---

## XSS Prevention

```typescript
// ❌ NIEMALS
this.sanitizer.bypassSecurityTrustHtml(userInput);
element.innerHTML = userInput;
eval(userCode);

// ✅ Angular escaped automatisch
template: `<div>{{ userInput() }}</div>`

// ✅ Wenn HTML nötig (nur TRUSTED sources!)
sanitizedHtml = computed(() =>
  this.sanitizer.sanitize(SecurityContext.HTML, this.trustedHtml()) || ''
);
```

---

## Authentication

### JWT in HttpOnly Cookie (NICHT localStorage!)
```typescript
// ❌ localStorage.setItem('token', jwt);
// ✅ Backend setzt HttpOnly Cookie: Set-Cookie: token=xxx; HttpOnly; Secure; SameSite=Strict
```

### Route Guards
```typescript
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login']);
};

export const roleGuard = (role: string): CanActivateFn => () => {
  return inject(AuthService).hasRole(role);
};

// Routes
{ path: 'admin', canActivate: [authGuard, roleGuard('admin')], ... }
```

---

## CSRF Protection

```typescript
// app.config.ts
provideHttpClient(
  withXsrfConfiguration({
    cookieName: 'XSRF-TOKEN',
    headerName: 'X-XSRF-TOKEN'
  })
)
```

---

## Sensitive Data

```typescript
// ❌ console.log('Password:', password);
// ❌ const apiKey = 'sk-xxx-hardcoded';
// ✅ console.debug('Login attempt for user:', email);
// ✅ const apiKey = environment.apiKey;
```

---

## Input Validation

```typescript
this.form = this.fb.group({
  email: ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.minLength(8),
    Validators.pattern(/^(?=.*[A-Z])(?=.*[0-9]).*$/)]],
});

export function noScriptValidator(): ValidatorFn {
  return (control) => {
    const forbidden = /<script/i.test(control.value);
    return forbidden ? { noScript: true } : null;
  };
}
```

---

## Error Handling

```typescript
// ❌ catchError((error) => throwError(() => error))  // Stack Trace an User
// ✅ Generische Fehlermeldung
catchError((error: unknown) => {
  console.error('Internal:', error);
  return throwError(() => new Error('Ein Fehler ist aufgetreten.'));
})
```

---

## Checklist (vor jedem Commit)

- Keine `console.log` mit sensiblen Daten
- Keine hardcoded Credentials
- Keine `bypassSecurityTrust*` mit User-Input
- Route Guards für geschützte Routes
- Input Validation vorhanden
- Error Messages ohne Details/Stack Traces

---

## Backend Security (Spring Boot)

### Spring Security FilterChain
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())  // Stateless API, CSRF not needed
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/**").permitAll()
            .anyRequest().denyAll()
        )
        .build();
}
```

### CORS Configuration
- Configured via `WebConfig` implementing `WebMvcConfigurer`
- Allowed origins from env var `CORS_ALLOWED_ORIGINS` (comma-separated)
- Allowed methods: GET, POST, PUT, DELETE, OPTIONS
- Allowed headers: Content-Type, Authorization, X-Session-Token

### Security Headers
| Header | Value |
|--------|-------|
| Content-Security-Policy | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'` |
| X-Frame-Options | `DENY` |
| X-Content-Type-Options | `nosniff` |
| Referrer-Policy | `strict-origin-when-cross-origin` |
| Permissions-Policy | `camera=(), microphone=(), geolocation=()` |

### Rate Limiting
- 100 requests per 15 minutes per IP (Bucket4j)
- Applied via servlet filter on `/api/v1/**`
- Returns HTTP 429 Too Many Requests when exceeded

### Session Token Validation
- HMAC-SHA256 signed session tokens
- Validated via `SessionTokenFilter` (servlet filter)
- Token passed in header: `X-Session-Token`
- Secret configured via `SESSION_TOKEN_SECRET` env var

### Origin Guard
- Validates `Origin` and `Referer` headers on mutating requests
- Rejects requests with mismatched or missing origin headers
- Configured allowed origins via `CORS_ALLOWED_ORIGINS` env var

### HTTPS / TLS
- Configurable via environment variables:
  - `TLS_ENABLED` (true/false, default: false)
  - `TLS_CERT_PATH` (path to certificate file)
  - `TLS_KEY_PATH` (path to private key file)
- In production: always enable TLS or terminate at load balancer
