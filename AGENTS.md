# Agent Instructions

Read these before editing:

1. `.github/copilot-instructions.md`
2. `docs/PRODUCT_SPEC.md`
3. `docs/DATA_SCHEMA.md`
4. `docs/FINALIZATION.md`

## Current repository state

`main` is the release history. There is one working branch pattern and no integration branch:

```text
main
  ^
  | pull request
  |
feature/<small-feature>
```

For each change:

1. branch from the latest `main`;
2. keep the branch to one coherent feature or fix;
3. run `./gradlew test` and `./gradlew assembleDebug` before pushing;
4. perform the smallest emulator/physical-device check that exercises the changed behavior;
5. open a pull request into `main` and let CI pass before merging.

Never develop directly on `main`.

## Non-negotiable product rules

- Preserve the user-defined threshold: AI never recommends, judges, or changes it.
- Never diagnose addiction or another medical/mental-health condition.
- Never shame, moralize, label the person, or state unsupported causation.
- Crisis-signalling external text must be handled locally and must not enter the normal Gemini/repair path.
- Numeric report facts are computed locally.
- Keep the deterministic fallback usable.
- Keep user profile/records device-local and preserve one-action data deletion.
- Never commit secrets, a real `local.properties`, or a `keystore.properties`.
- The app ships with no provider credential. Gemini is reached only through a key the user
  enters at runtime (`AiSettingsStore`); never reintroduce a build-time or bundled key.
- `LocalAiGateway` is the shipped default engine, not a test double. Keep it complete enough
  to be the product on its own.

## Architecture boundaries

- `ui/` — Compose product UI
- `overlay/` — real system intervention overlay
- `permissions/`, `usage/`, `monitor/` — Android core
- `ai/` — both engines, prompts, policy compiler, validators, settings, safety
- `data/` — local models/repository/demo records
- `MolaApp.kt` — integration/navigation boundary

Do not casually refactor cross-cutting architecture while implementing a small feature. Prefer a narrow change with explicit ownership.

## Validation expectations

For Android-core/overlay/voice changes, test the affected path on a physical phone when possible and report device/Android version. For pure logic, add/update unit tests. For AI changes, rerun the relevant golden scenarios in `docs/AI_EVALUATION.md`, plus offline fallback when network behavior changed.

User-facing copy is Turkish. Code, commit messages, and technical documentation are primarily English.
