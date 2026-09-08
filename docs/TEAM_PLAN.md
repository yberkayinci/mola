# Four-Person Two-Day Plan

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

No role is part-time. Each person has a full independent track, and the interfaces are arranged so one role does not block another.

## Saturday morning: freeze first

The first shared action is to approve `docs/DATA_SCHEMA.md` and post it to the group. Do not start parallel implementation with an unfrozen contract; otherwise four people will create incompatible assumptions and lose integration time.

## Branch map

```text
main
├── work/android-core
├── work/ui
├── work/ai
└── work/integration
```

No `dev`, `test`, or `staging` branch. Every role works on its assigned branch and opens a Pull Request into `main`.

## Roles

### A — Android Core — Bahadır — highest-risk track

Branch: `work/android-core`

Responsibilities:

- Usage Access permission flow
- Draw Over Other Apps permission flow
- `UsageStatsManager` readings for the selected package
- User-started foreground service with 60-second polling
- Foreground/target-app detection
- User-defined threshold trigger
- 15-minute intervention cooldown
- System overlay behavior and lifecycle
- Keyboard/back/OEM/battery edge cases
- **Yine de gir:** dismiss overlay and keep/return target app in front
- **Vazgeçtim:** dismiss overlay and move away from target app
- Physical-device validation of all high-risk Android behavior

Owned paths: `permissions/`, `usage/`, `monitor/`, `overlay/`, and coordinated manifest edits.

**Critical checkpoint:** by Saturday noon, the intervention card must appear above the selected target app on the physical demo phone after the limit is exceeded. If it does not, A focuses only on this risk until it works or a documented demo fallback is prepared.

### B — UI / Compose

Branch: `work/ui`

Responsibilities:

- Onboarding
- Home usage/limit state
- Intervention screen states
- Daily report layout
- App picker and limit input UX
- Loading, empty, and error states
- Turkish copy consistency
- Visual polish

Owned paths: `ui/`, `screens/`, `components/`.

B builds against stable interfaces and mocks. B must never wait for real AI or Android service implementation.

### C — AI & Safety

Branch: `work/ai`

Responsibilities:

- In the first 30 minutes, provide/verify fixed-JSON mock AI functions
- Card prompt
- Daily-report prompt
- Anthropic HTTP client for the prototype
- Strict JSON parsing
- Timeout/error fallback
- Crisis-language gate before any AI call
- Generated-language validation
- Block diagnostic or judgmental phrasing
- Preserve the rule that AI never defines the threshold; it only references the user’s own target

Owned paths: `ai/`, `network/`, `safety/`, `prompts/`.

The mock path must remain usable after the real API is connected.

### D — Data, Integration & Demo

Branch: `work/integration`

Responsibilities:

- Freeze and publish the data contract Saturday morning
- Device-local persistence (JSON/Room)
- Repository/data layer
- UI ↔ data wiring
- service ↔ data wiring
- AI ↔ data wiring
- Today-record aggregation
- 7-record minimum report eligibility
- 3–4 days of seeded demo data
- Integration tests and cross-feature bug fixing
- Shared navigation/dependency coordination
- README/report/ethics/demo-flow maintenance
- Demo-state preparation and final submission coordination

Owned paths: `data/`, `repository/`, `model/`, `demo/`, `integration/`, `docs/`.

D coordinates high-conflict shared files such as `MolaApp.kt`, navigation, shared dependency wiring, and frozen model files. D is not a rescue role; every owner is responsible for delivering a working PR.

## Critical dependency rules

1. D freezes and publishes the data model first.
2. C supplies the mock AI path in the first 30 minutes.
3. B builds against mocks/interfaces and never waits for backend/API work.
4. A validates the physical overlay path independently against the frozen profile/package/limit contract.
5. Do not put two people in the same file at the same time.
6. Coordinate edits to:
   - `app/src/main/java/com/yberkayinci/mola/MolaApp.kt`
   - `app/src/main/AndroidManifest.xml`
   - `app/build.gradle.kts`
   - navigation/shared dependency files
   - frozen data-model files

## Saturday

| Time | A — Android Core | B — UI | C — AI & Safety | D — Data / Integration |
|---|---|---|---|---|
| Morning | Permissions + usage reads | Onboarding + Home | Verify mock gateway, prompt v1 | Freeze schema + persistence |
| Noon checkpoint | **Does the overlay appear above the selected app on the physical demo phone after the limit?** | Intervention UI | Real API behind existing interface | UI/data integration |
| Afternoon | Cooldown + continue/stop app behavior | Finish intervention states | Prompt v2, strict parser, safety | Service/data/AI wiring |
| Evening | Four-person end-to-end first pass | Same | Same | Integration coordination |

If the noon checkpoint fails, A works only on that risk until it passes or a documented fallback is ready. B, C, and D continue against mocks/interfaces.

## Sunday

| Time | A — Android Core | B — UI | C — AI & Safety | D — Data / Integration |
|---|---|---|---|---|
| Morning | Edge cases, lock screen, battery/OEM tests | Daily report UI | Daily-report prompt | Aggregation + report eligibility |
| Noon | Device/integration support | Finish report | Crisis gate + validator tests | Integration + bug fixing |
| Afternoon | Prepare demo phone | Visual corrections | Prompt freeze + fallback verification | Report + ethics + seeded demo state |
| Evening | Demo rehearsal ×3, backup video, final submission | Same | Same | Same |

D owns prepared data across 3–4 days, with enough current-day records for the report. Never reach the stage with an empty report screen.

## Pull Request workflow

Each person stays on their own branch:

```bash
git switch work/<role>
git pull
git add <only-your-files>
git commit -m "feat: ..."
git push
```

Then open:

```text
work/<role> → Pull Request → main
```

Rules:

- Do not push directly to `main`.
- Do not force-push `main`.
- Keep PRs small enough to review quickly.
- Re-sync with `main` before large integration work if your branch is stale.
- If you need to change another role’s owned file, coordinate first.
- Prefer squash merge so `main` stays readable.
