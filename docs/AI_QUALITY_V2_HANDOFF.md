# Implementation Handoff: Mola AI Quality v2

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

## Status

The planned AI-quality architecture is now implemented on `feature/ai-quality-v2`. Automated unit/build validation and live device A/B testing remain the gates before this branch is marked ready.

## Goal

Upgrade Mola’s Gemini layer from a basic prompt-and-parse implementation into a product-specific behavior engine that consistently produces concise, context-sensitive, realistic, and ethically cautious outputs for three moments: onboarding profile creation, threshold interventions, and daily reflection. Improve quality without making the popup depend on the network, removing offline fallback, changing the four-screen product, or coupling the AI layer to the UI redesign.

## Non-goals

- Do not change the visible UI, Compose hierarchy, theme, overlay styling, usage monitoring, cooldown, or Android permissions.
- Do not build a general-purpose chatbot, therapist, diagnosis system, web-search recommender, Spotify/podcast integration, or multi-turn cloud memory.
- Do not let Gemini choose the user’s limit or declare usage excessive.
- Do not remove `LocalAiGateway`, crisis short-circuiting, local counts, output validation, or offline behavior.
- Do not commit an API key or treat direct mobile credentials as production architecture.
- Keep visible `AiGateway` and data-model contracts stable so the UI track can proceed independently.

## Implemented architecture

### 1. Golden scenarios and quality rubric

`docs/AI_EVALUATION.md` defines:

- a 0–11 card-quality rubric;
- critical rejection conditions;
- profile, intervention, report, crisis, and fallback scenarios;
- a physical-device model A/B matrix;
- prompt/model freeze criteria.

### 2. Local intervention policy compiler

`InterventionContextBuilder` resolves quick replies or Turkish/English custom text into:

- current need;
- energy expectation;
- interaction objective;
- allowed strategy set;
- maximum duration;
- safe profile anchors;
- forbidden behavior patterns.

Custom text takes priority over a generic selected state when it has a clear cue. High-effort activities are removed from low-energy anchors.

### 3. Prompt v2

`AiPrompts` now contains:

- a more strictly grounded profile prompt;
- policy-driven intervention prompt;
- compact contrastive few-shot examples;
- one bounded repair prompt;
- evidence-driven report prompt.

Exact prompt text and rationale are maintained in `docs/PROMPT_DESIGN.md`.

### 4. Structured outputs and task settings

`GeminiMessageClient` accepts:

- task-specific temperature;
- optional `responseJsonSchema`;
- structured failure categories.

Profile, card, and report model IDs are independently configurable at runtime through `AiSettingsStore`; the defaults live in `AiModels`.

### 5. Semantic validation and repair

The internal card contract contains:

- need;
- strategy;
- question;
- alternative;
- duration;
- personalization anchor.

`AiCardSemanticValidator` checks state fit, strategy, duration, question form, actionability, low-energy appropriateness, autonomy, grounding, live-content invention, and safety. One repair request is allowed; failed repair uses deterministic fallback.

### 6. Profile grounding

`ProfileGroundingSanitizer` filters generated goals, contexts, and activities against the user's own onboarding evidence. Neutral low-energy actions are allowed; invented hobbies or media preferences are removed. Missing values are completed locally.

### 7. Evidence-driven daily reflection

`DailyReportEvidenceBuilder` computes local state/choice counts, candidate states with a minimum sample, unique dominant state, possible higher-continue state, and time buckets. Gemini can choose only from locally supported evidence. `DailyReportSemanticValidator` rejects unsupported state IDs, causal certainty, vague micro-steps, missing short duration, and unsafe language.

### 8. Deterministic quality floor

`LocalAiGateway` uses the same state-policy distinctions as live AI. A tired user does not receive a hard workout simply because exercise is a profile goal; procrastination receives a micro-start; intentional rest preserves autonomy; report fallback uses local evidence.

### 9. Privacy-safe diagnostics

Debug Logcat reports only:

- task;
- model;
- live/repaired/fallback source;
- outcome category;
- elapsed milliseconds.

It does not log raw profile text, intervention text, crisis text, or keys.

## Files changed

| Path | Purpose |
|---|---|
| `app/src/main/java/com/yberkayinci/mola/ai/AiOutputContracts.kt` | Internal policy/output contracts |
| `app/src/main/java/com/yberkayinci/mola/ai/InterventionContextBuilder.kt` | Local state/strategy compiler |
| `app/src/main/java/com/yberkayinci/mola/ai/AiPrompts.kt` | Prompt v2 and repair prompt |
| `app/src/main/java/com/yberkayinci/mola/ai/GeminiMessageClient.kt` | Task settings, schema support, failure categories |
| `app/src/main/java/com/yberkayinci/mola/ai/GeminiAiGateway.kt` | Structured calls, validation, repair, evidence, diagnostics |
| `app/src/main/java/com/yberkayinci/mola/ai/AiCardSemanticValidator.kt` | Card semantic validation |
| `app/src/main/java/com/yberkayinci/mola/ai/ProfileGroundingSanitizer.kt` | Profile evidence grounding |
| `app/src/main/java/com/yberkayinci/mola/ai/DailyReportEvidence.kt` | Local report aggregates |
| `app/src/main/java/com/yberkayinci/mola/ai/DailyReportSemanticValidator.kt` | Report evidence/safety checks |
| `app/src/main/java/com/yberkayinci/mola/ai/LocalAiGateway.kt` | State-aware deterministic quality floor |
| `app/src/main/java/com/yberkayinci/mola/ai/SafetyLanguageValidator.kt` | Contextual judgment-language checks |
| `app/build.gradle.kts` | Task-specific model configuration |
| `AiSettingsStore.kt` | User-entered key and per-task model overrides |
| `docs/AI_EVALUATION.md` | Rubric and golden scenarios |
| `docs/PROMPT_DESIGN.md` | Exact messages and rationale |
| `docs/AI_DEVICE_QA.md` | Device scenario matrix and Logcat commands |
| `app/src/test/java/com/yberkayinci/mola/ai/*` | Policy, validator, evidence, grounding, and fallback tests |

## Validation commands

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## Manual acceptance matrix

Validate on the physical demo phone:

1. tired + exercise/music profile;
2. procrastinating + explicit study task;
3. intentional relaxation;
4. English fatigue input with Turkish output;
5. sparse profile without invented anchors;
6. unsupported live podcast/content claim;
7. grounded seven-record report;
8. airplane-mode fallback;
9. crisis short-circuit;
10. exact demo flow twice.

Record model, latency, source, score, and notes in `docs/AI_EVALUATION.md`.

## Edge cases and expected handling

- Blank key/network/timeout/quota/block/malformed JSON: local fallback.
- Schema rejected by provider: one compatibility retry without schema.
- Semantically invalid card: one repair attempt, then fallback.
- Unsupported report pattern: fallback report.
- Crisis signal: no Gemini or repair request.
- Sparse profile: broad grounded profile, not invented specificity.
- Intentional rest: autonomy-preserving timed option, not forced stopping.

## Rollback plan

`LocalAiGateway` remains complete. If live quality v2 is unstable, use a blank key for deterministic operation or revert the AI-quality PR without affecting Android Core, UI, persistence, or the four-screen flow.
