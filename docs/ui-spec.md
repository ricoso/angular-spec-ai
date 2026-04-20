# Requirements Board System Specification

Spring Boot MVC Backend + Angular Frontend + Dev Container Development

---

## 1. Overview

This project defines a **local-first Requirements Management System** running as a **Spring Boot web application** with an **Angular frontend**.

It provides a **Kanban board UI for managing project requirements**, implemented as:

- Spring Boot Backend (MVC, REST API)
- Angular Frontend (served as static files)
- Dev Container development environment
- Local filesystem storage
- Status polling every 60 seconds

This is **not a cloud service** and **not a full project management tool**.

It is a **lightweight developer workflow tool**.

---

## 2. Core Goals

The system must allow developers to:

- Open the board in a browser (`http://localhost:3200`)
- View requirements as Kanban cards
- Create requirements
- Edit requirements
- Move requirements between statuses via drag & drop
- Trigger workflow commands via REST API
- Enrich cards with metadata
- Auto-refresh status every 60 seconds (polling)
- Store everything locally in the repository

---

## 3. Runtime Environment

The system runs in:

- Dev Container (development)
- Local machine (production)
- Any Node.js environment

Typical workflow:

1. Open repository in VS Code
2. Reopen in Dev Container
3. Run `npm run board:start`
4. Open `http://localhost:3200` in browser
5. Kanban board UI appears
6. Board reads REQUIREMENTS.md from filesystem
7. Status auto-refreshes every 60 seconds

---

## 4. Architecture

### Spring Boot Backend (MVC)

Responsibilities:

- REST API for requirements CRUD
- Markdown parsing (REQUIREMENTS.md)
- Metadata reading (metadata.json)
- Workflow script execution
- Serves Angular static files

### Angular Frontend

Responsibilities:

- Board rendering
- Drag and drop (CDK)
- Dialogs and forms
- Cards with metadata badges
- HTTP polling every 60 seconds

### REST API

| Method | Route | Description |
|--------|-------|-------------|
| GET | `/api/requirements` | List all requirements |
| POST | `/api/requirements` | Create new requirement |
| PUT | `/api/requirements/:id/status` | Update status |
| POST | `/api/workflow/:type/:reqId` | Trigger workflow |

### Local Filesystem

Source of truth:

```
docs/requirements/REQUIREMENTS.md
docs/requirements/REQ-XXX-Name/
```

### Workflow Scripts

```
scripts/create-requirement.sh
scripts/implement-requirement.sh
scripts/update-requirement.sh
```

---

## 5. Kanban Board

Columns:

| Column | Description |
|--------|-------------|
| To-Do | Neue Requirements (Draft, Approved) |
| In Progress | In Bearbeitung |
| In Review | Review laeuft |
| Done | Abgeschlossen (Implemented, Rejected) |

Cards represent requirements.

Card actions:

- **Drag & Drop** -- Statuswechsel (PUT /api/requirements/:id/status)
- **Click** -- Details anzeigen / Editieren

---

## 6. Requirement Creation

Toolbar includes a **+ Button** (`mat-flat-button`).

### Create Dialog Fields

| Feld | Pflicht | Beschreibung |
|------|---------|--------------|
| Title | Ja | Requirement-Titel |
| Description | Ja | Kurzbeschreibung |
| Priority | Nein | High / Medium / Low |
| Tags | Nein | Freitext-Tags (Chips) |

### Creation Process

1. Angular sends POST `/api/requirements`
2. Backend generates next REQ-ID
3. Adds entry to REQUIREMENTS.md
4. Returns new requirement
5. Board updates optimistically

---

## 7. Drag & Drop Status

Moving cards updates the **Status** field in REQUIREMENTS.md via PUT API.

| Ziel-Spalte | Neuer Status |
|-------------|-------------|
| To-Do | Draft |
| In Progress | In Progress |
| In Review | In Review |
| Done | Implemented |

---

## 8. Polling

The Angular frontend polls `GET /api/requirements` every **60 seconds** to detect external changes to REQUIREMENTS.md (e.g., from git pull, manual edits, or Claude Code commands).

---

## 9. Workflow Integration

Workflow commands execute through the **Spring Boot backend** via REST API.

```
POST /api/workflow/create/:reqId
POST /api/workflow/implement/:reqId
POST /api/workflow/update/:reqId
```

Backend executes shell scripts in the workspace root.

> Commands **never execute from Angular UI** directly.

---

## 10. Source of Truth

Primary file: `docs/requirements/REQUIREMENTS.md`

Format: Markdown table with emoji status indicators.

---

## 11. Requirement Folder Model

Each requirement may have a folder:

```
docs/requirements/REQ-XXX-Name/
  requirement.md
  metadata.json
  screenshots/
    mockup.png
```

---

## 12. Metadata Format

Example `metadata.json`:

```json
{
  "priority": "high",
  "tags": ["auth", "ux"],
  "prNumber": 84,
  "reviewState": "open",
  "lastUpdated": "2026-03-11T10:30:00Z"
}
```

---

## 13. Card Layout

### Top Line

```
REQ-012  Improve login flow
```

### Second Line

Short preview text (truncated description).

### Bottom Row -- Badges

| Badge | Inhalt |
|-------|--------|
| Priority | `High` / `Medium` / `Low` (farbig) |
| Tags | Material Chips |
| Attachment | Icon wenn Dateien vorhanden |
| PR | Icon + PR-Nummer wenn verknuepft |

---

## 14. Angular Components

| Component | Aufgabe |
|-----------|---------|
| `BoardContainerComponent` | Container, inject(Store), Polling, Layout |
| `ColumnComponent` | CDK Drop List, Spalte mit Cards |
| `CardComponent` | CDK Drag, Requirement-Karte |
| `ToolbarComponent` | Create + Refresh Buttons |
| `MetadataBadgesComponent` | Priority, Tags, Attachments, PR |
| `RequirementFormComponent` | Reactive Form, Create/Edit Dialog |

---

## 15. NgRx Signal Store

```typescript
BoardStore {
  state: requirements, isLoading, error, selectedRequirement, dialogMode
  computed: todoRequirements, inProgressRequirements, inReviewRequirements, doneRequirements
  methods: loadRequirements(), moveRequirement(), createRequirement(), openDialog(), closeDialog()
}
```

Communicates with Spring Boot backend via `BoardApiService` (HttpClient).

---

## 16. Dev Container

Development runs inside `.devcontainer/`.

Container includes:

- Node.js
- Angular CLI
- Spring Boot CLI
- GitHub CLI

Port forwarding:

- 4200: Angular Dev Server (Haupt-App)
- 3200: Requirements Board

---

## 17. Project Structure

```
tools/requirements-board/
  build.gradle              # Spring Boot dependencies
  settings.gradle           # Gradle settings
  src/
    main/
      java/                 # Spring Boot Backend
        Application.java
        board/
          BoardController.java
          BoardService.java
        workflow/
          WorkflowController.java
          WorkflowService.java
      resources/
        application.yml     # Spring Boot config
    test/                   # Backend tests

  client/                   # Angular Frontend
    angular.json
    package.json
    src/
      main.ts
      index.html
      styles.scss
      app/
        app.component.ts/.html/.scss
        store/board.store.ts
        services/board-api.service.ts
        components/
          board/board-container.component.*
          column/column.component.*
          card/card.component.*
          toolbar/toolbar.component.*
          metadata-badges/metadata-badges.component.*
          requirement-form/requirement-form.component.*

scripts/
  create-requirement.sh
  implement-requirement.sh
  update-requirement.sh
```

---

## 18. Commands

```bash
npm run board:install      # Install all dependencies
npm run board:build        # Build server + client
npm run board:start        # Start dev server (watch mode)
npm run board:start:prod   # Build + start production
```

---

## 19. Security Rules

**Never commit:**

- `.env`, `.env.local`, `.env.production`
- API keys, tokens, credentials

Workflow execution happens only on the Spring Boot server layer.

---

## 20. Implementation Principles

The system must remain:

- **Simple** -- Spring Boot MVC + Angular, keine ueberkomplexe Architektur
- **Local-first** -- kein Cloud-Dienst
- **Secure** -- keine Secrets im Frontend
- **Readable** -- klarer, wartbarer Code
- **Polling-based** -- 60s Intervall fuer Status-Sync

---

## 21. Deliverables

- [x] Spring Boot Backend with REST API
- [x] Angular Frontend with Kanban Board
- [x] CDK Drag & Drop
- [x] NgRx Signal Store
- [x] 60s Polling
- [x] REQUIREMENTS.md Parsing
- [x] Metadata enrichment
- [x] Workflow script execution
- [x] Dev Container configuration
- [x] Build & run scripts
