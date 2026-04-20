# Spring Boot Backend — Autohaus Buchungs-API

Proxy/Middleware zur SoftNet Planning API (ersetzt das fruehere NestJS-Backend).

## Voraussetzungen

- **Java 25** (JDK)
- **Gradle** (oder `./gradlew` Wrapper im Projekt)
- **Spring Boot 4.1.0**
- **SoftNet-Fingerprint** Datei (JSON mit RSA Private Key)

## Konfiguration

Umgebungsvariablen setzen (oder in `application.yml` / `.env` ablegen):

```bash
# Server
export PORT=3000

# SoftNet API
export SOFTNET_BASE_URL=https://api.softnet.example.com
export SOFTNET_AUTH_URL=https://auth.softnet.example.com/token
export SOFTNET_FINGERPRINT_PATH=./config/softnet-fingerprint.json
export SOFTNET_TIMEOUT=10000
export GROUP_ID=your-group-id
export BRANCH_ID=your-branch-id

# Session
export SESSION_SECRET=min-32-zeichen-langer-secret-key-hier
export SESSION_TTL_MINUTES=20

# CORS
export CORS_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:3000

# TLS (optional)
export TLS_ENABLED=false
export TLS_CERT_PATH=
export TLS_KEY_PATH=
```

## Starten

```bash
# Development
cd server
./gradlew bootRun

# Oder mit Umgebungsvariablen
PORT=3000 SOFTNET_BASE_URL=... ./gradlew bootRun
```

Server laeuft auf `http://localhost:8080` (Standard) bzw. auf dem in `PORT` konfigurierten Port.

## Bauen

```bash
cd server
./gradlew build
```

JAR liegt unter `build/libs/`.

```bash
# JAR direkt starten
java -jar build/libs/server-*.jar
```

## Testen

```bash
# Unit-Tests
cd server
./gradlew test

# E2E-Tests (Angular Frontend gegen Spring Backend)
# 1. Spring Backend starten (Port 3000)
# 2. In einem separaten Terminal:
cd /path/to/angular-ai-test
npm run e2e
```

## Zusammen mit Angular Frontend

```bash
# Terminal 1: Spring Backend
cd server && ./gradlew bootRun

# Terminal 2: Angular Dev Server (proxied /api -> localhost:3000)
npm start
```

Die `proxy.conf.json` leitet `/api/*` automatisch an `http://localhost:3000` weiter.

## API-Endpunkte

| Methode | Pfad | Guard | Beschreibung |
|---------|------|:-----:|-------------|
| GET | `/api/v1/session/token` | - | Session-Token generieren |
| GET | `/api/v1/health` | - | Health Check |
| GET | `/api/v1/branches` | Token | Alle Standorte |
| GET | `/api/v1/branches/{id}` | Token | Einzelner Standort |
| GET | `/api/v1/branches/{id}/brands` | Token | Marken fuer Standort |
| GET | `/api/v1/branches/{id}/jobtypes` | Token | Services + Operationen |
| GET | `/api/v1/branches/{id}/mobility-alternatives` | Token | Mobilitaetsalternativen |
| POST | `/api/v1/snaps` | Token | Verfuegbare Terminslots |
| POST | `/api/v1/appointments` | Token | Termin erstellen |
| GET | `/api/v1/appointments/{id}` | Token | Termin abrufen |
| PUT | `/api/v1/appointments/{id}/cancel` | Token | Termin stornieren |
| POST | `/api/v1/customers` | Token | Kunde anlegen |
| GET | `/api/v1/customers/search` | Token | Kunde suchen |
| POST | `/api/v1/cars` | Token | Fahrzeug anlegen |

Alle Token-geschuetzten Endpunkte erwarten den Header `X-Session-Token`.

## Requirement

Siehe [REQ-018-Spring-Boot-Migration](../docs/requirements/REQ-018-Spring-Boot-Migration/requirement.md) fuer die vollstaendige Spezifikation.
