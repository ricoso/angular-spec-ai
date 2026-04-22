# REQ-020: Solution Proposal — Azure Container Apps Deployment

**Status:** Draft  
**Date:** 2026-04-22  
**Author:** Team  

---

## 1. Executive Summary

Deploy the AutoHausTermin application (Angular 21 + Spring Boot) as a **single container** on **Azure Container Apps (Consumption Plan)** using the existing Dockerfile from REQ-019. No custom domain needed — the auto-generated `*.azurecontainerapps.io` URL provides HTTPS out of the box.

**Total effort:** ~2h initial setup, ~5 min per subsequent deployment.  
**Monthly cost:** ~15 €/month (ACR Basic + 1 always-warm replica at idle rate).

---

## 2. Feasibility Assessment

### 2.1 Confirmed: Azure Container Apps supports this architecture

| Requirement | Azure Container Apps Support | Evidence |
|---|---|---|
| **Single container, Spring Boot + Angular** | ✅ Fully supported | [MS Docs: Deploy Java Spring Boot](https://learn.microsoft.com/en-us/azure/developer/java/identity/deploy-spring-boot-to-azure-container-apps) |
| **Custom Dockerfile** | ✅ `az containerapp up --source .` builds from Dockerfile | [MS Docs: containerapp up](https://learn.microsoft.com/en-us/azure/container-apps/containerapp-up) |
| **HTTPS with auto-TLS on `*.azurecontainerapps.io`** | ✅ Always-on, no config needed | [MS Docs: Custom domains](https://learn.microsoft.com/en-us/azure/container-apps/custom-domains-certificates) — "auto-generated Azure domain always uses HTTPS with a valid certificate" |
| **Port 3000** | ✅ Configurable via `--target-port 3000` | Standard Container Apps ingress |
| **Always-warm replica (idle rate billing)** | ✅ `minReplicas: 1` with reduced idle-rate pricing | [Pricing page](https://azure.microsoft.com/en-us/pricing/details/container-apps/) |
| **Environment variables / Secrets** | ✅ Via `--set-env-vars` and `--secrets` | Native Container Apps feature |
| **Secret file mounts (for fingerprint JSONs)** | ✅ Secret volumes supported | Container Apps Secret Volumes |
| **Germany West Central region** | ✅ Available | Azure region in Germany |
| **Azure CLI scriptable** | ✅ Fully automatable | `az containerapp` commands |

### 2.2 Key Technical Details

**Image build approach:** Two options available:

| Option | How | Pros | Cons |
|---|---|---|---|
| **A) `az containerapp up --source .`** | Azure builds the image from Dockerfile server-side via ACR Tasks | One command, no local Docker needed | Requires the full source context to be uploaded; slower; may hit build limits |
| **B) Local build + ACR push** | `docker build` locally, `az acr login`, `docker push` | Fast, full control, uses existing Dockerfile | Requires Docker Desktop locally |

**Recommendation: Option B** — we already have a working Dockerfile. Building locally gives us full control and faster iteration. The script below implements this approach.

### 2.3 What about `az containerapp up` vs explicit commands?

`az containerapp up` is a convenience wrapper that auto-creates Resource Group, ACR, Environment, and Container App in one command. However, for a reproducible setup with secrets and specific configuration, **explicit commands are more predictable and better documented.** The script below uses explicit commands.

---

## 3. Deployment Architecture

### 3.1 Azure Resources

```
Resource Group: rg-autohaus-poc
├── Azure Container Registry: acrautohauspoc
│     └── autohaus-app:latest (Docker Image)
└── Container Apps Environment: cae-autohaus-poc
      └── Container App: autohaus-app
            ├── Ingress: external, port 3000, HTTPS
            ├── Resources: 0.5 vCPU, 1 Gi Memory
            ├── Scale: 1 replica (always warm, no cold start)
            ├── Secrets: SoftNet credentials, session secret
            └── Secret Volume: /app/config/ (fingerprint JSONs)
```

### 3.2 Generated URL

```
https://autohaus-app.<random-hash>.germanywestcentral.azurecontainerapps.io
```

---

## 4. Deployment Script

The script below automates the entire setup. It should be placed at `infra/azure-poc/deploy.sh`.

### 4.1 Prerequisites

```bash
# Required tools
az --version          # Azure CLI >= 2.60
docker --version      # Docker Desktop (for local build)

# Login
az login
az account set --subscription "<SUBSCRIPTION_ID>"
```

### 4.2 Full Deploy Script

```bash
#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# AutoHausTermin — Azure Container Apps POC Deployment
# ============================================================================
# Usage:
#   ./deploy.sh setup     # First-time: create all resources + deploy
#   ./deploy.sh update    # Rebuild image + deploy new revision
#   ./deploy.sh teardown  # Delete everything
#   ./deploy.sh url       # Print the app URL
# ============================================================================

# --- Configuration -----------------------------------------------------------

RESOURCE_GROUP="rg-autohaus-poc"
LOCATION="germanywestcentral"
ACR_NAME="acrautohauspoc"           # must be globally unique, lowercase, no dashes
ENVIRONMENT="cae-autohaus-poc"
APP_NAME="autohaus-app"
IMAGE_NAME="autohaus-app"
IMAGE_TAG="latest"
TARGET_PORT=3000

# --- Secrets (override via environment or .env file) -------------------------

if [[ -f "$(dirname "$0")/.env" ]]; then
  # shellcheck disable=SC1091
  source "$(dirname "$0")/.env"
fi

: "${SOFTNET_READ_BASE_URL:=https://api.soft-nrg.com}"
: "${SOFTNET_READ_AUTH_URL:=https://auth.soft-nrg.com/oauth/token}"
: "${SOFTNET_WRITE_BASE_URL:=https://int-api.soft-nrg.com}"
: "${SOFTNET_WRITE_AUTH_URL:=https://int-auth.soft-nrg.com/oauth/token}"
: "${SOFTNET_TIMEOUT:=10000}"
: "${GROUP_ID:=698b34c24dd8eea4d6d42179}"
: "${BRANCH_ID:=58341c5cbe856f6dc47e9f05}"
: "${SESSION_SECRET:=$(openssl rand -hex 32)}"
: "${SESSION_TTL_MINUTES:=20}"
: "${TLS_ENABLED:=false}"

# Paths to fingerprint JSON files
: "${READ_FINGERPRINT_FILE:=./server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json}"
: "${WRITE_FINGERPRINT_FILE:=./server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json}"

# --- Helper Functions --------------------------------------------------------

log() { echo "==> $*"; }
err() { echo "ERROR: $*" >&2; exit 1; }

get_acr_server() {
  az acr show --name "$ACR_NAME" --query loginServer -o tsv
}

get_app_url() {
  az containerapp show \
    --name "$APP_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --query "properties.configuration.ingress.fqdn" -o tsv
}

build_and_push() {
  local acr_server
  acr_server=$(get_acr_server)

  log "Building Docker image..."
  docker build -t "${acr_server}/${IMAGE_NAME}:${IMAGE_TAG}" -f Dockerfile .

  log "Logging into ACR..."
  az acr login --name "$ACR_NAME"

  log "Pushing image to ACR..."
  docker push "${acr_server}/${IMAGE_NAME}:${IMAGE_TAG}"
}

# --- Commands ----------------------------------------------------------------

cmd_setup() {
  log "Creating Resource Group: ${RESOURCE_GROUP}"
  az group create \
    --name "$RESOURCE_GROUP" \
    --location "$LOCATION" \
    --output none

  log "Creating Azure Container Registry: ${ACR_NAME}"
  az acr create \
    --resource-group "$RESOURCE_GROUP" \
    --name "$ACR_NAME" \
    --sku Basic \
    --admin-enabled true \
    --output none

  log "Creating Container Apps Environment: ${ENVIRONMENT}"
  az containerapp env create \
    --name "$ENVIRONMENT" \
    --resource-group "$RESOURCE_GROUP" \
    --location "$LOCATION" \
    --output none

  # Build and push Docker image
  build_and_push

  local acr_server
  acr_server=$(get_acr_server)
  local acr_username acr_password
  acr_username=$(az acr credential show --name "$ACR_NAME" --query username -o tsv)
  acr_password=$(az acr credential show --name "$ACR_NAME" --query "passwords[0].value" -o tsv)

  # Read fingerprint file contents for secrets
  local read_fp write_fp
  read_fp=$(cat "$READ_FINGERPRINT_FILE" | base64)
  write_fp=$(cat "$WRITE_FINGERPRINT_FILE" | base64)

  log "Creating Container App: ${APP_NAME}"
  az containerapp create \
    --name "$APP_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --environment "$ENVIRONMENT" \
    --image "${acr_server}/${IMAGE_NAME}:${IMAGE_TAG}" \
    --registry-server "$acr_server" \
    --registry-username "$acr_username" \
    --registry-password "$acr_password" \
    --target-port "$TARGET_PORT" \
    --ingress external \
    --cpu 0.5 \
    --memory 1.0Gi \
    --min-replicas 1 \
    --max-replicas 1 \
    --secrets \
      "session-secret=${SESSION_SECRET}" \
      "read-fingerprint=${read_fp}" \
      "write-fingerprint=${write_fp}" \
    --env-vars \
      "PORT=${TARGET_PORT}" \
      "SOFTNET_READ_BASE_URL=${SOFTNET_READ_BASE_URL}" \
      "SOFTNET_READ_AUTH_URL=${SOFTNET_READ_AUTH_URL}" \
      "SOFTNET_WRITE_BASE_URL=${SOFTNET_WRITE_BASE_URL}" \
      "SOFTNET_WRITE_AUTH_URL=${SOFTNET_WRITE_AUTH_URL}" \
      "SOFTNET_TIMEOUT=${SOFTNET_TIMEOUT}" \
      "GROUP_ID=${GROUP_ID}" \
      "BRANCH_ID=${BRANCH_ID}" \
      "SESSION_TTL_MINUTES=${SESSION_TTL_MINUTES}" \
      "TLS_ENABLED=${TLS_ENABLED}" \
      "SESSION_SECRET=secretref:session-secret" \
      "SOFTNET_READ_FINGERPRINT_BASE64=secretref:read-fingerprint" \
      "SOFTNET_WRITE_FINGERPRINT_BASE64=secretref:write-fingerprint" \
    --output none

  # Set CORS after we know the URL
  local app_url
  app_url=$(get_app_url)
  log "App deployed at: https://${app_url}"

  log "Updating CORS_ALLOWED_ORIGINS..."
  az containerapp update \
    --name "$APP_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --set-env-vars "CORS_ALLOWED_ORIGINS=https://${app_url}" \
    --output none

  echo ""
  echo "============================================"
  echo "  Deployment complete!"
  echo "  URL: https://${app_url}"
  echo "============================================"
}

cmd_update() {
  build_and_push

  local acr_server
  acr_server=$(get_acr_server)

  log "Updating Container App with new image..."
  az containerapp update \
    --name "$APP_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --image "${acr_server}/${IMAGE_NAME}:${IMAGE_TAG}" \
    --output none

  local app_url
  app_url=$(get_app_url)
  echo ""
  echo "============================================"
  echo "  Update deployed!"
  echo "  URL: https://${app_url}"
  echo "============================================"
}

cmd_teardown() {
  log "Deleting Resource Group: ${RESOURCE_GROUP} (this deletes ALL resources)"
  az group delete \
    --name "$RESOURCE_GROUP" \
    --yes \
    --no-wait
  echo "Teardown initiated (async). Resources will be deleted in the background."
}

cmd_url() {
  local app_url
  app_url=$(get_app_url)
  echo "https://${app_url}"
}

cmd_logs() {
  az containerapp logs show \
    --name "$APP_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --type console \
    --follow
}

# --- Main --------------------------------------------------------------------

case "${1:-help}" in
  setup)    cmd_setup ;;
  update)   cmd_update ;;
  teardown) cmd_teardown ;;
  url)      cmd_url ;;
  logs)     cmd_logs ;;
  *)
    echo "Usage: $0 {setup|update|teardown|url|logs}"
    echo ""
    echo "  setup     Create all Azure resources and deploy the app"
    echo "  update    Rebuild and deploy a new version"
    echo "  teardown  Delete all Azure resources"
    echo "  url       Print the app URL"
    echo "  logs      Stream container logs"
    exit 1
    ;;
esac
```

### 4.3 Environment File Template (`.env`)

```bash
# infra/azure-poc/.env — DO NOT COMMIT (add to .gitignore)

# SoftNet API Configuration (INT environment)
SOFTNET_READ_BASE_URL=https://api.soft-nrg.com
SOFTNET_READ_AUTH_URL=https://auth.soft-nrg.com/oauth/token
SOFTNET_WRITE_BASE_URL=https://int-api.soft-nrg.com
SOFTNET_WRITE_AUTH_URL=https://int-auth.soft-nrg.com/oauth/token
SOFTNET_TIMEOUT=10000

# SoftNet IDs
GROUP_ID=698b34c24dd8eea4d6d42179
BRANCH_ID=58341c5cbe856f6dc47e9f05

# Session
SESSION_SECRET=<generate-with-openssl-rand-hex-32>
SESSION_TTL_MINUTES=20

# Fingerprint files (relative to project root)
READ_FINGERPRINT_FILE=./server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json
WRITE_FINGERPRINT_FILE=./server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json
```

---

## 5. Step-by-Step Execution Plan

### Phase 1: Prerequisites (~15 min)

| Step | Command | What it does |
|------|---------|-------------|
| 1.1 | `az login` | Authenticate with Azure |
| 1.2 | `az account set --subscription "<ID>"` | Select the target subscription |
| 1.3 | `az extension add --name containerapp --upgrade` | Ensure Container Apps CLI extension is installed |
| 1.4 | `az provider register --namespace Microsoft.App` | Register the Container Apps resource provider |
| 1.5 | `az provider register --namespace Microsoft.OperationalInsights` | Register Log Analytics provider |

### Phase 2: Initial Deployment (~30 min)

| Step | Command | What it does |
|------|---------|-------------|
| 2.1 | Copy `.env.template` to `.env`, fill in values | Configure secrets |
| 2.2 | `./deploy.sh setup` | Creates RG, ACR, Environment, builds image, deploys app |
| 2.3 | Open the printed URL in browser | Verify the app loads |
| 2.4 | Test booking flow end-to-end | Verify SoftNet API integration works |

### Phase 3: Subsequent Updates (~5 min)

| Step | Command | What it does |
|------|---------|-------------|
| 3.1 | Make code changes locally | Fix bugs, add features |
| 3.2 | `./deploy.sh update` | Rebuild image + deploy new revision |

### Phase 4: Teardown (when OpenShift is ready)

| Step | Command | What it does |
|------|---------|-------------|
| 4.1 | `./deploy.sh teardown` | Deletes entire Resource Group + all resources |

---

## 6. Potential Issues & Mitigations

### 6.1 SoftNet API — Outbound IP

**Issue:** SoftNet might block requests from Azure datacenter IPs.

**Mitigation:**
```bash
# Get the outbound IPs of the Container App
az containerapp show \
  --name autohaus-app \
  --resource-group rg-autohaus-poc \
  --query "properties.outboundIpAddresses" -o tsv
```
→ Provide these IPs to SoftNet for whitelisting if needed.

### 6.2 Cold Start Latency

**Mitigiert:** Mit `minReplicas: 1` läuft immer eine Instanz. Kein Cold Start für den Kunden. Die zusätzlichen Kosten (~10 €/Monat Idle-Rate) sind für ein POC akzeptabel.

> Falls das POC länger nicht gebraucht wird, kann man temporär auf `minReplicas: 0` umstellen:
> ```bash
> az containerapp update --name autohaus-app --resource-group rg-autohaus-poc --min-replicas 0
> ```

### 6.3 Fingerprint Files

**Issue:** The Spring Boot app expects fingerprint JSON files on the filesystem, not as environment variables.

**Mitigation options:**
1. **Base64 env var + init script:** Pass fingerprint as Base64 env var, decode on container start. Requires modifying the ENTRYPOINT.
2. **Azure Files volume mount:** Mount an Azure File Share with the fingerprint files. More complex setup.
3. **Modify Spring Boot to accept inline JSON:** Read from env var instead of file. Cleanest but requires code change.

**Recommendation:** Option 1 (Base64 env var) — minimal code change, just modify the ENTRYPOINT:

```dockerfile
ENTRYPOINT ["sh", "-c", "\
  if [ -n \"$SOFTNET_READ_FINGERPRINT_BASE64\" ]; then \
    echo \"$SOFTNET_READ_FINGERPRINT_BASE64\" | base64 -d > /app/config/read-fingerprint.json; \
  fi && \
  if [ -n \"$SOFTNET_WRITE_FINGERPRINT_BASE64\" ]; then \
    echo \"$SOFTNET_WRITE_FINGERPRINT_BASE64\" | base64 -d > /app/config/write-fingerprint.json; \
  fi && \
  exec java $JAVA_OPTS -jar /app/app.jar"]
```

### 6.4 CORS Configuration

**Issue:** The Container App URL is only known after deployment, but CORS needs it.

**Mitigation:** The deploy script handles this — it first deploys, then updates `CORS_ALLOWED_ORIGINS` with the actual URL in a second step.

---

## 7. Comparison with Alternatives

| Approach | Effort | Monthly Cost | Custom Domain | Scale-to-Zero | Verdict |
|---|---|---|---|---|---|
| **Azure Container Apps** | ~2h | ~15 € | Auto `*.azurecontainerapps.io` | ✅ (optional) | **Recommended** |
| Azure App Service (Container) | ~2h | ~15-50 € | Auto `*.azurewebsites.net` | ❌ (always running) | More expensive, no scale-to-zero |
| Azure Container Instance (ACI) | ~1h | ~10-20 € | No auto-domain | ❌ | No auto HTTPS, manual IP mgmt |
| Azure Kubernetes Service (AKS) | ~4-8h | ~70+ € | Manual | ✅ (with KEDA) | Way overkill for POC |
| Azure Static Web Apps + separate backend | ~3h | ~0 € frontend | Auto domain | N/A | Splits architecture, more complex |

**Azure Container Apps is the clear winner** for this use case: minimal effort, cheapest, auto-HTTPS, auto-domain, scale-to-zero.

---

## 8. Timeline

| Day | Activity |
|-----|----------|
| Day 1 | Verify Azure subscription access, install Azure CLI, create `.env` |
| Day 1 | Run `./deploy.sh setup`, test the URL |
| Day 1 | Fix any SoftNet outbound IP issues if needed |
| Day 1 | Share URL with customer |

**Total: ~half a day** including troubleshooting.

---

## 9. Files to Create

| File | Purpose |
|------|---------|
| `infra/azure-poc/deploy.sh` | Main deployment script |
| `infra/azure-poc/.env.template` | Environment template (committed) |
| `infra/azure-poc/.env` | Actual secrets (gitignored) |
| `infra/azure-poc/README.md` | Usage documentation |

---

## 10. Decision Required

Before implementation, the following decisions are needed:

1. **Azure Subscription ID** — Which subscription to deploy to?
2. **SoftNet Environment** — Use INT or PROD API endpoints?
3. **Access Control** — Should the POC be publicly accessible, or protected (e.g., Azure AD / Basic Auth)?
4. **ACR Name** — The registry name `acrautohauspoc` must be globally unique. Confirm or choose alternative.
