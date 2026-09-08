# Mola Documentation Map

Use this file to tell current product/release documentation apart from retained historical
material.

## Current source of truth

Read these for any new work:

1. [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) — current product behavior and boundaries
2. [`DATA_SCHEMA.md`](DATA_SCHEMA.md) — persisted device data and structured AI contracts
3. [`PROMPT_DESIGN.md`](PROMPT_DESIGN.md) — prompt design and rationale
4. [`AI_EVALUATION.md`](AI_EVALUATION.md) — AI quality/safety scenarios and results
5. [`AI_DEVICE_QA.md`](AI_DEVICE_QA.md) — device procedures for on-device and live checks
6. [`VALIDATION.md`](VALIDATION.md) — Android/emulator/physical validation record
7. [`PLAY_STORE.md`](PLAY_STORE.md) — store submission checklist and Data safety answers

Repository-level policy: [`../README.md`](../README.md), [`../PRIVACY.md`](../PRIVACY.md),
[`../AGENTS.md`](../AGENTS.md), [`../COPILOT_PROMPT.md`](../COPILOT_PROMPT.md) and
[`../.github/copilot-instructions.md`](../.github/copilot-instructions.md).

## How the AI is configured (current)

Mola ships **no provider credential**. `LocalAiGateway` is the default engine and runs fully
on-device. `GeminiAiGateway` is used only when the user has entered their own Google AI
Studio key in the app (*Home → Yazı motoru*), which `AiSettingsStore` holds in app-private
preferences. `AiGatewayFactory` decides between the two.

Any document below that describes a `GEMINI_API_KEY` in `local.properties` is describing the
hackathon prototype, not the current build.

## Historical documents

These are retained as implementation history — design decisions, task decomposition and the
state of the project during its hackathon phase. Each carries a banner to that effect. They
do **not** describe the current architecture or workflow:

- [`FINALIZATION.md`](FINALIZATION.md)
- [`AI_PERSONALIZATION_IMPLEMENTATION.md`](AI_PERSONALIZATION_IMPLEMENTATION.md)
- [`AI_QUALITY_V2_HANDOFF.md`](AI_QUALITY_V2_HANDOFF.md)
- [`TEAM_PLAN.md`](TEAM_PLAN.md)
- [`IMPLEMENTATION_HANDOFF.md`](IMPLEMENTATION_HANDOFF.md)
- [`PARALLEL_SPRINT_PLAN.md`](PARALLEL_SPRINT_PLAN.md)
- [`UI_PRODUCT_REDESIGN_HANDOFF.md`](UI_PRODUCT_REDESIGN_HANDOFF.md)

## Active workflow

```text
main
  ^
  | pull request
  |
feature/<small-feature>
```

Each change branches from the latest `main`, stays narrow, passes `./gradlew test` plus a
proportional device check, and returns through one pull request that CI must pass.
