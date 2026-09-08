# Mola — Parallel Finish Sprint

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

This plan freezes the validated functional base and separates the remaining work into two low-conflict tracks.

## Branches

```text
feature/ai-personalization   # validated shared base
├── feature/ui-product-redesign   # Codex / UI track
└── feature/ai-quality-v2          # ChatGPT / AI-quality track
```

Neither track should modify the other track's owned files. Integration happens only after both branches build independently.

## Ownership

### Track U — Product UI — `feature/ui-product-redesign`

Owner: Codex working locally with Android build/ADB access.

Owns:

- `app/src/main/java/com/yberkayinci/mola/ui/**`
- `app/src/main/java/com/yberkayinci/mola/ui/theme/**`
- UI resources
- visual-only changes in `overlay/OverlayController.kt`
- device screenshots and visual QA

Must not change:

- `ai/**`
- Gemini prompts/transport
- data models and persistence
- usage monitoring, permissions, or cooldown

Detailed handoff: `docs/UI_PRODUCT_REDESIGN_HANDOFF.md` on the UI branch.

### Track A — AI Quality — `feature/ai-quality-v2`

Owner: ChatGPT through the GitHub connector, with GitHub Actions as automated validation and the demo phone used for manual A/B checks.

Owns:

- `app/src/main/java/com/yberkayinci/mola/ai/**`
- AI tests
- `docs/PROMPT_DESIGN.md`
- `docs/AI_EVALUATION.md`
- narrowly necessary AI model/configuration fields

Must not change:

- `ui/**`
- theme or visual resources
- overlay visuals
- monitoring, permission, or cooldown behavior

Detailed handoff: `docs/AI_QUALITY_V2_HANDOFF.md` on the AI branch.

## Sprint 0 — Freeze and baseline — 20–30 minutes

Both tracks:

1. Pull the correct branch.
2. Inspect the actual repository before editing.
3. Run `./gradlew test` and `./gradlew assembleDebug`.
4. Record any pre-existing failures.
5. Do not change shared interfaces without coordinating first.

Exit criterion: both branches start green and both agents acknowledge ownership boundaries.

## Sprint 1 — Highest-value foundations — 2 to 3 hours in parallel

### UI

- Define product colors, typography, shapes, spacing, and shared components.
- Redesign Home into a clear product dashboard.
- Move test controls into a discreet developer area.

Exit criterion: Home no longer looks like a debug form; all callbacks still work.

### AI

- Define quality rubric and 12 golden scenarios.
- Add local intervention context/policy builder.
- Map tired/procrastinating/intentional-rest/bored/waiting/habit states to explicit strategies and constraints.

Exit criterion: pure tests prove different states produce different policy contexts.

## Sprint 2 — Core experience — 2 to 3 hours in parallel

### UI

- Redesign onboarding around narrative voice/text.
- Redesign Compose intervention states.
- Redesign the real overlay without changing lifecycle behavior.

Exit criterion: the complete onboarding-to-overlay route is visually coherent and functional.

### AI

- Implement prompt v2 with compact contrastive few-shot examples.
- Add richer internal structured card output while preserving visible `AiCard` fields.
- Add semantic validation and one bounded repair attempt.

Exit criterion: golden scenarios pass pure validation and live outputs are more state-appropriate than the baseline.

## Sprint 3 — Report and hardening — 1.5 to 2 hours in parallel

### UI

- Redesign Daily Report.
- Check large fonts, small screens, scrolling, keyboard overlap, and dark/light behavior.
- Capture screenshots of all screens and major overlay states.

### AI

- Improve profile grounding without changing stored schema.
- Compute local report aggregates and make report synthesis evidence-driven.
- Add privacy-safe diagnostics and failure categories.

Exit criterion: both branches pass unit tests and assemble independently.

## Sprint 4 — Controlled integration — 1 to 2 hours

1. Ensure both branches are committed and CI green.
2. Merge AI-quality changes into the selected integration base first because they should not alter UI files.
3. Merge/rebase UI branch on top and resolve only intentional shared-file changes.
4. Run:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

5. Execute the exact demo route twice on the physical phone.
6. Fix only concrete integration defects; freeze features afterward.

Exit criterion: one combined build succeeds twice through onboarding, monitoring, overlay, live AI, fallback, and report.

## Sprint 5 — Submission package

- Finalize `docs/HACKATHON_REPORT.md` from prompt, implementation, and ethics docs.
- Finalize `docs/DEMO_SCRIPT.md` for a five-minute recording.
- Record one primary demo and one backup video.
- Verify repository links, README, API-key hygiene, and final APK/source state.

## Shared stop rules

- No fifth product screen.
- No new external content integration.
- No large refactor of working Android Core.
- No prompt/model change after final A/B freeze.
- No visual change after two successful final demo rehearsals unless it fixes a real defect.
- Never commit `local.properties` or an API key.
