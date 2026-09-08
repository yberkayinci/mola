# Implementation Handoff: Mola Android Hackathon Prototype

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

## Goal

Deliver an end-to-end Android prototype in which a user selects one app and a daily minute limit, explicitly starts monitoring, and receives a neutral intervention overlay after that limit is reached while the selected app is active. The app must ask what is happening, run a local crisis-language gate, produce one personalized AI card or deterministic fallback, record **Yine de gir** or **Vazgeçtim**, and produce an eligible daily report from device-local data.

## Non-goals

Do not add accounts, passwords, cloud persistence, social features, streaks, gamification, push campaigns, medical diagnosis, addiction scores, Accessibility Service automation, more than four product screens, or unrelated architecture refactors. Do not let AI set or recommend the user’s limit. Do not make the demo depend on network availability.

## Current evidence from the repo

- `app/src/main/java/com/yberkayinci/mola/data/Models.kt`: profile, intervention record, card, and report models exist.
- `app/src/main/java/com/yberkayinci/mola/data/JsonMolaRepository.kt`: the frozen Turkish-key JSON contract is persisted in app-private storage.
- `app/src/main/java/com/yberkayinci/mola/ai/LocalAiGateway.kt`: deterministic card and report outputs already unblock UI work.
- `app/src/main/java/com/yberkayinci/mola/ai/AnthropicAiGateway.kt`: real-network integration seam exists but intentionally falls back to the mock.
- `app/src/main/java/com/yberkayinci/mola/ui/`: all four product screens exist as Compose files.
- `app/src/main/java/com/yberkayinci/mola/monitor/UsageMonitorService.kt`: user-started foreground service polls at 60-second intervals.
- `app/src/main/java/com/yberkayinci/mola/usage/UsageStatsReader.kt`: Usage Access checks, daily usage, and recent foreground-package lookup exist.
- `app/src/main/java/com/yberkayinci/mola/overlay/OverlayController.kt`: a basic full-screen application overlay exists and persists choices.
- `app/src/main/java/com/yberkayinci/mola/usage/CooldownPolicy.kt`: 15-minute cooldown logic is isolated and tested.
- `app/src/test/`: pure tests cover cooldown, crisis language, output language, report eligibility, and date grouping.
- `docs/DATA_SCHEMA.md`: the team contract is already written and should be frozen before parallel work.
- The system integration has not yet been proven on the team’s physical demo phone; repository code is evidence of intent, not proof of OEM behavior.

## Assumptions and open questions

- Assumption: one selected target application is enough for the judged prototype.
- Assumption: debug controls remain on Home and therefore do not count as a fifth product screen.
- Assumption: a mobile-direct Anthropic call is accepted only for a hackathon demonstration; production uses a backend proxy.
- Assumption: all daily grouping uses the phone’s current local timezone and `zaman_ms` as the authoritative timestamp.
- Open question: which current Anthropic Haiku-class and Sonnet-class model IDs the team’s account supports. Verify official provider documentation at implementation time and keep IDs configurable.
- Open question: whether the demo phone’s OEM requires battery-optimization exceptions. Test rather than assuming.

## Design approach

Use a deliberately small architecture:

```text
Compose screens -> MolaApp integration state -> MolaRepository
                                      |-> AiGateway -> real implementation or LocalAiGateway
Foreground service -> UsageStatsReader -> CooldownPolicy -> OverlayController -> MolaRepository
```

The foreground service is started only from an explicit user action. Each poll checks: profile exists, Usage Access is granted, overlay permission is granted, screen is interactive and unlocked, no overlay is already visible, the selected package is the latest foreground package, today’s numeric use has reached the user’s limit, and the 15-minute cooldown has expired.

The local crisis gate runs before any AI request. Real AI calls must be asynchronous, timeout-bounded, strict-JSON-only, and guarded by the deterministic output validator. Any timeout, HTTP error, parse error, empty field, blocked wording, or model refusal uses `LocalAiGateway` output. Numeric report fields are always computed locally. The daily report receives only records on the current local date and is unavailable below seven records.

## Files likely to change

| Path | Change |
|---|---|
| `app/src/main/java/com/yberkayinci/mola/ai/AnthropicAiGateway.kt` | Add the real timeout-bounded HTTP implementation without committing a key. |
| `app/src/main/java/com/yberkayinci/mola/ai/AiGateway.kt` | Convert to suspend/asynchronous API if needed; coordinate all callers. |
| `app/src/main/java/com/yberkayinci/mola/ai/AiPrompts.kt` | Final card/report prompts and strict output schemas. |
| `app/src/main/java/com/yberkayinci/mola/overlay/OverlayController.kt` | Async loading/error state, lifecycle polish, and device fixes. |
| `app/src/main/java/com/yberkayinci/mola/monitor/UsageMonitorService.kt` | Device-specific trigger fixes and observability. |
| `app/src/main/java/com/yberkayinci/mola/usage/UsageStatsReader.kt` | Improve foreground detection or day-boundary handling if device tests require it. |
| `app/src/main/java/com/yberkayinci/mola/ui/*.kt` | Loading/error copy and visual polish without adding screens. |
| `app/src/main/java/com/yberkayinci/mola/MolaApp.kt` | Coordinated integration wiring only. |
| `app/build.gradle.kts` | Add one small HTTP/JSON dependency set if required. |
| `app/src/test/**` | Add parser, prompt-contract, fallback, and eligibility tests. |
| `docs/**` | Record device results, ethics, demo procedure, and known limitations. |

## Implementation slices

### Slice 1: Freeze interfaces and preserve the mock

**Intent:** Let all three roles work without waiting.

**Steps:**

1. Review `docs/DATA_SCHEMA.md` as a team and do not rename fields.
2. Run or inspect pure unit tests.
3. Confirm every UI path can use `LocalAiGateway` without network access.
4. Keep the mock as a permanent fallback, not temporary code to delete.

**Validation:** Load demo data, open the intervention debug flow, make both choices, and open the report with airplane mode enabled.

**Acceptance criteria:** Four screens work through the debug path with no remote API.

### Slice 2: Pass the Android system checkpoint

**Intent:** Retire the highest technical risk before polishing AI.

**Steps:**

1. Grant Usage Access and Draw Over Other Apps on the physical demo phone.
2. Select a real installed target app and set a temporary low limit.
3. Start monitoring from Home.
4. Verify daily usage and current foreground package readings in controlled tests.
5. Open the target app after the threshold and wait through one poll interval.
6. Fix only the minimum service, usage, manifest, or overlay code necessary.

**Validation:** Observe an Mola overlay above the selected app on the physical phone.

**Acceptance criteria:** The overlay appears when all conditions are true, does not appear below the limit, and does not appear when another app is active.

### Slice 3: Complete choice persistence and cooldown

**Intent:** Make the core behavioral loop reliable without AI dependency.

**Steps:**

1. Confirm **Yine de gir** records `yine_de_gir`, dismisses the overlay, and leaves/returns the target app in front.
2. Confirm **Vazgeçtim** records `vazgectim`, dismisses the overlay, and moves away from the target app.
3. Confirm a successful overlay show writes the cooldown timestamp.
4. Confirm the overlay does not reappear for 15 minutes and can reappear at the boundary.
5. Reset cooldown when profile/limit data is reset for testing.

**Validation:** Inspect `mola_state.json` through Android Studio Device Explorer and repeat controlled app opens.

**Acceptance criteria:** Exactly one record is saved per completed card and cooldown behavior matches the rule.

### Slice 4: Add real card AI behind the interface

**Intent:** Make generative AI meaningful without making the demo fragile.

**Steps:**

1. Verify the current provider model ID and keep it configurable.
2. Keep credentials outside Git and inject only for the local demo build.
3. Send the user text, local time, numeric usage/limit, hobbies, reason, and optional profile context.
4. Require JSON with exactly `question` and `alternative` strings.
5. Add connection/read timeout, non-2xx handling, strict parser, blank-field rejection, language validation, and deterministic fallback.
6. Make the call asynchronous; never block the Compose or overlay main thread.
7. Never send text that matches the crisis gate.

**Validation:** Test success, airplane mode, invalid JSON, timeout, HTTP error, blocked wording, and blank fields.

**Acceptance criteria:** A valid result displays; every failure displays safe deterministic content and still lets the user choose.

### Slice 5: Complete the daily report

**Intent:** Turn local interactions into one bounded synthesis.

**Steps:**

1. Filter records to the current local date using `zaman_ms`.
2. Compute total count and choice counts locally.
3. Under seven eligible records, skip the network and show **Yeterli veri yok**.
4. At seven or more records, call the configurable Sonnet-class model.
5. Require exactly `observation_question` and `micro_step` strings.
6. Validate that the observation is a question and all generated text passes banned-language checks.
7. Fall back deterministically on every failure.

**Validation:** Test six records, seven records, midnight boundary, mixed-date seed data, invalid output, and offline mode.

**Acceptance criteria:** The report has numeric facts, one question, and one micro-step, with no model-generated arithmetic.

### Slice 6: Device hardening and demo packaging

**Intent:** Make judged behavior repeatable.

**Steps:**

1. Test permission denial/revocation, screen lock, overlay already open, target app removed, service stopped, process death, clock rollback, midnight, and OEM battery handling.
2. Prepare a four-day seed state with at least seven records today.
3. Write a 60–90 second deterministic demo script.
4. Rehearse three times on the final phone.
5. Record a backup video and document known limitations honestly.

**Validation:** Complete three consecutive rehearsals without changing code or manually repairing data.

**Acceptance criteria:** Live demo and backup video both show the same core loop and populated report.

## Tests and verification

- Unit tests: cooldown boundaries, clock rollback, crisis phrases, ordinary non-crisis text, banned whole words, safe word fragments, report threshold, local-date grouping, strict AI JSON parsing, and fallback selection.
- Integration tests: repository append/load, today-only filtering, choice counts, and mocked network response handling.
- Manual physical-device QA: permission grant/revoke, below/at/above threshold, target/non-target foreground, overlay interaction, continue/stop behavior, 15-minute cooldown, lock screen, battery optimization, and midnight.
- Commands after wrapper bootstrap: `./gradlew test`, `./gradlew assembleDebug`.

## Edge cases and failure modes

- Permissions denied or revoked: show repair controls; do not crash or claim monitoring works.
- Notification permission denied: verify foreground-service behavior on the demo Android version and document it.
- Target app missing: disable/open failure safely and return to app selection rather than crashing.
- Usage event unavailable or stale: skip the intervention for that poll.
- Screen locked: never show the intervention.
- Overlay already visible: never create a duplicate.
- Cooldown clock moves backward: allow recovery instead of locking the user out indefinitely.
- Local midnight: new-day usage/report selection must reset by local date.
- Network unavailable, timeout, rate limit, refusal, invalid JSON, or blocked wording: deterministic fallback.
- Crisis match: no network call, no model output, support-oriented local state.
- Fewer than seven records today: no report-model call.
- Process/service killed: app should remain usable; be honest about any monitoring limitation.

## Rollback plan

Real AI is isolated behind `AiGateway`. If networking or parser work destabilizes the demo, instantiate `LocalAiGateway` and retain the entire local core loop. If service triggering remains unreliable on the demo OEM after the Saturday checkpoint, keep the in-app intervention debug control as an explicit backup and disclose that the system trigger is a prototype limitation rather than faking successful monitoring.

## Executor prompt for fresh session

Use `COPILOT_PROMPT.md`. It restates the goal, repository evidence, slice order, validation commands, execution rules, and required final report so a fresh Copilot/Codex session does not depend on prior conversation context.
