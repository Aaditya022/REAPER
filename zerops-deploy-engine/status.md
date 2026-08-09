# STACKD Ignition — Deployment Status Stage Report

**Stage:** 3.2 — Deployment lifecycle status tracking
**Module:** `zerops-deploy-engine`
**Status:** Complete — build and tests green

## Deliverables

### Domain (`src/main/java/com/stackd/ignition/status/`)

| File | Purpose |
|------|---------|
| `Deployment.java` | Immutable deployment snapshot (PENDING initial state, `with*` copy methods, timestamps). |
| `DeploymentStatus.java` | Lifecycle state machine (added in stage 3.1): `PENDING → ANALYZING → CONFIGURING → DEPLOYING → HEALTH_CHECKING → HEALTHY`, plus `FAILED` from any non-terminal stage. |
| `DeploymentStore.java` | Thread-safe in-memory store (`ConcurrentHashMap`); atomic per-key updates via `computeIfPresent`. |
| `DeploymentStatusService.java` | Stateful facade: create/get, validated `transitionTo`, `fail`, `updateMessage`, `attachStack`, `attachLiveUrl`. |
| `DeploymentNotFoundException.java` | `404 DEPLOYMENT_NOT_FOUND`. |
| `DeploymentAlreadyExistsException.java` | `409 DEPLOYMENT_ALREADY_EXISTS`. |
| `InvalidStateTransitionException.java` | `409 INVALID_STATE_TRANSITION`. |

### API layer (`src/main/java/com/stackd/ignition/api/`)

| File | Purpose |
|------|---------|
| `dto/DeploymentCreateRequest.java` | `POST /api/v1/deployments` request body with `@NotBlank` validation. |
| `dto/DeploymentResponse.java` | Full deployment representation for `GET /api/v1/deployments/{id}`. |
| `dto/DeploymentStatusResponse.java` | Status-only representation for `GET /api/v1/deployments/{id}/status`. |
| `error/ErrorResponse.java` | Standard body `{"error":true,"code":"...","message":"..."}`. |
| `error/ApiException.java` | Base exception carrying HTTP status + error code. |
| `error/GlobalExceptionHandler.java` | Central `@RestControllerAdvice` boundary; stack traces logged, never exposed. |

## Tests (`src/test/java/`)

- `status/DeploymentStoreTest` — store CRUD, duplicate rejection, concurrency safety (8×25 distinct keys; 32 threads on one key).
- `status/DeploymentStatusServiceTest` — defaults, all valid transitions, fail-from-every-stage, invalid transitions, terminal-state locking, attach/update operations, not-found paths.
- `api/dto/DeploymentCreateRequestTest` — bean-validation behavior.
- `api/error/ErrorResponseTest` — JSON shape, 404 mapping, generic internal error without stack traces.

**Result:** `mvn clean test` → Tests run: 27, Failures: 0, Errors: 0. `mvn package` → bootable jar.

## Design decisions / accepted debt

- **No persistence:** state is in-memory by design (MVP has no DB dependency; demo is a single boot). Restart loses deployments.
- **Immutable entities + atomic per-key updates** make concurrent transitions safe without locks in callers.
- **Controllers for the deployment endpoints are not yet implemented** — the DTOs, service, and error boundary are ready to be wired in Stage 3.3 (REST API).

---

## Stage 3.3 — REST controller wiring

**Status:** Complete — build and tests green, verified live with curl.

### Files

| File | Action |
|------|--------|
| `api/controller/DeploymentController.java` | New — thin REST controller for the three endpoints. |
| `status/DeploymentStatusService.java` | Modified — added `createDeployment(projectPath, zeropsProjectId)` overload that generates the deployment id (UUID), keeping id generation out of the web layer. |
| `test/.../api/controller/DeploymentControllerTest.java` | New — `@WebMvcTest` slice tests (8 cases). |
| `test/.../status/DeploymentStatusServiceTest.java` | Modified — added id-generation test. |

### Endpoints

| Endpoint | Status | Behavior |
|----------|--------|----------|
| `POST /api/v1/deployments` | `202` | Validates DTO (`@Valid`), generates id, creates PENDING state. Returns `{deploymentId, status, message}`. Pipeline NOT started. |
| `GET /api/v1/deployments/{deploymentId}` | `200` | Full summary: id, projectPath, zeropsProjectId, status, message, errorCode/liveUrl when present, timestamps. |
| `GET /api/v1/deployments/{deploymentId}/status` | `200` | Status-only: id, status, message, errorCode/liveUrl when present. |
| Any unknown id | `404` | `{"error":true,"code":"DEPLOYMENT_NOT_FOUND","message":"..."}`. |
| Blank/missing fields | `400` | `{"error":true,"code":"VALIDATION_FAILED","message":"<field>: must not be blank"}`. |

### Decision: response fields

`projectPath` and `zeropsProjectId` ARE returned by `GET /deployments/{id}`. Both are user-supplied identifiers, not secrets — credentials (Zerops token, DB passwords) never enter any DTO and no request/response carries them. `application.yml` sets `spring.jackson.default-property-inclusion: non_null`, so null `errorCode`/`liveUrl`/`stack` fields are omitted rather than leaking as `null`.

### Controller thinness

`DeploymentController` contains only three one-line delegations to `DeploymentStatusService`. No idempotency, orchestration, direct store access, filesystem checks, Zerops calls, polling, or health checks. Id generation lives in the service.

### Verification

- `mvn clean test` → **Tests run: 36, Failures: 0, Errors: 0** (was 27; +8 controller slice tests, +1 service test).
- `mvn package` → bootable jar (`target/stackd-ignition-0.1.0-SNAPSHOT.jar`).
- Live on port 18080: POST → `202` with generated id; GET full → `200`; GET status → `200`; GET unknown → `404 DEPLOYMENT_NOT_FOUND`; blank projectPath → `400 VALIDATION_FAILED`; blank zeropsProjectId → `400 VALIDATION_FAILED`. All responses `Content-Type: application/json`.

### Deviations from architecture

None. One note: `POST` reuse of `DeploymentStatusResponse` (includes `message`) instead of a dedicated create DTO — deliberate to avoid extra DTOs while satisfying "return deploymentId and current status".

---

## Stage 4.1 — Architecture analyzer

**Status:** Complete — build and tests green.

### Files

| File | Action |
|------|--------|
| `analyzer/DetectedStack.java` | Existing record reused — nested `Frontend` / `Backend` / `Database` / `Orm` / `Auth` enums were already present. |
| `analyzer/ProjectAnalysisException.java` | New — analyzer failure with error codes `PROJECT_PATH_INVALID`, `NOT_A_STACKD_PROJECT`, `AMBIGUOUS_STACK`, `UNSUPPORTED_LAYOUT`, `UNREADABLE_PROJECT`. |
| `analyzer/ProjectLayout.java` | New — package-private record `(root, frontendDir, backendDir)`. |
| `analyzer/PackageJson.java` | New — package-private record `(dependencies, hasWorkspaces)`. |
| `analyzer/ArchitectureAnalyzer.java` | New — `@Component`; reads only well-known relative files, returns `DetectedStack`. |
| `test/.../analyzer/ArchitectureAnalyzerTest.java` | New — 29 tests over `@TempDir` fixtures mirroring generator layouts. |

### Detection behavior

- **fb layout** (`frontend/` + `backend/`): React JS (`react` dep), React TS (`react` + `typescript` + `src/main.tsx`), Angular, Vue, or Next frontend; Express JS/TS backend (TS = `@types/express`/`typescript` + `index.ts`), Django/DRF (`manage.py`).
- **fs layout** (Next at root): root `package.json` with `next` → `NEXT`; `next` dominates `react` (Next bundles React). Backend `NONE` (API routes are not a separate framework).
- **Databases**: `DATABASE_URL` scheme in `.env` (`postgres`/`postgresql`/`mysql`/`mongodb`/`mongodb+srv`) OR Prisma `datasource` provider (regex-filtered to DB providers so `prisma-client-js` is ignored). Disagreement → `AMBIGUOUS_STACK`.
- **ORM**: Prisma (`schema.prisma` or `prisma` dep) / Drizzle (`drizzle.config.ts|js` or `drizzle-orm` dep); both → `AMBIGUOUS_STACK`.
- **Auth**: `next-auth` > `passport` > `jsonwebtoken` precedence on the primary package.
- **Fail loudly, never guess**: empty/random folders → `NOT_A_STACKD_PROJECT`; incomplete output (`frontend/` or `backend/` without a manifest) → `NOT_A_STACKD_PROJECT` "incomplete"; Turborepo (`turbo.json`/`apps/`) and workspaces → `UNSUPPORTED_LAYOUT`; Django without DRF in requirements → `UNSUPPORTED_LAYOUT`; conflicting frameworks → `AMBIGUOUS_STACK`.

### Security

- Project root canonicalized with `toRealPath()` (resolves symlinks and `..`); all reads are hard-coded relative names under that root; caller input never reaches a read path.
- Config files (`package.json`, `.env`, `schema.prisma`, `manage.py`) read only when a `NOFOLLOW_LINKS` regular-file check passes — symlinked config is treated as absent, so a read cannot be redirected outside the project.
- Reads bounded at 256 KiB (`MAX_CONFIG_BYTES`); `.env` content is reduced to its URL scheme, so connection strings/passwords never appear in results or error messages (test asserts the password does not leak).
- Analyzer performs no logging, so nothing secret is written server-side.

### Self-review findings addressed (4.1.b → 4.1.c)

1. **Plain Django misdetected as DRF** — `manage.py` alone was treated as DRF. Fixed: when `requirements.txt` or `requirements/*.txt` exist, DRF must appear; otherwise `UNSUPPORTED_LAYOUT` ("Django without Django REST Framework").
2. **Empty `"workspaces": []` false positive** — an empty workspaces array no longer triggers monorepo rejection.
3. **Duplication** — `mapScheme` and `mapProvider` (near-identical switches) merged into one `mapDatabase(keyword, source)`.
4. **Test-fixture bug (build-time)** — `writePackageJson(write(root, "frontend/package.json"), …)` wrote to `root/package.json` because the helper returned the base dir; the empty `frontend/package.json` then failed JSON parsing. Fixed by returning the file's parent dir; 12 test failures traced to this single root cause.

### Unverified file/JSON APIs (4.1.d)

`Files.readAllBytes`, `Files.size`, `Files.writeString(Path, String)` (tests), `Path.toRealPath()` (follows symlinks, throws `IOException` when missing), `Files.isRegularFile/isDirectory(Path, LinkOption.NOFOLLOW_LINKS)`, `ObjectMapper.readTree(byte[])` (returns `null` for JSON null; throws `JsonProcessingException` on empty/malformed input — confirmed empirically by the empty `package.json` failure), `JsonNode.isObject()/fieldNames()`, `String.split("\\R")`, string `switch` expressions. All standard JDK 17 / Jackson behavior, no third-party deps added.

### Verification

- `mvn clean test` → **Tests run: 65, Failures: 0, Errors: 0** (was 36; +29 analyzer tests).
- `mvn package` → bootable jar built.

### Deviations from architecture

- Monorepos (Turborepo/workspaces) are deliberately unsupported (`UNSUPPORTED_LAYOUT`) — the Go generator ships them with comments advising not to build; automatic detection is deferred.
- Single `ProjectAnalysisException` with error codes, rather than one exception class per failure mode — keeps the five failure categories explicit without class sprawl; the controller's `GlobalExceptionHandler` can map these in a later stage.
- `.env` symlinks are deliberately rejected (`NOFOLLOW_LINKS`) — a real project that symlinks its `.env` will fail analysis rather than risk reading outside the project; flagged as an accepted trade-off.
- Django frontend (`DJANGO` enum value) is never produced: a Django project reports `Frontend.NONE` — STACKD's full-stack Django template is commented out in the generator, so there is no emitted layout to detect. Flagged as intentionally unverified.

---

## Stage 4.2 — Zerops configuration generator

**Status:** Complete — build and tests green.

### Files

| File | Action |
|------|--------|
| `zeropsconfig/ZeropsConfigGenerator.java` | New — `@Component`; renders the `zerops.yaml` document from classpath templates. Lives in the scaffold's intended `com.stackd.ignition.zeropsconfig` package (see its `package-info`). |
| `src/main/resources/zeropsconfig/*.tmpl` | New — 9 template resources mirroring STACKD's template-per-concern layout. |
| `test/.../zeropsconfig/ZeropsConfigGeneratorTest.java` | New — 16 tests; every generated document is re-parsed with SnakeYAML. |

### Generation behavior

- **Template-per-variant, lookup-table selection** (no if/else chains): `Frontend → {frontend-vite, frontend-angular, frontend-next}.tmpl`, `Backend → {backend-expressjs, backend-expressts, backend-drf}.tmpl`, `Database → {env-postgres, env-mysql, env-mongo}.tmpl`.
- **`{{placeholder}}` substitution**: `{{envVariables}}` on the service templates is replaced by the chosen env block; the env templates own their full indentation so substituted YAML stays correctly nested. Unresolved placeholders fail loudly (`IllegalStateException`); whitespace-only lines left by empty substitutions are stripped.
- **Runtimes**: React/Vue SPA → static server (`frontend/dist/~`); Next → `nodejs@22` running `npm start` on 3000 with `/` health check; Express JS/TS → `nodejs@22`, `node index.js` on 3000 with `/` health check (TS builds with `npx tsc`); DRF → `python@3.11`, `python manage.py runserver 0.0.0.0:8000` on 8000, deliberately no health check (Django `/` returns 404 by default).
- **Database wiring** via Zerops cross-service env refs. PostgreSQL (MVP): `DATABASE_URL: ${db_connectionString}` — platform-provided whole-value ref (verified vs docs.zerops.io env-vars guide + `zeropsio/recipe-redwoodjs`). Mongo/MySQL still compose inline from `${db_user}`, `${db_password}`, `${db_hostname}`, `${db_port}`, `${db_dbName}` (deferred, not MVP). No DB → no `envVariables` block.
- **Error cases**: stack with neither frontend nor backend → `IllegalArgumentException`; a `Frontend`/`Backend`/`Database` value with no template (e.g. the never-produced `Frontend.DJANGO`) → `IllegalArgumentException`.

### Self-review findings addressed (4.2.b → 4.2.c)

1. **Broken YAML indentation** — the placeholder line was indented (`      {{envVariables}}`) while the env template also carried 6-space indentation, so substitution produced `envVariables:` at 12 spaces with `DATABASE_URL:` dangling at 8. Every parse-based test failed with `Scanner mapping values are not allowed here`. Fixed: placeholders sit at column 0; the env templates own all indentation. Output verified visually with a standalone harness.
2. **Whitespace-only placeholder remnant** — a no-DB stack left a blank line where the placeholder had been. Fixed in `render` by stripping lines that are blank/whitespace-only (templates contain no intentional blank lines, so it is safe).
3. **Test-side typing** — the ports/env helpers used a non-generic `castList`; fixed with a generic `<T> List<T> list(Object)` and element casts.
4. **Package-name architecture violation** — the generator was initially placed in `com.stackd.ignition.zerops`, but the scaffold's `zeropsconfig/package-info.java` declares that package as the owner of "Generates the Zerops deployment configuration". Moved generator, tests, and resources to `zeropsconfig` and removed the stray package.
5. **DRF template deployed the whole repo** — `deployFiles: ./` shipped the monorepo root (sibling frontend included) and `pip install` ran at repo root. Fixed to `cd backend && pip install -r requirements.txt` + `deployFiles: backend/~`, matching the other backends.

### MVP-critical Zerops questions resolved (4.2.f)

Resolved against official sources (docs.zerops.io + `zeropsio` GitHub org). Applied to the MVP (PostgreSQL) only.

- **A. Inline multi-`${...}` interpolation in one value — VERIFIED.** Official recipe-app `nestjs-showcase-worker` uses `SEARCH_URL: http://${search_hostname}:${search_port}`; spartan-ng's zerops.yml uses `DATABASE_URL: ${db_connectionString}/${db_dbName}`. Supported, but hand-composing from raw `user:password` parts is fragile (escaping; the showcase recipe's comments warn about exactly this). **Adopted instead**: `DATABASE_URL: ${db_connectionString}` — a single whole-value ref (docs env-vars guide + `recipe-redwoodjs`).
- **B. `${db_dbName}` — VERIFIED.** Used verbatim in official recipe-apps (`DB_NAME: ${db_dbName}` in nodejs-hello-world, nestjs-minimal-app, showcase-recipe-app) and present in the Postgres reference env-vars table. Now moot for the MVP (the connection string includes it); still used by the deferred mongo/mysql templates.
- **C. PostgreSQL service relationship — CONFIRMED.** `zerops.yaml` declares runtime services only; the db service is provisioned separately (import YAML or GUI, `type: postgresql:single@18` / `postgresql:ha@18`), must already exist in the target project, and is referenced purely via `${db_*}` refs in `run.envVariables`. Refs resolve at container start; unresolved refs stay literal (app fails at connect time). Provisioning is out of MVP scope.
- **D. Prisma build vs runtime — VERIFIED runtime-only.** `recipe-redwoodjs` (official, Prisma-based) sets `DATABASE_URL: ${db_connectionString}` only in `run.envVariables`; its build (`yarn rw build`, which runs Prisma generate) declares no DATABASE_URL. Migrations run at runtime, never in build: `run.initCommands` → `zsc execOnce $ZEROPS_appVersionId -- prisma migrate deploy` (Zerops staff guidance + zsc docs). Generated YAML carries no build-time DATABASE_URL.
- **E. Final MVP env strategy**: provision PostgreSQL service `db` (import YAML/GUI — outside this stage) → backend `run.envVariables: DATABASE_URL: ${db_connectionString}` → Express/Prisma reads `process.env.DATABASE_URL` at runtime. No local `.env` forwarding, no literal secrets, no hand-composed URL.

Applied change: `env-postgres.tmpl` → `DATABASE_URL: ${db_connectionString}`; test renamed to `postgresDatabaseUrlUsesDbConnectionStringReference`. `mvn clean test` → **81 tests, 0 failures, 0 errors**.

### Schema verification against Zerops docs (docs.zerops.io/zerops-yaml/specification + zeropsio/recipes)

Cross-checked the generated output against the official spec and recipe apps:

- **Confirmed**: `zerops:` top-level list; service keys `setup`/`build`/`run`; `run` required; `build.base` required; `deployFiles` required; `ports[].port` (10–65435) and `ports[].httpSupport`; `run.base`; `start`; `healthCheck.httpGet.port/path`; `run.envVariables`; `build.envVariables`; `${db_*}` cross-service refs as whole values (`DB_PASS: ${db_password}`); `${db_connectionString}` for the connection URL; `cache`; `~` trailing-tilde strip on `deployFiles` entries (`dist/~` is the canonical pairing with `base: static`); `base: static` (Nginx static runtime, built-in SPA fallback); `nodejs@22`, `python@3.11`, `python manage.py runserver`, `cd <dir> && cmd` build steps.
- **Inferred, not confirmed by docs**: `mongodb+srv://` scheme against the db service; `python manage.py runserver` as a production start; `backend/~` bare-subdir strip (confirmed for `dist/~`; symmetric semantics assumed); `frontend/.next` cache path. (Since the last stage, `${db_dbName}` and inline multi-ref composition are no longer open items for PostgreSQL — see 4.2.f.)

### Unverified file/format assumptions (4.2.d)

These mirror what STACKD's generator emits but are not contractually verified:

1. `base: nodejs@22` / `python@3.11` runtime type versions — plausible per Zerops docs; not validated against a live project.
2. `${db_*}` placeholders interpolated **inside a longer string** — VERIFIED supported in official recipe-apps (`SEARCH_URL: http://${search_hostname}:${search_port}` in nestjs-showcase-worker; `DATABASE_URL: ${db_connectionString}/${db_dbName}` in spartan-ng), but hand-composing from raw credential parts is fragile (escaping; the showcase recipe warns about it). PostgreSQL (MVP) now uses `${db_connectionString}`; only mongo/mysql still compose inline (deferred).
3. `cd frontend &&` / `cd backend &&` build steps — the Zerops build working directory is the repo root; `cd` chaining relies on the commands running in a shell. `deployFiles` `~/` wildcard and bare-subdir forms (`backend/~`) follow the documented `dist/~` pattern.
4. Express-TS in-place `npx tsc` emit — valid only for a default `tsc --init` tsconfig (no `outDir`).
5. `python manage.py runserver` for DRF is a dev server; production would use gunicorn/uvicorn — accepted for the MVP demo (STACKD's own full-stack Django template is disabled upstream).
6. `setup:` hostnames are `frontend`/`backend`; cross-service `${db_*}` refs additionally require a Zerops database service named `db` to exist in the project (the database service itself is not declared by this generator — it lives in the project import config, which is out of scope here).
7. Angular `deployFiles` hardcodes `dist/angular-app/browser/~` (STACKD's `--name angular-app`); the template carries a YAML comment to adjust if the project name differs. The project name is not carried by `DetectedStack`, so it cannot be parameterized without an API change.
8. `npm ci` requires committed `package-lock.json` files; both `npm create vite@latest` and `npm install` produce them, so any committed STACKD project has them.
9. **`ignition.db.*` config is not yet consumed** — `application.yml` and `.env.example` reserve POSTGRES/MYSQL/MONGO user/password/database/port settings for "credentials forwarded into generated zerops.yaml". This generator emits Zerops cross-service `${db_*}` refs instead and does not read `ignition.db.*`; wiring that config is a separate stage (decide which model the demo needs).

### Verification

- `mvn clean test` → **Tests run: 81, Failures: 0, Errors: 0** (was 65; +16 generator tests, all re-parsing output with SnakeYAML).
- Standalone harness confirmed the rendered `zerops.yaml` for full-stack, backend-only, and no-DB stacks is well-formed and correctly nested.

### Deviations from architecture

- Env templates are standalone `env-*.tmpl` resources (STACKD inlines DB env into each backend template) — avoids duplicating the connection string across expressjs/expressts/drf/next, and the lookup-table selection mirrors the template-per-concern pattern.
- The generator emits runtime services only; the Zerops project import file (which provisions the `db` service) remains out of scope, documented in the unverified list.
- The scaffold's `ignition.deploy.zerops-yaml-name` property (default `zerops.yaml`) is reserved for the stage that writes the file; the generator returns the document as a string and has no filesystem side effects, so it does not consume that property yet.

---

## Stage 4.3 — Environment configuration manager

**Status:** Complete — build and tests green.

### Files

| File | Action |
|------|--------|
| `envmanager/EnvConfigManager.java` | New — `@Component`; merges the project `.env` with the Zerops-injected environment and validates required vars. |
| `envmanager/MergedEnv.java` | New — immutable, secret-safe merged environment result (`values()`, `maskedValues()`, `validate()`). |
| `envmanager/EnvConfigException.java` | New — `PROJECT_PATH_INVALID`, `MISSING_REQUIRED_ENV_VARS`, `UNREADABLE_ENV_FILE`. |
| `api/error/GlobalExceptionHandler.java` | Modified — maps `EnvConfigException` to `422`; messages name keys only, never values. |
| `test/.../envmanager/EnvConfigManagerTest.java` | New — 17 tests over `@TempDir` fixtures. |
| `test/.../api/error/ErrorResponseTest.java` | Modified — +1 handler test asserting no secret values reach the response body. |

### Behavior

- **Merge**: reads `backend/.env` first (fb layout) then root `.env` (fs layout) — the same candidate order the analyzer uses — then overlays the Zerops-injected set, injected values winning on key collision. Project vars that Zerops does not inject are forwarded unchanged.
- **Read safety**: mirrors the analyzer's discipline — `NOFOLLOW_LINKS` regular-file check (symlinked `.env` treated as absent), 256 KiB bound, canonicalized project root. No `.env` → empty project set (the required DB var must come from Zerops).
- **Required validation**: derived from `DetectedStack.database`; every database stack requires `DATABASE_URL`, and it must be satisfied by the Zerops side (the injected `${db_connectionString}` or an explicit injected value). A local development value in the project `.env` never counts — forwarding `postgresql://stackd:...@localhost/...` to Zerops is exactly what 4.2.f-E forbids. `mergeValidated()` / `MergedEnv.validate()` fail with `MISSING_REQUIRED_ENV_VARS` before any deployment starts.
- **Secrets never leak**: the manager performs no logging; `MergedEnv.toString()` lists variable names and required/missing sets only; `maskedValues()` replaces every value with `********`; exception messages name only keys or file names. Tests assert a real connection string and secret never appear in any of these.

### Self-review findings addressed (4.3.b → 4.3.c)

1. **Localhost-`DATABASE_URL` footgun** — an initial design validated required vars against the merged map, which passes when the project `.env` supplies a local dev URL; that would ship a `localhost` connection string to Zerops. Tightened the rule: required vars must be provided by the Zerops side, so a missing injection fails loudly instead (tests `missingRequiredVarThrowsWithKeyNamesOnly`, `requiredVarProvidedByZeropsValidates`).
2. **Present-but-blank injection** — a naive `containsKey` check would accept `DATABASE_URL: ""`. `missingRequiredVars()` treats blank/whitespace values as missing (test `blankZeropsValueCountsAsMissing`).
3. **Secret bleed into messages** — the exception message format and `toString()` were reviewed against a sample `supersecret` connection string; key-name-only wording enforced with dedicated assertions (tests `secretValuesNeverAppearInToStringOrMaskedValues`, `exceptionMessageNeverContainsValues`).
4. **Mutable result maps** — all exposed maps are wrapped unmodifiable so a downstream orchestrator cannot corrupt the merged environment (test `mergedEnvMapsAreUnmodifiable`).
5. **Hand-edited `.env` tolerance** — STACKD emits plain `KEY=value`, but real projects hand-edit: parser supports single/double quotes, `export` prefix, spaced assignments, comments, and ignores bare lines (tests `envFileParsesCommentsQuotesAndExportPrefix`, `malformedLineWithoutEqualsIsIgnored`).

### Verification

- `mvn clean test` → **Tests run: 99, Failures: 0, Errors: 0** (was 81; +17 envmanager tests, +1 error-boundary test).

### Deviations from architecture

- Not yet wired into a live pipeline: the `deployment` package is still empty by design, so nothing calls `mergeValidated()` yet. The integration point is documented — the future orchestrator calls `envConfigManager.mergeValidated(projectPath, stack, zeropsEnv)` (where `zeropsEnv` is the generated `zerops.yaml` run-env with `${db_*}` refs) before sending the Zerops deploy request, and logs only `toString()`/`maskedValues()`.
- Required-var derivation is hard-coded to `DATABASE_URL` for DB stacks; a later stage may pass an explicit required set if other Zerops-required vars appear.

