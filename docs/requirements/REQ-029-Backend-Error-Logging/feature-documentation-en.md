# Feature Documentation: Backend Error Logging (Trace ID, Error Payload Dump, Azure Email Alert)

**Created:** 2026-07-06
**Requirement:** REQ-029-Backend-Error-Logging
**Language:** EN
**Status:** Implemented

---

## Overview

The SoftNet API operator reports errors (502 among others) allegedly caused by our requests. Until now, the backend could not prove which data was actually sent and received: neither request/response payloads nor error response bodies were logged, and the log lines of a single request were not correlatable.

This feature closes that gap with three building blocks:

1. **Trace ID prefix on every log line** (normal case): The `RequestIdFilter` reads or generates the `X-Request-Id` and stores it as SLF4J MDC key `traceId`. The Logback console pattern prepends it to every line; outside of HTTP requests (startup, scheduler, cache) the default `system: ` applies.
2. **Error payload dump ONLY in the failure case**: When a SoftNet call fails, the backend writes an ERROR line carrying the stable marker `SOFTNET_ERROR` — including the sent request body and the SoftNet error response body. When a reservation call fails, the **unmodified** raw snap (exactly as received from SoftNet) is logged additionally. All payloads are truncated to 8,000 characters. On success, **no** payloads are logged.
3. **Azure email alert**: A scheduled-query alert rule (`alert-softnet-error-logs`) on the Log Analytics workspace matches `SOFTNET_ERROR` via KQL and notifies the existing action group `gs-app-alerts` by email.

As a result, every SoftNet failure is immediately visible (email) and provable towards the API operator (payloads + trace ID correlation).

---

## User Guidance

n/a — backend feature without UI. There are no screenshots and no mockups; instead, the following system flows and log format examples document the behavior.

---

## How It Works (System Flows)

### Step 1: HTTP request arrives

**System:** The `RequestIdFilter` reads the `X-Request-Id` header (or generates a UUID if it is missing) and stores the value as MDC key `traceId`.

**Result:** All log lines of this request carry the prefix `<traceId>: ` (after timestamp/level/logger).

### Step 2: SoftNet call succeeds

**System:** The `SoftnetApiClient` only writes a debug log with the call duration — **no** payloads.

**Result:** The log stays lean in the normal case; customer data never ends up in the log.

### Step 3: Request ends

**System:** The MDC key is cleared in the `finally` block; the `LoggingFilter` writes the access line (method, URL, status code, duration).

**Result:** No trace ID leakage into unrelated requests (thread pool reuse).

### Alternative flow: Log outside of an HTTP request

Startup, scheduler, or cache logs have no request context: the MDC key `traceId` is not set, the Logback pattern `%X{traceId:-system}` kicks in, and the line appears with the prefix `system: `.

### Error flow E1: SoftNet call fails (502/4xx/timeout/auth)

1. `SoftnetApiClient.executeRequest()` throws an exception; if it is a `RestClientResponseException`, the response body is extracted.
2. ERROR line: `SOFTNET_ERROR <METHOD> <endpoint> failed after <ms>: <msg> | requestBody=<json> | responseBody=<json>` — both payloads truncated to 8,000 characters.
3. The exception propagates unchanged; the `GlobalExceptionHandler` maps it to 502/503/504 as before.

### Error flow E2: Reservation fails

1. `AppointmentsService.createAppointment()` additionally logs: `SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap=<unmodified snap>`.
2. The complete error picture consists of **three adjacent lines sharing the same trace ID**: raw snap → patched snap + SoftNet error body → error summary.

### Azure email alert

A scheduled-query alert rule checks the Log Analytics workspace (`ContainerAppConsoleLogs_CL`) every 5 minutes for lines containing `SOFTNET_ERROR` and, on a match, triggers the action group `gs-app-alerts` (email). `--mute-actions-duration PT30M` prevents mail storms during longer outages. The complete `az monitor scheduled-query create` command is documented in [`docs/infrastructure/azure-read.md`](../../infrastructure/azure-read.md) (section "Log-basierter Alert: SOFTNET_ERROR (REQ-029)").

---

## Log Format Examples

**Normal case (every line carries the trace ID prefix):**

```
2026-07-06T10:15:03.123 [http-nio-3000-exec-1] INFO  HTTP - 7f3e…-uuid: {"correlationId":"","sessionId":"","method":"POST","url":"/api/v1/snaps","statusCode":200,"duration":"245ms"}
```

**Failure case (SoftNet) — marker `SOFTNET_ERROR`:**

```
… ERROR c.a.b.softnet.SoftnetApiClient - <traceId>: SOFTNET_ERROR POST /api/planning/appointments failed after 312ms: 502 Bad Gateway | requestBody={…} | responseBody={…}
… ERROR c.a.b.a.AppointmentsService - <traceId>: SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap={…}
```

**Log outside of a request (startup/scheduler/cache):**

```
… INFO  c.a.b.snaps.SnapCache - system: cache warmed up
```

**Truncation (payload > 8,000 characters):**

```
…{truncated payload}...[truncated, total N chars]
```

---

## Responsive Views

n/a — backend feature without UI.

---

## Accessibility

n/a — backend feature without UI.

---

## Error Analysis Guide

When an alert email arrives (or a SoftNet error is reported), analyze in the Log Analytics workspace `workspacedefaultresourcegroupdewc99c4` using KQL:

**1. Find `SOFTNET_ERROR` lines (they contain the payloads and the trace ID):**

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "SOFTNET_ERROR"
| project TimeGenerated, Log_s
| order by TimeGenerated desc
```

**2. Pull ALL log lines of ONE request via the trace ID (correlation):**

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "<traceId>"
| project TimeGenerated, Log_s
| order by TimeGenerated asc
```

**Procedure:** Copy the trace ID (the prefix before `: `) from the `SOFTNET_ERROR` line and use query 2 to retrieve the full request history. For reservation failures, the raw snap, patched snap (= request body), SoftNet error body, and error summary are adjacent lines under the same trace ID — this is the evidence data set for the SoftNet operator.

**Testing the alert:** Temporarily set an invalid `SOFTNET_BASE_URL` env var (or trigger a booking with an expired snap), issue a snaps request, wait ~15–20 minutes, check the email inbox, and reset the env var.

---

## Operational Notes

- **Create the alert only AFTER deployment:** The alert rule `alert-softnet-error-logs` is a one-time manual setup step after the REQ-029 change has been deployed to the Azure Container App (command in `docs/infrastructure/azure-read.md`, section 8).
- **Ingestion latency:** `ContainerAppConsoleLogs_CL` has ~5–15 minutes of ingestion latency — the alert email arrives with a corresponding delay (5-minute evaluation window + latency).
- **Mail storms:** `--mute-actions-duration PT30M` suppresses follow-up mails for 30 minutes after an alert.
- **Cost:** Scheduled-query rules are billed per rule (~1.5 USD/month at 5-minute frequency).
- **Recipients:** Must have confirmed the action group confirmation mail of `gs-app-alerts`.
- **Data privacy:** The error payloads contain customer data (name, email, address, license plate). They are logged **exclusively in the failure case** (a deliberate trade-off for clarifying issues with the provider); retention is governed by the Log Analytics workspace retention. No secrets/bearer tokens are logged.
- **Stable marker:** `SOFTNET_ERROR` must not be renamed — the Azure KQL alert rule matches on it. The trace ID is NOT transmitted to SoftNet.

---

## Technical Details

| Property | Value |
|----------|-------|
| Route | n/a — backend feature without UI |
| Container Component | n/a — backend feature without UI |
| Store | n/a — backend feature without UI |
| API Service | n/a — no frontend service affected |

**Changed/new backend classes** (`server/src/main/java/com/autohaus/backend/`):

| Class/File | Change |
|------------|--------|
| `common/filter/RequestIdFilter.java` | MDC put/remove of key `traceId` (`TRACE_ID_MDC_KEY`), source: `X-Request-Id` header (fallback: generated UUID) |
| `common/logging/LogPayloadUtil.java` | **New** — `truncate()` cuts payloads at `MAX_PAYLOAD_LOG_CHARS = 8_000` characters (+ truncation notice) |
| `softnet/SoftnetApiClient.java` | Auth moved into try block; `SOFTNET_ERROR` log with request/response body and call duration in the failure case |
| `appointments/AppointmentsService.java` | `deepCopy()` before patching; `SOFTNET_ERROR` log with unmodified raw snap on failed reservation |
| `common/filter/LoggingFilter.java` | Removed redundant requestId from log messages (now part of the MDC prefix) |
| `common/exception/GlobalExceptionHandler.java` | Removed redundant requestId from log messages; error mapping (502/503/504) unchanged |
| `src/main/resources/application.yml` | Console pattern extended with `%X{traceId:-system}: ` |

**Azure (manual setup step):**

| Resource | Value |
|----------|-------|
| Alert rule | `alert-softnet-error-logs` (scheduled query, 5-minute window/frequency, severity 2) |
| Action group | `gs-app-alerts` (existing, email) |
| Log Analytics workspace | `workspacedefaultresourcegroupdewc99c4` (table `ContainerAppConsoleLogs_CL`) |

**Related documents:**
- Requirement: [`requirement.md`](./requirement.md)
- Azure alert setup + KQL: [`docs/infrastructure/azure-read.md`](../../infrastructure/azure-read.md)
