# Mola Baseline Finalization

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

This document is the source of truth for the current hackathon baseline before optional feature work continues.

Baseline branch: `feature/final-integration`

The goal of this pass is to finish the current product and repository workflow first. Optional features should branch from this known-good baseline rather than being mixed into unresolved integration work.

## Product status

| Area | Status | Notes |
|---|---|---|
| Onboarding | PASS | Narrative-first text/voice input; generated profile is grounded against supplied evidence. |
| Target app selection | PASS | Launchable apps are listed; Mola itself is excluded. |
| User-defined threshold | PASS | Threshold is always entered/changed by the user. AI does not choose it. |
| Usage Access | PASS | Guided system-settings flow validated. |
| Usage measurement | PASS | `UsageStatsManager` values validated against the selected target app. |
| Foreground monitoring | PASS | Foreground service and 60-second polling validated. |
| Monitoring notification | IMPLEMENTED | Android 13+ notification permission is requested contextually when monitoring starts, remains optional for core tracking, and can be repaired from Home. |
| Threshold trigger | PASS | Real target-app trigger validated on physical Android hardware. |
| Cooldown | PASS | 15-minute cooldown works; changing the limit resets it for testing. |
| System overlay | PASS | `TYPE_APPLICATION_OVERLAY` appears above the target app and is usable on the physical phone. |
| Quick-state intervention | PASS | Personalized local quick states appear without an AI call. |
| Custom text | PASS | Custom context reaches the card pipeline and can override a generic state. |
| Voice | PASS | Physical-device speech recognition returns text to the intervention flow. |
| Live intervention AI | PASS | Profile-grounded/state-aware cards validated. |
| AI semantic validation | PASS | Structured need/strategy/duration/anchor contract plus display-safety validation. |
| Repair/fallback | PASS | One repair attempt is bounded; deterministic local fallback remains usable offline. |
| Intentional continuation | PASS | User can intentionally continue; no forced blocking. |
| Stop/try alternative action | PASS | User can dismiss the target-app moment and return toward launcher/home. |
| In-app navigation | IMPLEMENTED | Android system Back from Compose intervention/report returns Home rather than unexpectedly exiting the activity. |
| Local records | PASS | Intervention records persist locally in app-private JSON storage. |
| Daily report threshold | PASS | Live synthesis is withheld below seven records. |
| Daily report counts | PASS | Numeric facts are computed locally. |
| Daily report AI | PASS | Evidence-constrained live report validated after gateway fixes. |
| Daily report loading UX | IMPLEMENTED | Home now shows/disables the report action while the reflection is being generated instead of appearing frozen. |
| Data deletion | PASS / polished | Clear-data logic was validated; the action is now visible in a product-level data section and requires destructive confirmation. |
| Backup privacy | PASS | Profile/records and monitoring preferences are excluded from cloud backup and device transfer. |
| App identity | IMPLEMENTED | Explicit Mola launcher/round icon and foreground-monitor notification icon replace generic/default identity. |
| CI | PASS / simplified | CI runs for PRs into `feature/final-integration` or `main`, plus pushes to `main`, avoiding duplicate feature-push + PR runs. |

## Combined QA gate — 2026-08-30

### Emulator

Android 16 Google emulator (`sdk_gphone64_x86_64`).

| Test | Result | Relevant source/result |
|---|---|---|
| Fresh onboarding | PASS | Grounded profile; no invented activities. `profile source=live`, ~2.2 s. |
| Real overlay / tired state | PASS | Low-effort suggestion; no workout mismatch. `card source=live`, ~1.1 s. |
| Procrastination text | PASS | Concrete micro-start distinct from tired flow. `card source=live`, ~1.0 s. |
| Voice + decisions | PASS with emulator limitation | Both decisions worked; recognizer failure degraded gracefully because emulator has no real microphone path. |
| Offline fallback | PASS | Instant safe fallback; no hang/crash. `source=local_fallback`, network failure. |
| Daily report | PASS after gateway fix | Evidence-grounded reflection and 2-minute micro-step; `report source=live`, ~5.8 s. |

### Report gateway issue found during QA

Two issues were reproduced directly against the API and fixed in commit `9538ed7`:

1. The report's previous 520-token budget could be consumed by model reasoning, producing `MAX_TOKENS` before the JSON answer. The report budget is now 2,048 tokens.
2. `additionalProperties` was not accepted by the tested report model's structured-output schema subset. The unsupported field was removed from the app schemas so structured output is used directly instead of unnecessarily falling back to schema-less generation.

The validated report model is now also the repository default, so a fresh local configuration matches the tested baseline unless explicitly overridden.

### Physical Android phone

The combined build was installed and smoke-tested after integration and the report gateway fixes.

Confirmed:

- Home/product UI loads correctly;
- foreground monitoring can be restarted after reinstall;
- real overlay triggers above the selected target app;
- quick-state card flow works;
- physical speech recognition returns to the intervention;
- both final decisions dismiss correctly;
- seeded daily report opens and generates successfully.

A finalization-only polish pass was added afterward: launcher/notification icons, report loading state, visible/confirmed data deletion, standard system-Back routing, and Android 13+ monitoring-notification permission handling. After pulling the latest baseline, these additions need only a short smoke check; they do not change the validated threshold/overlay/AI decision logic.

## AI configuration used for the validated demo build

```properties
GEMINI_PROFILE_MODEL=gemini-2.5-flash-lite
GEMINI_CARD_MODEL=gemini-2.5-flash-lite
GEMINI_REPORT_MODEL=gemini-3.6-flash
```

The API key remains only in ignored `local.properties` and is never committed. The direct client is explicitly hackathon-only.

## Privacy and safety baseline

The current baseline intentionally includes these controls:

- no account or remote app database;
- profile and records stored in app-private local storage;
- relevant context may be sent to Gemini only for live AI generation, as disclosed in onboarding;
- sensitive persisted state excluded from Android backup/device transfer;
- visible in-app local-data deletion with confirmation;
- foreground tracking has explicit app/notification identity and optional notification permission;
- no API key in source control;
- crisis-signalling text bypasses Gemini;
- AI never sets or recommends the usage threshold;
- local policy constrains need, strategy, duration, and personalization anchors;
- generated language is checked for unsafe/judgmental content;
- report numeric facts are computed locally;
- debug AI diagnostics omit raw biography/custom/crisis text.

## Baseline repository workflow

The earlier role branches and parallel UI/AI branches are finished implementation history. PRs #2, #4, and #5 are closed as superseded. PR #7 is the single final integration PR and targets protected `main` directly.

```text
main
  ^
  | PR #7 (single final submission PR; keep draft while optional features continue)
  |
feature/final-integration
  ^
  |
feature/<small-feature>
```

### Rules

1. `main` stays protected and receives no direct development commits.
2. `feature/final-integration` is the current known-good candidate.
3. Every optional feature begins from the latest `feature/final-integration`.
4. A feature branch owns one coherent change; avoid mixing unrelated polish or refactors.
5. Open the feature PR back into `feature/final-integration`; CI runs there.
6. Run `test` and `assembleDebug` and perform the smallest device check that exercises the changed behavior.
7. PR #7 remains the single final PR to `main` until feature work stops.
8. Do not merge PR #7 without the project owner's explicit final-merge request.
9. At final freeze, mark PR #7 ready, obtain the required approval, squash-merge to `main`, and rotate/delete the hackathon API key after the demo/submission.

Current instructions are synchronized across `AGENTS.md`, `COPILOT_PROMPT.md`, `.github/copilot-instructions.md`, `.github/prompts/mola-build.prompt.md`, and `.github/instructions/android.instructions.md`. `docs/README.md` separates current source-of-truth documents from historical sprint/handoff material.

## Non-feature release gates

These are final release/submission tasks, not new product features:

- keep GitHub Actions green after each integrated feature;
- after the latest finalization polish, do one short install/visual smoke check for the launcher icon, monitoring notification/permission, report-loading state, data-delete confirmation, and system Back behavior;
- update this document when a feature changes the validated demo route;
- run the exact final demo route twice consecutively after the last optional feature is merged;
- capture a backup screen recording of the final demo route;
- set the final version name/code only after optional feature work stops;
- verify the API key is present only on the demo machine/phone build and not in Git history;
- prepare the required 1–2 page hackathon report and roughly five-minute demo;
- after submission/demo, rotate or revoke the embedded hackathon key.

## Deliberately deferred edge cases

The following are useful robustness work but are not blockers for the baseline and should not be mixed into product-feature work unless time remains:

- OEM-specific background killing/battery restrictions;
- OEM-specific notification-channel/task-manager presentation differences;
- very long unattended service lifetime;
- unusual clock/timezone transitions;
- speech recognizer/provider-specific cancellation races;
- exhaustive migration/corrupt-file fuzzing;
- localization beyond the current Turkish product experience.

## Optional feature branch template

```powershell
git fetch origin
git switch feature/final-integration
git pull --ff-only
git switch -c feature/<feature-name>
```

After implementation:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
git push -u origin feature/<feature-name>
```

Open a narrow PR into `feature/final-integration`, let CI pass, run the affected device path, then merge the feature into the integration candidate. PR #7 will automatically accumulate the integrated feature for the eventual final merge to `main`.
