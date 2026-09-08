# AI-Native Personalization — Implementation Plan

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

## Goal

Turn Mola from a generic screen-time interruption into an AI-native digital-wellbeing flow that:

1. converts a spoken or typed onboarding narrative into a structured, device-local personalization profile;
2. shows three instant profile-aware state choices when the intervention appears;
3. accepts custom text or voice;
4. generates one neutral reflection and one realistic micro-alternative; and
5. uses accumulated records to create a cautious daily reflection.

## Product principles

- The user defines the app and time limit. AI never decides what is excessive.
- Describe situations and patterns, not fixed personality labels.
- Intentional rest is valid; the intervention does not always push the user away from the phone.
- The popup must open instantly without waiting for the network.
- The app remains usable with no key, no quota, airplane mode, malformed output, or provider failure.
- Keep exactly four product screens.

## Architecture

```text
Voice or text onboarding
        ↓
ProfileIntake
        ↓
GeminiAiGateway ──failure/blank key──> LocalAiGateway
        ↓
PersonalizationProfile stored locally
        ↓
3 local quick states shown instantly in popup
        ↓
Selected state or custom voice/text
        ↓
Local crisis gate
        ↓ safe
Gemini card call ──failure/unsafe output──> deterministic fallback
        ↓
Reflection + micro-alternative + choice stored locally
        ↓
7+ same-day records
        ↓
Gemini daily synthesis, with all numeric counts computed locally
```

## Data model v2

Keep the original Android Core profile fields and add:

- `biography`: original spoken/typed onboarding narrative;
- `personalization`: goals, recurring contexts, preferred activities, low-energy alternatives, quick-state options, and tone;
- `schemaVersion`: backward-compatible migration marker.

Intervention records additionally store:

- selected state ID and label;
- input method: quick reply, text, or voice;
- generated question and alternative;
- final continue/stop choice.

Existing schema-v1 files remain readable through defaults.

## Gemini integration

### Provider classes

- `GeminiAiGateway`: profile, card, and report orchestration, parsing, safety checks, and fallback.
- `GeminiMessageClient`: isolated timeout-bounded REST transport.
- `LocalAiGateway`: complete deterministic offline implementation.

### Endpoint

The client uses Gemini `generateContent` with:

- `x-goog-api-key` authentication;
- system instruction plus one user payload;
- `responseMimeType = application/json`;
- short connect/read timeouts;
- strict extraction and validation;
- local fallback for any error or blocked generation.

### Local configuration

The key is supplied by the user at runtime and held in `AiSettingsStore`; no build file
contains one:

```text
Home -> Yazı motoru -> Kendi anahtarını ekle
GEMINI_FAST_MODEL=gemini-2.5-flash-lite
GEMINI_REPORT_MODEL=gemini-2.5-flash
```

The model IDs are configurable without changing business logic.

The app ships no credential of its own. Because requests are made with the user's own key and quota, there is nothing bundled to extract and no developer-operated proxy to abuse.

## AI operations

### 1. Profile generation

Input:

- name and optional department;
- free onboarding narrative;
- explicitly supplied hobbies, goal, and improvement area.

Output:

- 1–3 goals;
- 1–4 recurring contexts;
- 1–5 preferred activities;
- 1–3 low-energy alternatives;
- one tone enum;
- exactly six concise quick-state candidates after local default completion.

The generated profile is a tentative summary, not a diagnosis.

### 2. Intervention card

The popup first shows three quick states stored locally. No model call is needed for this first interaction.

After a state or custom response is selected, the model receives:

- local time;
- target app;
- current usage and the user's own limit;
- user-stated goal;
- profile activities and low-energy options;
- selected state or custom text;
- input method.

Output is exactly:

```json
{
  "question": "...",
  "alternative": "..."
}
```

The alternative must be concrete, realistic, and grounded in the profile. The app rejects empty, malformed, diagnostic, judgmental, or unsafe output.

### 3. Daily report

The report call runs only with at least seven same-day records. The application computes usage, intervention count, continue count, and stop count locally.

The model returns only:

```json
{
  "observation_question": "...",
  "micro_step": "..."
}
```

The observation must remain tentative and cannot claim causality.

## Voice flow

Android speech recognition produces editable text; audio is not sent directly to Gemini.

- Compose onboarding/intervention use the Activity Result API.
- The real system overlay launches a tiny transparent bridge Activity.
- The overlay hides while speech recognition is visible and restores safely afterward.
- Cancellation or recognizer unavailability falls back to text.

## Safety and privacy

- Run the crisis filter locally before every possible provider call.
- Crisis-signalling onboarding, intervention, profile, or report context never goes to Gemini.
- Never commit or log the API key.
- Keep profile and intervention records in device-local JSON.
- Length-limit model input and output.
- Filter generated profile fields, quick-state labels, cards, and reports.
- Fall back to deterministic safe output on any ambiguity.
- Explain in the UI that live-AI text is sent to Gemini and speech recognition may use the phone's configured recognition service.

## Implementation slices

### Slice 1 — Schema and persistence

- Add profile-personalization and enriched intervention models.
- Preserve v1 read compatibility.
- Update demo data and schema documentation.

Acceptance: old state loads and v2 data round-trips.

### Slice 2 — Offline AI contract

- Expand `AiGateway` with suspend profile/card/report operations.
- Implement all behavior first in `LocalAiGateway`.
- Keep outputs deterministic and safety-clean.

Acceptance: full flow works with a blank key.

### Slice 3 — Narrative onboarding and voice

- Add prominent voice/text biography input.
- Generate and display a structured profile summary.
- Save it with the existing target app, limit, and permissions.

Acceptance: spoken or typed onboarding produces a saved local profile.

### Slice 4 — AI-first intervention

- Show three quick states immediately.
- Add custom voice/text.
- Generate a reflection and alternative asynchronously.
- Save context, output, and final choice.

Acceptance: useful card is reachable in one tap and popup opening never depends on network.

### Slice 5 — Gemini transport

- Use timeout-bounded `generateContent` REST requests.
- Read key/models from BuildConfig values sourced from ignored local properties.
- Handle HTTP errors, quota errors, safety blocks, empty candidates, malformed JSON, and unsafe wording through fallback.

Acceptance: valid key gives live output; all failures remain usable offline.

### Slice 6 — Daily memory

- Include repeated states and final choices in report context.
- Keep all numeric summaries local.
- Produce one question and one micro-step after seven records.

Acceptance: report references visible patterns without diagnosis or causal claims.

### Slice 7 — Device hardening

- Verify voice cancellation, overlay restoration, airplane mode, live Gemini, data migration, data deletion, and lock-screen behavior.
- Rehearse both live and offline demo paths.

Acceptance: the same stage demo succeeds with Gemini available or unavailable.

## Validation

Automated:

```bash
./gradlew test
./gradlew assembleDebug
```

Physical-device QA:

```bash
./gradlew installDebug
```

Then verify:

- offline profile, card, and report fallback;
- live Gemini profile, card, and report;
- three instant overlay quick states;
- custom text and voice;
- both final actions;
- crisis short-circuit;
- airplane-mode fallback;
- v1 profile migration;
- complete data deletion.

See `docs/AI_DEVICE_QA.md` for the exact phone checklist.

## Rollback

`LocalAiGateway` remains complete and is the shipped default. Deleting the key in app settings disables live calls without changing product behavior. Schema-v2 fields have defaults, so the feature can fall back without deleting existing user data.
