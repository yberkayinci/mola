# Mola — Current Feature Executor Prompt

Use this prompt when a coding agent is asked to implement an optional feature or fix after the validated baseline.

## First: verify the current baseline

Before editing:

1. Read `AGENTS.md`.
2. Read `.github/copilot-instructions.md`.
3. Read `docs/PRODUCT_SPEC.md`, `docs/DATA_SCHEMA.md`, and `docs/FINALIZATION.md`.
4. For AI behavior changes, also read `docs/AI_EVALUATION.md` and `docs/PROMPT_DESIGN.md`.
5. Inspect the actual files affected by the requested change.

The active integration baseline is:

```text
main
```

The old `work/*`, `feature/ai-personalization`, `feature/ui-product-redesign`, and `feature/ai-quality-v2` branches are historical. Do not put new work on them.

## Branch workflow

Unless the project owner explicitly requests work directly on the integration branch, implement optional work on one narrow branch created from the latest integration baseline:

```powershell
git fetch origin
git switch main
git pull --ff-only
git pull --ff-only
git switch -c feature/<short-name>
```

Do not create an integration branch. Each feature branches from `main` and returns through one pull request.

## Current product contract

Mola monitors one user-selected Android app and compares local usage with a daily threshold set by the user. Once the threshold is reached while the target app is foreground, the app can show a system overlay after the 15-minute cooldown.

The intervention asks:

> Şu an seni burada tutan ne?

The user can choose one of three locally available personalized quick states, type context, or use Android voice recognition. Normal context enters a local policy compiler and then the Gemini card pipeline. The visible result is one short reflection question and one concrete micro-alternative. The user may intentionally continue or try the alternative.

There are four in-app product screens:

1. onboarding;
2. Home;
3. intervention;
4. daily report.

The real Android system overlay is an additional presentation surface, not a fifth in-app screen.

## Non-negotiable behavior

- The threshold is always chosen by the user. AI never recommends, judges, or changes it.
- Never diagnose addiction or another medical/mental-health condition.
- Never shame, moralize, label the person, or claim unsupported causation.
- Crisis-signalling external text must be handled locally and must not enter the normal Gemini/repair path.
- Keep `LocalAiGateway` and the offline/provider-failure path working.
- Numeric daily-report facts and evidence aggregates stay local.
- Fewer than seven current-date intervention records means no live report synthesis.
- Keep profile and records on-device and preserve the one-action delete flow.
- Preserve Android backup/device-transfer exclusions for Mola state.
- Never commit `local.properties`, `keystore.properties`, API keys, signing files, generated captures, or other credentials.
- The app ships with no provider credential; Gemini is reached only through a user-entered key.

## Validated AI baseline

The combined QA configuration is:

```properties
GEMINI_PROFILE_MODEL=gemini-2.5-flash-lite
GEMINI_CARD_MODEL=gemini-2.5-flash-lite
GEMINI_REPORT_MODEL=gemini-3.6-flash
```

The AI implementation already includes:

- profile grounding;
- local need/energy/objective/strategy compilation;
- constrained structured card output;
- semantic validation;
- one bounded repair attempt;
- deterministic fallback;
- local report evidence aggregation;
- privacy-safe diagnostics.

Do not replace this with an unconstrained prompt-only implementation. Do not request or claim chain-of-thought.

## Engineering expectations

1. Keep the requested change narrow; avoid unrelated refactors.
2. Preserve validated functionality unless the feature explicitly changes it.
3. If UI behavior exists in both Compose and the real overlay, decide explicitly whether the feature must be implemented in both surfaces.
4. If you change a data model or stored schema, preserve backward compatibility and update `docs/DATA_SCHEMA.md`.
5. If you change AI prompts/contracts/policy/model settings, rerun the relevant golden scenarios from `docs/AI_EVALUATION.md`.
6. If you change monitoring/permissions/overlay/voice behavior, perform a physical-device check of the affected path when possible.
7. Do not use Accessibility Service as a shortcut.
8. Do not silently weaken crisis, privacy, grounding, safety, semantic-validation, or fallback behavior.

## Validation before finishing

At minimum run:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

If the change affects a real-device behavior, also install/test the affected path:

```powershell
.\gradlew.bat installDebug
```

Validation should be proportional to the change. Do not repeat unrelated OEM/battery/timezone edge matrices for a small product feature unless that feature touches those behaviors.

## Final response from the coding agent

Report:

- branch used;
- files changed;
- concise behavior summary;
- tests/build commands and results;
- emulator/physical-device check performed, if relevant;
- any known limitation introduced or still outstanding;
- confirmation that no secret was committed and the fallback path remains intact when relevant.

Do not merge to `main` unless CI passes and the project owner approves the pull request.
