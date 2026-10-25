# AGENTS.md — ShowMeOutfit AI Agent Guidance & Project Manual

> **Universal Agent Primer:** This document serves as the primary entry point for AI coding agents (Claude Code, Cursor, Windsurf, Copilot, Antigravity, Roo Code, etc.) working on the **ShowMeOutfit** repository. It outlines project behaviors, system architecture, milestone progress, development workflows, and strict engineering conventions established during Sprint 1.

---

## 1. Project Overview & Behaviors

**ShowMeOutfit** is an offline-first, standalone native Android application that provides personalized daily clothing and accessory recommendations based on diurnal weather conditions.

### Core Behaviors & Capabilities
1. **Zero-Backend Standalone Operation:** Direct client queries to the public [Open-Meteo REST API](https://open-meteo.com/) without intermediate servers or API keys.
2. **Dual Operational Modes:**
   - **Mode 1 (Scheduled Morning Alert):** Exact battery-compliant morning notification (e.g., 07:00 AM) summarizing daily layers and vital accessories.
   - **Mode 2 (Interactive On-Demand):** Real-time interactive Jetpack Compose dashboard with diurnal temperature curves, rain windows, and location search.
3. **Diurnal Temperature Intelligence:** Analyzes daytime swings ($\Delta T = T_{\max} - T_{\min} \ge 7^\circ\text{C}$ between 07:00 and 22:00) to recommend removable mid/outer layers (e.g., cardigans, light jackets).
4. **Protective Accessory Triggers:**
   - Rain spike alerts and umbrella recommendations when $P_{\text{rain}} \ge 35\%$.
   - UV alerts (sunglasses/sunscreen for $\text{UV} \ge 3$; sun hat for $\text{UV} \ge 6$).
   - Windbreaker recommendations when wind speed $\ge 35\text{ km/h}$.
5. **Personalization Offsets:** Adjusts layer thresholds dynamically via thermal sensitivity ($\pm 2.5^\circ\text{C}$) and gender preferences.

> 📖 **Deep Dive:** For full algorithmic rules and layering matrices, see [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md#3-domain-model--diurnal-recommendation-algorithm).

---

## 2. Architecture & Codebase Map

The project strictly adheres to **Clean Architecture** with **MVI / Unidirectional Data Flow (UDF)**.

```
ShowMeOutfit/
├── app/src/main/java/com/smoh/showmeoutfit/
│   ├── domain/              # Pure Kotlin business logic (ZERO Android framework imports)
│   │   ├── engine/          # Diurnal recommendation engine (RuleBasedPresetEngine)
│   │   ├── model/           # Immutable domain entities, enums, and value objects
│   │   ├── repository/      # Repository interface contracts
│   │   └── usecase/         # Interactors / Use cases (e.g. GetTodayOutfitRecommendationUseCase)
│   ├── data/                # Data layer implementations
│   │   ├── local/           # Room DB data sources & DAO abstractions
│   │   ├── remote/          # Ktor Open-Meteo client, DTOs, and network mappers
│   │   └── repository/      # Repository implementations (caching, offline fallback)
│   ├── core/                # Core platform infrastructure
│   │   ├── database/        # Room Database configuration, entities, and Room mappers
│   │   ├── datastore/       # Jetpack Preferences DataStore for UserProfile
│   │   └── network/         # Ktor HTTP client engine configuration & serialization
│   ├── di/                  # Koin 4.x dependency injection modules (AppModule.kt)
│   └── ui/                  # Jetpack Compose presentation layer (Material 3)
│       └── theme/           # Color palettes, typography, dynamic themes
├── app/src/test/            # 100% JVM unit tests for domain, data, and DI layers
├── docs/architecture/       # Architecture specification and visual system diagrams
└── .sprint/                 # Sprints backlog, boards, milestones, and closure reports
```

### Architectural Invariants (Must Never Be Broken)
- **Domain Layer Purity:** `domain/` must **never** import `android.*` packages. All domain logic must remain 100% testable on a standard JVM.
- **Offline-First Caching:**
  - 3-hour cache freshness window for weather snapshots in Room.
  - Graceful degradation: return cached data up to 12 hours old when offline; prompt user retry if $> 24$ hours old.
- **Dependency Rule:** UI $\rightarrow$ Domain $\leftarrow$ Data. Dependencies point inward toward `domain`.

> 📖 **Deep Dive:** For sequence diagrams, caching strategy, and tech stack trade-offs, see [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md).

---

## 3. Milestones & Sprint Progress

| Milestone | Target | Sprints | Status |
| :--- | :--- | :--- | :--- |
| **Milestone 1** | Standalone Weather Outfit MVP (v1.0.0) | Sprint 1 – Sprint 4 (67 SP) | 🟡 In Progress |
| **Milestone 2** | Smart Wardrobe & Digital Closet (v2.0.0) | Sprint 5 – Sprint 6 (38 SP) | ⚪ Backlog |

### Current Project State
- **Sprint 1 (COMPLETED):** Core diurnal recommendation engine, domain foundation, Open-Meteo Ktor client, Room caching, DataStore preferences, Koin DI, and 27 passing unit tests. Merged into `main` via PR #1.
- **Sprint 2 (NEXT / ACTIVE TARGET):** Jetpack Compose UI (Material 3), Home Dashboard, diurnal timeline curve, outfit visual layer cards, and GPS/search location integration.
- **Sprint 3 (PLANNED):** Morning notification scheduler (`AlarmManager` exact wake-up + `WorkManager`), Android 12/13 permission flows, and Settings screen.
- **Sprint 4 (PLANNED):** Doze mode validation, extreme weather edge cases, R8 optimization, and release packaging.

> 📖 **Deep Dive:** See [`.sprint/ROADMAP_AND_MILESTONES.md`](.sprint/ROADMAP_AND_MILESTONES.md) and completed execution verification in [`.sprint/sprint_1_closure_report.md`](.sprint/sprint_1_closure_report.md).

---

## 4. Standard Development Workflow & Git Conventions

All agents and contributors must follow the verified Sprint 1 development process:

### 4.1 Git Branching Model
```
main (protected baseline)
  │
  └── sprint-X (active sprint integration branch)
        ├── feature/<TICKET-KEY>-<short-description>
        └── feature/<TICKET-KEY>-<short-description>
```
1. **Never commit directly to `main` or `sprint-X`.**
2. Branch all features from `sprint-X` (e.g. `sprint-2`) using the pattern:
   `feature/<TICKET-KEY>-<kebab-case-description>` (e.g., `feature/SMO-S2-01-dashboard-ui`).
3. Open PRs targeting `sprint-X`.
4. At sprint end, after full QA sign-off and closure report, a sprint-level PR merges `sprint-X` into `main`.

### 4.2 Commit Message Convention
Adhere strictly to Conventional Commits with the associated ticket key:
```
<type>(<scope>): <short imperative description> (<TICKET-KEY>)
```
- **Allowed Types:** `feat`, `fix`, `test`, `refactor`, `chore`, `docs`, `build`, `ci`.
- **Allowed Scopes:** `engine`, `domain`, `network`, `storage`, `repository`, `di`, `ui`, `sched`, `arch`.
- **Examples:**
  - `feat(engine): implement diurnal outfit recommendation engine with complete test matrix (SMO-S1-03)`
  - `feat(ui): create DiurnalTimelineBar component with Material 3 styling (SMO-S2-02)`
  - `test(repository): verify 12-hour offline fallback in WeatherRepositoryImpl (SMO-S1-06)`

### 4.3 Dependency Management Convention
- **Gradle Version Catalog Only:** Declare all plugins and libraries in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
- **Zero Hardcoded Versions:** Never put explicit version strings in `app/build.gradle.kts`.

---

## 5. Agent Rules of Engagement (Guardrails)

### ✅ What Agents MUST Do
1. **Consult Active Backlog First:** Always check `.sprint/` for the active sprint plan, ticket dependencies, and acceptance criteria before writing code.
2. **Maintain 100% JVM Testability:** Write unit tests for all domain and data logic (`app/src/test/`).
3. **Verify Build & Tests Before Submitting:** Run unit tests using the project toolchain before marking any task complete:
   ```bash
   # Windows PowerShell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest
   
   # Linux / macOS
   ./gradlew testDebugUnitTest
   ```
4. **Use Established Abstractions:** Inject dependencies via Koin modules in [`AppModule.kt`](app/src/main/java/com/smoh/showmeoutfit/di/AppModule.kt). Do not instantiate singletons manually.
5. **Request User Review Before Git Actions:** Never run `git commit`, `git push`, or merge actions without explicit user review and approval.

### ❌ What Agents MUST NOT Do
1. **No Android Imports in Domain:** Do not import `android.*`, `androidx.*`, or UI elements inside `domain/`.
2. **No API Keys or Backend Middleware:** Do not add backend servers or hardcode private API keys (Open-Meteo is open and keyless).
3. **No Direct Commits to `main`:** Do not bypass the feature branch and sprint branch lifecycle.
4. **No Duplicate Documentation:** Do not rewrite or duplicate algorithms, test matrices, or roadmaps. Link directly to existing docs in `docs/` and `.sprint/`.

---

## 6. Document Directory Quick Reference

| Topic | Primary Reference File |
| :--- | :--- |
| **Architecture & Algorithms** | [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) |
| **Interactive System Visuals** | [`docs/architecture/architecture_v1.0.0.html`](docs/architecture/architecture_v1.0.0.html) |
| **Milestones & Sprints Roadmap** | [`.sprint/ROADMAP_AND_MILESTONES.md`](.sprint/ROADMAP_AND_MILESTONES.md) |
| **Sprint 1 Execution & QA Report** | [`.sprint/sprint_1_closure_report.md`](.sprint/sprint_1_closure_report.md) |
| **Sprint 1 Detailed Backlog** | [`.sprint/sprint_1_backlog_and_planning.md`](.sprint/sprint_1_backlog_and_planning.md) |
| **Dependency Declarations** | [`gradle/libs.versions.toml`](gradle/libs.versions.toml) |
| **Koin DI Graph** | [`app/src/main/java/com/smoh/showmeoutfit/di/AppModule.kt`](app/src/main/java/com/smoh/showmeoutfit/di/AppModule.kt) |
