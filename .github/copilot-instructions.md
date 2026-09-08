# Mola repository instructions for coding agents

Mola is an Android digital-wellbeing prototype. It monitors one user-selected app and compares its local usage with a daily threshold chosen by the user. When the selected app is foreground and the threshold is reached, Mola can show a system overlay, ask for current context, produce a short personalized reflection/micro-alternative, and record whether the user intentionally continued or tried something else.

## Source of truth

Before changing code, read:

- `docs/PRODUCT_SPEC.md`
- `docs/DATA_SCHEMA.md`
- `docs/FINALIZATION.md`
- `docs/AI_EVALUATION.md` for AI behavior changes

`main` is the release history. Older `work/*`, UI sprint, AI-quality sprint and handoff branches belong to the project's hackathon phase and no longer exist.

## Active Git workflow

```text
main
  ^
  | pull request
  |
feature/<small-feature>
```

Rules:

- Never develop directly on `main`.
- Start each feature from the latest `main`.
- Keep the branch to one coherent feature/fix.
- Run `./gradlew test` and `./gradlew assembleDebug` before opening a pull request.
- Retest the smallest device path affected by the change.
- Let CI pass before merging into `main`.

## Non-negotiable product rules

- The user sets the threshold. AI never recommends, judges, or changes it.
- Never diagnose addiction or any medical/mental-health condition.
- Never label the person from a context such as procrastination.
- Avoid shame, accusation, moralizing, unsupported causal certainty, and retrospective blame.
- Crisis-signalling external text must be gated locally and must not enter the normal Gemini/repair path.
- With fewer than seven current-date records, do not call the report model.
- Compute counts, durations, and evidence aggregates locally.
- Keep profile/intervention records on-device and preserve the clear-data action.
- Preserve Android backup/device-transfer exclusions for app state.
- Never commit API keys, `local.properties`, or `keystore.properties`.
- The app ships with no provider credential. Gemini is reached only through a key the user
  enters at runtime (`AiSettingsStore`); never reintroduce a build-time or bundled key.
- `LocalAiGateway` is the shipped default engine and must stay complete on its own.

## Current architecture

- Kotlin + Jetpack Compose, min SDK 26, compile SDK 37, target SDK 36.
- `data/`: device-local models, JSON repository, demo seeding.
- `ui/`: four in-app product screens.
- `overlay/`: real `TYPE_APPLICATION_OVERLAY` intervention.
- `permissions/`, `usage/`, `monitor/`: Android-core behavior.
- `ai/`: Gemini client/gateway, prompt contracts, local policy compiler, grounding/safety validators, deterministic fallback.
- `MolaApp.kt`: integration/navigation boundary.

The system overlay is an Android surface in addition to the four Compose screens; do not create unnecessary product screens for small features.

## AI behavior

Current validated demo configuration:

- profile: `gemini-2.5-flash-lite`
- card: `gemini-2.5-flash-lite`
- daily report: `gemini-3.6-flash`

These remain locally configurable. Keep `LocalAiGateway` working regardless of live-provider availability.

Before a live card request, the app compiles authoritative local policy (need, energy, objective, allowed strategies, max duration, allowed anchors, forbidden patterns). Gemini must fit that policy, structured output must be semantically validated, and only one repair attempt is allowed before fallback.

Do not request or claim chain-of-thought. Prompt design is structured + compact contrastive few-shot examples + local validation.

## Engineering rules

- Inspect actual files before editing; do not assume old handoff paths are current.
- Prefer narrow changes over architecture-heavy refactors.
- Keep 60-second monitor polling and 15-minute cooldown unless a product decision explicitly changes them.
- Do not use Accessibility Service as a shortcut.
- Do not silently remove privacy/safety/fallback behavior to make a feature easier.
- For pure logic, add/update unit tests.
- For overlay/monitor/voice changes, validate on a physical phone when possible.
- For AI changes, run the relevant golden scenarios in `docs/AI_EVALUATION.md` and test offline fallback when networking changed.

## Language

User-facing copy is Turkish. Code, comments, commit messages, and technical documentation are primarily English. Report observations remain tentative questions; each eligible report has one micro-step.
