# Platform Team Questions — REQ-017 Deployment & Infrastructure

**Project:** Angular 21 + NestJS Backend (Car Dealership Booking App)  
**Date:** 2026-04-09  
**Contact:** _____________  

> Please answer each question so we can finalize the deployment configuration.  
> Mark your answers directly in this document or reply via email/ticket.

---

## 1. OpenShift Cluster

| # | Question | Your Answer |
|---|----------|-------------|
| 1.1 | What **OpenShift version** is running? (e.g. 4.14, 4.15, 4.16) | |
| 1.2 | Is it **managed** (ROSA, ARO, IBM ROKS) or **self-hosted**? | |
| 1.3 | Which **namespaces/projects** should we use for Dev and Prod? | Dev: _________ Prod: _________ |
| 1.4 | Are namespaces already created, or should we request them? | |
| 1.5 | What **SecurityContextConstraints** (SCC) are available? (`restricted-v2`?) | |
| 1.6 | Is there a **resource quota** on the namespaces? (CPU/Memory limits) | |
| 1.7 | Do you use **NetworkPolicies**? If yes, do we need to whitelist outbound traffic to `*.soft-nrg.com`? | |

---

## 2. Container Registry

| # | Question | Your Answer |
|---|----------|-------------|
| 2.1 | Which **container registry** should we use? (GitLab Container Registry, Quay, Harbor, OpenShift Internal, other) | |
| 2.2 | What is the **registry URL**? (e.g. `registry.gitlab.com/group/project`) | |
| 2.3 | Is there an **image pull secret** already configured in the namespaces? | |
| 2.4 | Do we need to set up a **registry mirror** or proxy? | |
| 2.5 | Is **image scanning** (Trivy, Clair, Quay Scanner) required before deployment? | |
| 2.6 | What **base images** are approved? (We plan to use `node:22-alpine`) | |

---

## 3. Helm & Deployment

| # | Question | Your Answer |
|---|----------|-------------|
| 3.1 | What **Helm version** is available? (Helm 3.x?) | |
| 3.2 | Do you have a **Helm chart repository** (ChartMuseum, Harbor, GitLab)? Or do we deploy from the Git repo directly? | |
| 3.3 | Should we use `Deployment` (standard K8s) or `DeploymentConfig` (OpenShift legacy)? | |
| 3.4 | Is **ArgoCD** or **Flux** used for GitOps? If yes, we need to adapt the deployment flow. | |
| 3.5 | Are there **naming conventions** for Helm releases? (e.g. `<team>-<app>-<env>`) | |
| 3.6 | Is **HorizontalPodAutoscaler (HPA)** available and desired for Prod? | |

---

## 4. Networking & Routes

| # | Question | Your Answer |
|---|----------|-------------|
| 4.1 | What **domain names** should we use for the Routes? | Dev: _________ Prod: _________ |
| 4.2 | Is **TLS Edge Termination** via OpenShift Router the standard? Or do you use a different TLS strategy? | |
| 4.3 | Are **wildcard certificates** available on the router, or do we need to provide our own certs? | |
| 4.4 | Is there an **external load balancer** or **CDN** (e.g. Cloudflare, Akamai) in front of OpenShift? | |
| 4.5 | Do we need to configure **CORS** at the Route level, or is it handled purely in the application? | |
| 4.6 | Is **WebSocket** support needed on the Route? (Currently no, but for future reference) | |

---

## 5. GitLab CI/CD

| # | Question | Your Answer |
|---|----------|-------------|
| 5.1 | What is the **GitLab project URL**? | |
| 5.2 | Are **GitLab Runners** available? (Shared or dedicated?) | |
| 5.3 | Do the runners have **Docker-in-Docker (DinD)** support for building images? | |
| 5.4 | Can runners access the **OpenShift API** for Helm deployments? (Or do we use a deploy token/service account?) | |
| 5.5 | Is there an **OpenShift service account token** we should use for CI/CD deployments? | |
| 5.6 | Should we use GitLab **Environments** feature for tracking deployments? | |
| 5.7 | Are there existing **CI/CD templates** or includes we should extend? | |

---

## 6. Secrets Management

| # | Question | Your Answer |
|---|----------|-------------|
| 6.1 | Should secrets be stored in **GitLab CI/CD Variables**, or is there a dedicated secrets manager? (Vault, CyberArk, Sealed Secrets) | |
| 6.2 | Is **GitLab Environment Scoping** for variables (dev/prod separation) acceptable? | |
| 6.3 | How should **Fingerprint files** (RSA private keys, ~2KB JSON) be handled? GitLab File Variables → K8s Secret Volume Mount? | |
| 6.4 | Who has **access** to manage GitLab CI/CD Variables? (Only platform team, or also dev team?) | |
| 6.5 | Is there a **secrets rotation policy**? (How often should SESSION_SECRET be rotated?) | |
| 6.6 | The app needs these **environment variables** at runtime — please confirm this approach is acceptable: |

```
PORT=3000
SOFTNET_READ_BASE_URL=https://api.soft-nrg.com
SOFTNET_READ_AUTH_URL=https://auth.soft-nrg.com/oauth/token
SOFTNET_READ_FINGERPRINT_PATH=/app/config/fingerprint-read.json
SOFTNET_WRITE_BASE_URL=https://int-api.soft-nrg.com        # Dev only
SOFTNET_WRITE_AUTH_URL=https://int-auth.soft-nrg.com/...    # Dev only
SOFTNET_WRITE_FINGERPRINT_PATH=/app/config/fingerprint-write.json
SOFTNET_TIMEOUT=10000
GROUP_ID=698b34c24dd8eea4d6d42179
BRANCH_ID=58341c5cbe856f6dc47e9f05
CORS_ALLOWED_ORIGINS=https://booking.example.com
IFRAME_ALLOWED_ORIGINS=https://www.kundenwebsite.de
SESSION_SECRET=<min-32-chars-hmac-secret>
SESSION_TTL_MINUTES=20
TLS_ENABLED=false
```

---

## 7. Monitoring & Logging

| # | Question | Your Answer |
|---|----------|-------------|
| 7.1 | Is **centralized logging** available? (EFK, Loki, Splunk?) Our app outputs structured JSON logs. | |
| 7.2 | Is **Prometheus** available for metrics scraping? We can expose `/api/v1/metrics`. | |
| 7.3 | Is **Grafana** available for dashboards? | |
| 7.4 | Do you have **alerting** set up? (PagerDuty, OpsGenie, email?) | |
| 7.5 | Are there **log retention policies** we should be aware of? | |

---

## 8. Outbound Network Access

| # | Question | Your Answer |
|---|----------|-------------|
| 8.1 | Can pods make **outbound HTTPS calls** to external APIs? Our backend calls `*.soft-nrg.com`. | |
| 8.2 | Is there an **egress proxy** (Squid, corporate proxy) we need to configure? | |
| 8.3 | Do we need to **whitelist** specific external domains? If yes, please add: | |

```
# Required external access (HTTPS/443):
api.soft-nrg.com          # SoftNet Production API
int-api.soft-nrg.com      # SoftNet Integration API
auth.soft-nrg.com         # SoftNet Production Auth
int-auth.soft-nrg.com     # SoftNet Integration Auth
```

---

## 9. Rollback & Recovery

| # | Question | Your Answer |
|---|----------|-------------|
| 9.1 | Is `helm rollback` the preferred rollback method, or do you have a different process? | |
| 9.2 | How many **revision history** entries should Helm keep? (default: 10) | |
| 9.3 | Is there a **change management / approval process** for Prod deployments? | |

---

## 10. Timeline & Access

| # | Question | Your Answer |
|---|----------|-------------|
| 10.1 | When can we get **access** to the Dev namespace for testing? | |
| 10.2 | Who is the **point of contact** on the platform team for deployment issues? | |
| 10.3 | Is there a **sandbox/playground** namespace we can use for initial testing? | |
| 10.4 | Do you need a **review** of our Dockerfile/Helm Charts before first deployment? | |

---

## Summary of What We Provide

| Artifact | Status |
|----------|--------|
| NestJS Backend (Node.js 22) | ✅ Done |
| Angular 21 Frontend (static files served by backend) | ✅ Done |
| Health endpoint (`GET /api/v1/health`) | ✅ Done |
| Structured JSON logging | ✅ Done |
| Graceful shutdown (SIGTERM handling) | ✅ Done |
| Dockerfile (multi-stage, non-root) | 📋 Ready to create |
| Helm Charts | 📋 Ready to create |
| `.gitlab-ci.yml` | 📋 Ready to create |
| GitHub Actions (PR checks + E2E) | 📋 Ready to create |

> **Next step:** Once we have your answers, we'll finalize the Helm Charts, Dockerfile, and CI/CD pipeline configuration (REQ-017 implementation).
