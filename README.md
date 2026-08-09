# REAPER

> **Build it. Ship it.**

REAPER is a full-stack project generator that scaffolds a production-shaped application on your machine and then ships it to the cloud for you. Pick your stack from an interactive terminal menu, and REAPER creates the project, configures it, packages it, and deploys it to Zerops — no cloud dashboards, no Dockerfiles, no `kubectl`.

---

## Table of Contents

- [Demo Video](#demo-video)
- [Judge Demo Account](#judge-demo-account)
- [Hackathon Quick Start](#hackathon-quick-start)
- [Overview](#overview)
- [Key Features](#key-features)
- [Supported Stacks](#supported-stacks)
- [Architecture](#architecture)
- [Component Responsibilities](#component-responsibilities)
- [Deployment Lifecycle](#deployment-lifecycle)
- [REST API](#rest-api)
- [Local Setup](#local-setup)
- [Running the Engine](#running-the-engine)
- [Running the CLI](#running-the-cli)
- [Environment Configuration](#environment-configuration)
- [Try a Live Deployment](#try-a-live-deployment)
- [Demo Project](#demo-project)
- [Failure Demo](#failure-demo)
- [Testing](#testing)
- [Security](#security)
- [Known Limitations](#known-limitations)
- [Project Structure](#project-structure)
- [3–5 Minute Demo Flow](#35-minute-demo-flow)
- [Troubleshooting](#troubleshooting)
- [GitHub Rules](#github-rules)

---

## Demo Video

VIDEO_URL_HERE

---

## Judge Demo Account

| Field | Value |
| --- | --- |
| Email | DEMO_EMAIL_HERE |
| Password | DEMO_PASSWORD_HERE |

---

## Hackathon Quick Start

1. Open two terminals.
2. **Terminal 1 — start the engine:**
   ```bash
   cd zerops-deploy-engine
   mvn clean package
   java -jar target/stackd-ignition-0.1.0-SNAPSHOT.jar
   ```
3. **Terminal 2 — scaffold a project (no cloud credentials needed):**
   ```bash
   go build -o reaper .
   ./reaper create
   ```
4. Enter a project directory (e.g. `./some-demo`), choose **Frontend + Backend**, then:
   - Frontend: **React (JavaScript)**
   - UI: **TailwindCSS**
   - Backend: **ExpressJS**
   - ORM: **Prisma**
   - Database: **postgresql**
   - Database URL: any valid PostgreSQL connection string (the scaffold runs locally with Docker Compose)
   - Authentication: **JWT**
5. At **"Deploy this to Zerops now?"**, choose **No** for the local demo — your project is scaffolded, wired, and ready to run locally.
6. Watch the generated `frontend/` + `backend/` structure appear with a working React + Express + Prisma + PostgreSQL stack.

For the live cloud demo, see [Try a Live Deployment](#try-a-live-deployment).

---

## Overview

REAPER is two pieces of software that talk to each other:

- **A Go CLI** — an interactive scaffolder. It asks what stack you want and generates a complete, runnable full-stack project (frontend, backend, ORM, database config, auth) on your machine. It also prompts whether you want the project deployed to Zerops.
- **A Java (Spring Boot) engine** — "REAPER Ignition". When you say yes to a deployment, the CLI sends the project path to the engine over HTTP. The engine reads the project from disk, inspects it, derives a Zerops deployment configuration, packages the code, deploys backend and frontend to Zerops through its public REST API, polls for readiness, health-checks the live URL, and reports the result back to the terminal.

The terminal session mirrors the whole journey: the engine streams state transitions (`ANALYZING → CONFIGURING → DEPLOYING → HEALTH_CHECKING → HEALTHY`) and finally prints the live URL.

---

## Key Features

- **Interactive scaffolding** — arrow-key driven prompts (no YAML, no hand-written Dockerfiles).
- **Three project modes**:
  - `Frontend + Backend` — React + Express or React + Express (TypeScript), with Prisma/Drizzle, PostgreSQL/MySQL/MongoDB, and JWT auth.
  - `Full Stack Frameworks` — Next.js or Django, with ORM + auth.
  - `MonoRepos` — Turborepo.
- **Instant deploy to Zerops** — the engine builds a Zerops project import file (`zerops.yaml`) from a lookup table, injects the database service and credentials, uploads the code, and manages the full deploy + health check.
- **Live status in the terminal** — polling with back-off, printing each state change (2s interval, 10-minute timeout, state changes only).
- **Safe-by-default deploy prompt** — "Deploy this to Zerops now?" defaults to **No**; pressing Enter can never accidentally trigger a cloud deploy.
- **Secrets handled carefully** — database URLs and API tokens are never logged; local `.env` values are layered under injected Zerops values.
- **A full marketing site** — the repo ships a Next.js landing page (hero, features, how it works, infrastructure, metrics, integrations, security, developers, testimonials, pricing, CTA, footer).
- **Structured failure reporting** — machine-readable error codes and messages from every failure mode.

---

## Supported Stacks

| Dimension | Supported options |
| --- | --- |
| Project type | Frontend + Backend · Full Stack Frameworks · MonoRepos |
| Frontend (FB mode) | React (JavaScript) · React (TypeScript) · None |
| UI (FB mode) | TailwindCSS · TailwindCSS + ShadCN |
| Backend (FB mode) | ExpressJS · ExpressTS · None |
| ORM | Prisma · Drizzle ORM |
| Database | PostgreSQL · MySQL · MongoDB · NoDB |
| Auth | JWT · None |
| Full Stack frameworks | Next.js · Django |
| Monorepo frameworks | Turborepo · None |

The cloud-deploy analyzer detects the actual stack in a scaffolded (or any compatible) project:

- **Frontend**: none, React (JS/TS), Next.js, Vue, Angular
- **Backend**: none, Express (JS/TS), Django REST Framework
- **Database**: none, PostgreSQL, MySQL, MongoDB
- **ORM**: Prisma, Drizzle
- **Auth**: JWT, NextAuth, Passport

Zerops runtime templates exist for: ExpressJS, ExpressTS, Django REST Framework, Next.js, Vite (React/Vue/Angular → static), plus PostgreSQL/MySQL/MongoDB service provisioning.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                            Your Terminal                                  │
│                                                                            │
│   ┌─────────────────────────────┐      HTTP (JSON)      ┌─────────────────┐ │
│   │  REAPER CLI (Go)            │  ───────────────────▶ │  REAPER Ignition │ │
│   │  · interactive prompts      │  POST /api/v1/deployments               │ │
│   │  · scaffold generator       │                      │  (Spring Boot)  │ │
│   │  · deploy client + poller   │  ◀─────────────────── │  · analyzer     │ │
│   │                            │  status / live URL    │  · env manager   │ │
│   └────────────┬────────────────┘                      │  · config gen    │ │
│                │ generates                              │  · deploy svc    │ │
│                ▼                                        │  · zerops client │ │
│   ┌─────────────────────────────┐                      │  · health check  │ │
│   │  Scaffolded project on disk │                      └────────┬─────────┘ │
│   │  (frontend/ + backend/ + .env)                              │           │
│   └─────────────────────────────┘                               │ HTTPS     │
│                                                                 ▼           │
│                                                  ┌─────────────────────────┐ │
│                                                  │   Zerops Public REST    │ │
│                                                  │   API  (cloud)          │ │
│                                                  │   project import, build │ │
│                                                  │   deploy, poll, service │ │
│                                                  │   info                  │ │
│                                                  └─────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

Flow: **CLI scaffolds** → on deploy request, **CLI sends the project path** → **Ignition reads and analyzes the project from disk** → **builds Zerops config + env** → **packages and uploads** → **deploys backend, then frontend** → **polls with back-off** → **health-checks the public HTTPS URL** → returns `liveUrl` → **CLI prints it**.

---

## Component Responsibilities

| Component | Responsibility |
| --- | --- |
| CLI prompts (`internal/*/prompt*`) | Arrow-key and text-input TUI menus for every scaffold choice. |
| CLI executors (`internal/*/executors*`) | Write the actual files: Vite app, Tailwind config, Express server, Prisma schema, `.env`, Docker Compose, JWT middleware, README. |
| Deploy client (`internal/deploy`) | Talks to Ignition over HTTP; polls status every 2s (10-min cap), printing only on state change; mirror-displays engine states. |
| `ArchitectureAnalyzer` | Reads the project directory, classifies frontend/backend/database/ORM/auth, and rejects unsupported layouts with a specific error code. |
| `EnvConfigManager` | Reads the project `.env` (root or `backend/`), 256KB cap, no symlink following; requires `DATABASE_URL` for database stacks; merges values so injected Zerops secrets win over local ones; never exposes values in logs. |
| `ZeropsConfigGenerator` | Renders the Zerops project config from template files using a lookup table (no branching); unresolved `{{placeholder}}` fails loudly; provisions DB services and wires cross-service credentials. |
| `ZeropsClient` | Authenticated (`Bearer` token) client for the Zerops public REST API; executes project import, deploy, polling, and service info; never logs the token or payload bodies. |
| `DeploymentService` | Orchestrates analyze → env → package/upload → deploy (backend first, then frontend) → poll with bounded back-off → health check; runs async on a bounded executor; records `liveUrl` only once healthy. |
| `HealthCheckService` | Single unauthenticated HTTPS `GET` against the live host; any 2xx = healthy; non-2xx / timeout / invalid = `HealthCheckException` (no retries, 10s connect timeout). |
| `DeploymentStore` | In-memory registry of deployments and their state transitions. |
| `GlobalExceptionHandler` | Normalizes every failure into `{"error": true, "code": ..., "message": ...}` with correct HTTP status. |

---

## Deployment Lifecycle

```
PENDING → ANALYZING → CONFIGURING → DEPLOYING → HEALTH_CHECKING → HEALTHY
   │
   └── any non-terminal state can fail → FAILED
```

- **PENDING** — request accepted (HTTP 202).
- **ANALYZING** — project structure classified.
- **CONFIGURING** — env file merged, Zerops config generated.
- **DEPLOYING** — backend uploaded and started, then frontend.
- **HEALTH_CHECKING** — public HTTPS URL probed.
- **HEALTHY** — `liveUrl` available. Terminal.
- **FAILED** — reached from any non-terminal state; `errorCode` + `message` explain why. Terminal.

Progress is reported to the CLI by polling `GET /api/v1/deployments/{id}/status`; the CLI prints a line only when the state changes.

---

## REST API

Base path: `http://localhost:8080/api/v1` (configurable via the CLI's `STACKD_IGNITION_URL`).

| Method | Path | Description | Success |
| --- | --- | --- | --- |
| GET | `/health` | Liveness. | `200` → `{"status":"ok"}` |
| POST | `/deployments` | Start a deployment. Body: `{"projectPath": "...", "zeropsProjectId": "..."}` (both required). | `202` → full deployment object |
| GET | `/deployments/{deploymentId}` | Full deployment details. | `200` |
| GET | `/deployments/{deploymentId}/status` | Status-only view (`deploymentId`, `status`, `message`, `errorCode`, `liveUrl`). | `200` |
| GET | `/deployments/{deploymentId}/health` | Same status-only view. | `200` |

**Request example:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{"projectPath":"./some-demo","zeropsProjectId":"YOUR_PROJECT_ID"}'
```

**Error format** (every failure):
```json
{ "error": true, "code": "ZEROPS_DEPLOY_TIMEOUT", "message": "..." }
```

| HTTP | Code | Meaning |
| --- | --- | --- |
| 404 | `DEPLOYMENT_NOT_FOUND` | Unknown deployment id (also after an engine restart — see Known Limitations). |
| 409 | `DEPLOYMENT_ALREADY_EXISTS` | A deployment for the same path is already in flight. |
| 409 | `INVALID_STATE_TRANSITION` | Illegal status move attempted. |
| 400 | `PROJECT_PATH_INVALID` / `MISSING_REQUIRED_ENV_VARS` / `UNREADABLE_ENV_FILE` | Env/config validation failures. |
| 400 | `NOT_A_STACKD_PROJECT` / `AMBIGUOUS_STACK` / `UNSUPPORTED_LAYOUT` / `UNREADABLE_PROJECT` | Project analysis failures. |
| 502 | `ZEROPS_API_ERROR` | Zerops rejected the request (e.g. bad token → 401). |
| 504 | `ZEROPS_API_TIMEOUT` | A Zerops call timed out. |
| 502 | `ZEROPS_API_UNREACHABLE` | Three consecutive transient failures against Zerops. |
| 502 | `ZEROPS_SERVICE_NOT_FOUND` | Deployed service lookup failed. |
| 502 | `ZEROPS_DEPLOY_FAILED` / `ZEROPS_DEPLOY_TIMEOUT` | Build/deploy failed or exceeded the timeout. |
| 502 | `ZEROPS_RESPONSE_MALFORMED` | Unexpected response shape from Zerops. |

---

## Local Setup

**Prerequisites**

| Tool | Version used to build this repo |
| --- | --- |
| Go | 1.24.4 (`go.mod`; tested with Go 1.26.5) |
| Java | 17+ (`pom.xml` targets Java 17; built with JDK 21) |
| Maven | 3.9.x |
| Node.js | 22.x (scaffolds target Node 22) |
| npm | 10.x |
| pnpm | 11.x (for the bundled Next.js marketing site) |
| Docker (optional) | Only if you want to run the scaffolded PostgreSQL locally |

No cloud credentials are needed to scaffold or to run the whole test suite.

---

## Running the Engine

```bash
cd zerops-deploy-engine
mvn clean package
java -jar target/stackd-ignition-0.1.0-SNAPSHOT.jar
```

- Listens on port `8080` by default (`IGNITION_PORT` to change).
- Loads `zerops-deploy-engine/.env` if present (see [Environment Configuration](#environment-configuration)).
- Spring Boot 3.5.16, Java 17, actuator endpoints exposed for health/metrics.

Quick sanity check:
```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok"}
```

---

## Running the CLI

```bash
# from the repo root
go build -o reaper .
./reaper create
```

The interactive flow (arrow keys + Enter):

1. **Enter project directory** — e.g. `./some-demo`
2. **Select Project Type** — `Frontend + Backend` / `Full Stack Frameworks` / `MonoRepos`
3. Depending on your choice, you'll pick through the menus in [Supported Stacks](#supported-stacks): frontend framework, UI framework, backend framework, ORM, database type, database URL, and auth method.
4. **"Deploy this to Zerops now?"** — defaults to **No**. Choose **Yes** only when you want a live cloud deployment (see next section).

The CLI uses `STACKD_IGNITION_URL` (default `http://localhost:8080`) to reach the engine.

---

## Environment Configuration

**Engine (`zerops-deploy-engine/.env`)** — copy from `.env.example`:

| Variable | Required for live deploys | Default | Purpose |
| --- | --- | --- | --- |
| `ZEROPS_API_TOKEN` | yes | — | Zerops personal access token (Bearer auth to the API). |
| `ZEROPS_PROJECT_ID` | yes | — | Zerops project to deploy into. |
| `IGNITION_PORT` | no | `8080` | Engine HTTP port. |
| `ZEROPS_API_BASE_URL` | no | Zerops public API | API base URL override. |
| `ZEROPS_API_TIMEOUT_MS` | no | — | Per-call timeout to Zerops. |
| `IGNITION_POLL_INTERVAL_MS` | no | `5000` | Deploy poll interval. |
| `IGNITION_MAX_POLL_INTERVAL_MS` | no | `60000` | Back-off ceiling for polling. |
| `IGNITION_POLL_TIMEOUT_MS` | no | `300000` | Total deploy wait before `ZEROPS_DEPLOY_TIMEOUT`. |
| `IGNITION_HEALTH_TIMEOUT_MS` | no | `30000` | Health-check deadline. |
| `IGNITION_DEPLOY_EXECUTOR_POOL_SIZE` | no | `4` | Concurrent deployment slots. |
| `POSTGRES_*` / `MYSQL_*` / `MONGO_*` | no | — | Optional custom service provisioning blocks. |

The engine loads `.env` via Spring's `optional:file:.env` import; secrets are read from the environment, never written into the packaged jar.

**CLI** — `STACKD_IGNITION_URL` points the CLI at the engine (default `http://localhost:8080`).

**Scaffolded project** — REAPER writes a `.env` into the generated project with the database URL you entered, plus a `docker-compose.yml` to run the database locally.

---

## Try a Live Deployment

> Requires a real Zerops account with a project id and an API token.

1. Start the engine with credentials configured:
   ```bash
   cd zerops-deploy-engine
   cp .env.example .env
   # edit .env: set ZEROPS_API_TOKEN and ZEROPS_PROJECT_ID
   mvn clean package && java -jar target/stackd-ignition-0.1.0-SNAPSHOT.jar
   ```
2. In a second terminal, scaffold with deploy enabled:
   ```bash
   go build -o reaper .
   ./reaper create
   ```
3. Choose your stack, then answer **Yes** at **"Deploy this to Zerops now?"**.
4. Watch the terminal stream: `ANALYZING → CONFIGURING → DEPLOYING → HEALTH_CHECKING → HEALTHY`, then the **live HTTPS URL** is printed.
5. Open the URL — the engine health-checked it (`2xx`) before reporting success.

The engine handles the full Zerops lifecycle for you: project import file generation (service templates for your exact stack), database service provisioning with cross-service credential injection, code packaging/upload, sequential backend-then-frontend deploy, back-off polling, and health verification.

---

## Demo Project

A React + ExpressJS + Prisma + PostgreSQL scaffold looks like this:

```
some-demo/
├── frontend/            # Vite React app (Tailwind configured, wired to the backend)
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
├── backend/
│   ├── prisma/          # schema.prisma + generated client
│   ├── index.js         # Express server (JWT middleware included)
│   ├── .env             # DATABASE_URL
│   └── package.json
├── docker-compose.yml   # local PostgreSQL service
├── .env
└── README.md            # per-project run instructions
```

To run the scaffold locally: `docker compose up -d`, then start `backend` and `frontend` with their respective `npm run dev` scripts.

The repo also includes a bundled **marketing site** at `web/` (Next.js + React 19) with sections for hero, features, how-it-works, infrastructure, metrics, integrations, security, developers, testimonials, pricing, CTA, and footer.

---

## Failure Demo

A deliberate failure path is easy to trigger and produces clean, structured output:

- **Invalid project path**: POST a deployment with a nonexistent `projectPath` → `PROJECT_PATH_INVALID` (400).
- **Missing engine credentials**: run a live deploy without `ZEROPS_API_TOKEN` / `ZEROPS_PROJECT_ID` → the env manager fails fast with `MISSING_REQUIRED_ENV_VARS`.
- **Invalid Zerops token**: keep the token wrong → `ZEROPS_API_ERROR` with the HTTP status from Zerops (e.g. 401). The engine logs the operation and status only — never the token.
- **Unsupported layout**: point the analyzer at a directory it can't classify → `UNSUPPORTED_LAYOUT` or `NOT_A_STACKD_PROJECT`.
- **Engine restart mid-flight**: deployments are stored in memory, so a restart makes old ids unknown → `DEPLOYMENT_NOT_FOUND`.

Every failure returns the standard `{"error":true,"code":...,"message":...}` shape so a demo can read the code straight off the terminal.

---

## Testing

Run each suite from the repo root:

```bash
# Go CLI: unit tests + regression tests for the scaffolder
go build ./...
go vet ./...
go test ./...

# Engine: Spring Boot test suite (controllers, analyzer, env manager,
# config generator, health check, error mapping)
cd zerops-deploy-engine && mvn test

# Marketing site: production build + TypeScript check
cd web && pnpm install && pnpm exec tsc --noEmit && pnpm build
```

Current verified status: **Spring suite green (155 tests, 0 failures)**, Go `build`/`vet`/`test` all pass, Next.js production build and TypeScript check pass.

Regression coverage of note: the scaffolder's Vite command is unit-tested for both absolute and relative project directories, and the full scaffold path was smoke-tested end-to-end (no deploy) producing byte-identical structures for relative and absolute paths.

---

## Security

- **No secrets in logs** — API tokens and request/response payloads are never logged by the engine; Zerops error messages describe only the operation and HTTP status.
- **Environment layering** — Zerops-injected values override the local `.env`; merged values are masked in every readable representation.
- **Safe deploy prompt** — "Deploy this to Zerops now?" defaults to **No**.
- **Bounded file reads** — project env files are read with a 256KB cap; symlinks are not followed.
- **No credentials in the repo** — only `.env.example` templates are committed; `.env` files are git-ignored.
- **Fail-closed config generation** — unresolved `{{placeholder}}` tokens abort generation instead of shipping a broken config.

---

## Known Limitations

- **In-memory deployment store** — deployments don't survive an engine restart (`DEPLOYMENT_NOT_FOUND` afterwards).
- **Live deploys need real credentials** — a live Zerops deploy requires a valid `ZEROPS_API_TOKEN` and `ZEROPS_PROJECT_ID`; without them the request fails fast.
- **Defined layout set** — the analyzer recognizes the supported layouts above; unusual structures are rejected with `UNSUPPORTED_LAYOUT`.
- **Single health probe** — health checking is one HTTPS `GET` expecting `2xx`; no retries and no content checks.
- **External dependency** — live deploys depend on Zerops API availability and rate limits.

---

## Project Structure

```
REAPER/
├── cmd/                          # Cobra CLI entry (create command, banner)
├── internal/
│   ├── fb/                       # Frontend + Backend mode
│   │   ├── promptfb/             #   interactive prompts
│   │   └── executorsfb/          #   file generation (frontend, UI, backend, ORM, auth)
│   ├── fs/                       # Full Stack mode (Next.js / Django)
│   ├── monorepo/                 # MonoRepos mode (Turborepo)
│   ├── deploy/                   # engine HTTP client, polling, confirm prompt, display
│   └── utils/                    # shared prompts (dir, type, ORM, DB type, DB URL, directories)
├── zerops-deploy-engine/         # REAPER Ignition — Spring Boot engine
│   ├── src/main/java/com/stackd/ignition/
│   │   ├── api/                  #   controllers, DTOs, error handler
│   │   ├── analyzer/             #   ArchitectureAnalyzer, DetectedStack
│   │   ├── envmanager/           #   EnvConfigManager
│   │   ├── zeropsconfig/         #   ZeropsConfigGenerator + runtime templates
│   │   ├── deployment/           #   DeploymentService, ZeropsClient, exceptions
│   │   ├── health/               #   HealthCheckService
│   │   └── status/               #   Deployment, DeploymentStatus
│   ├── src/main/resources/zeropsconfig/   # zerops.yaml templates per stack
│   ├── .env.example / application.yml
│   └── status.md                 # stage report on the deploy lifecycle
├── web/                          # Next.js marketing site (landing page)
├── go.mod / go.sum               # Go 1.24.4, cobra, bubbletea, huh, lipgloss
└── README.md
```

---

## 3–5 Minute Demo Flow

1. **Open** — show `go.mod`, `pom.xml`, and the engine + CLI directory layout (30s).
2. **Local scaffold** — `./reaper create`, walk the menus, choose the React + Express + Prisma + PostgreSQL stack, answer **No** at the deploy prompt; show the generated `frontend/` + `backend/` tree (90s).
3. **Failure demo** — point a deploy at a nonexistent path and show the structured `PROJECT_PATH_INVALID` error; or start the engine without credentials and show `MISSING_REQUIRED_ENV_VARS` (60s).
4. **Live deploy** — with credentials configured, run the same scaffold answering **Yes**, stream the lifecycle to **HEALTHY**, and open the printed live URL (90s–2min).
5. **Wrap** — run `go test ./...` and `mvn test` to show the suites green (30s).

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `curl /api/v1/health` fails | Engine not running; start it with `java -jar target/stackd-ignition-0.1.0-SNAPSHOT.jar`. |
| CLI can't reach the engine | Check `STACKD_IGNITION_URL`; default is `http://localhost:8080`. |
| Deploy fails with `ZEROPS_API_ERROR` (401) | Wrong/expired `ZEROPS_API_TOKEN` in `zerops-deploy-engine/.env`. |
| Deploy fails with `MISSING_REQUIRED_ENV_VARS` | `ZEROPS_API_TOKEN` or `ZEROPS_PROJECT_ID` not set. |
| `DEPLOYMENT_NOT_FOUND` after a restart | In-memory store; the deployment pre-dates the engine restart. |
| `UNSUPPORTED_LAYOUT` / `NOT_A_STACKD_PROJECT` | The directory isn't a layout the analyzer recognizes. |
| Database prompt rejects empty URL | A database URL is required when a database type is selected. |
| Scaffold hangs at Vite install | First run downloads npm packages; give it a moment. |

---

## GitHub Rules

This repository is shared and graded from the public GitHub repo. Rules we follow here:

- **No real credentials** — API tokens, passwords, or live account secrets must never be committed. Only `.env.example` templates live in the repo; `.env` files are git-ignored.
- **No generated junk** — build outputs (`target/`, `node_modules/`, `dist/`, `.next/`) and local demo directories are git-ignored.
- **Branding** — the user-facing name is **REAPER**.
- **Keep it honest** — every claim in this README is backed by code and tests in this repo; nothing is invented.
