# Implementation Handoff: Mola Product UI Redesign

> **Historical document.** This captures a phase of the project's hackathon development and
> is retained as implementation history. It may not match the current code. For current
> behavior see [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md) and the repository [`README.md`](../README.md).

## Goal

Transform the current functional but visibly barebones Compose interface into a calm, coherent, demo-ready digital-wellbeing product while preserving every validated behavior: onboarding/profile generation, usage monitoring, threshold overlay, quick replies, text/voice input, Gemini/fallback cards, final choices, and the daily report. The finished app must still have exactly four product screens and must not depend on unfinished backend work.

## Non-goals

- Do not change `AiGateway`, prompts, Gemini transport, safety logic, persistence, usage monitoring, cooldown behavior, or the data schema.
- Do not add a fifth product screen, bottom-navigation architecture, accounts, login, cloud sync, charts, external content integrations, or new backend services.
- Do not redesign by deleting existing callbacks or testability.
- Do not turn the app into a neon/tech demo. It should feel like a calm wellbeing product.
- Do not add a large UI framework or dependency unless the existing Compose/Material 3 stack cannot reasonably implement the design.

## Current evidence from the repo

- `app/src/main/java/com/yberkayinci/mola/ui/theme/Theme.kt`: currently selects the default Material 3 light/dark color schemes and defines no product-specific colors, type scale, shapes, or component treatment.
- `app/src/main/java/com/yberkayinci/mola/ui/HomeScreen.kt`: currently renders a single vertical list of default cards, text fields, buttons, permission rows, and a visible `Hackathon testleri` section.
- `app/src/main/java/com/yberkayinci/mola/ui/OnboardingScreen.kt`: contains the complete narrative/voice/profile/app/limit/permission flow, but presents it as one long form.
- `app/src/main/java/com/yberkayinci/mola/ui/InterventionScreen.kt`: exposes all required quick-state, text, voice, loading, crisis, AI-card, and final-choice behavior using mostly default components.
- `app/src/main/java/com/yberkayinci/mola/ui/DailyReportScreen.kt`: correctly separates local numbers from AI reflection, but currently looks like a debug summary.
- `app/src/main/java/com/yberkayinci/mola/overlay/OverlayController.kt`: is the real system intervention surface; its interaction behavior is validated and must be preserved while its visual hierarchy is improved.
- `app/src/main/java/com/yberkayinci/mola/MolaApp.kt`: already owns the four-screen flow and callbacks. Avoid editing it unless a UI requirement is impossible otherwise.

## Assumptions and open questions

- Assumption: Turkish remains the demo language.
- Assumption: the demo phone is the primary visual target; layouts must still tolerate common Android phone sizes and font scaling.
- Assumption: the existing `feature/ai-personalization` behavior is the frozen functional baseline.
- Assumption: debug/demo controls must remain reachable, but they do not need to dominate the normal Home screen.
- Open question: final accent color can be adjusted after first device screenshots, but the implementation should begin with a warm neutral surface, near-black text, and one restrained green/teal accent.

## Design approach

Create one small product design system and apply it consistently to all visible surfaces.

### Product character

- Calm, mature, supportive, and non-clinical.
- Warm light neutral background with a strong dark foreground and one restrained accent.
- Large readable headings, generous spacing, rounded surfaces, minimal borders, and clear action hierarchy.
- Use color to communicate state, not decoration.
- The user’s own target and choice remain central; AI should not visually appear authoritative or medical.

### Shared structure

Introduce reusable Compose components rather than restyling every screen independently, for example:

- `MolaScreen` / `MolaTopBar`
- `MolaCard`
- `StatusPill`
- `SectionTitle`
- `PrimaryActionButton`
- `SecondaryActionButton`
- `QuickStateButton`
- `StatItem`
- `PermissionBanner`
- `DeveloperToolsCard`

Use Material 3 under the hood, but define explicit color schemes, typography, shapes, and component defaults.

### Screen hierarchy

#### Onboarding

- Make the narrative voice/text input the hero interaction.
- Keep name, target app, limit, and permissions clearly required.
- Present optional details as secondary/supporting inputs rather than equal-weight fields.
- Present generated profile output as compact goal/context/activity chips or cards.
- Keep loading, crisis, fallback, and permission behavior unchanged.

#### Home

- Lead with brand/status and today’s selected-app usage.
- Use one strong usage card with large current minutes, target, progress, and monitoring state.
- Put the report in a prominent reflection card/action.
- Show permission remediation only when a permission is missing.
- Move limit editing and monitoring settings into compact settings rows/cards.
- Keep test controls behind a clearly marked, collapsed/low-priority developer section, preferably shown only in debug builds when practical.

#### Intervention

- Treat this as the hero product surface.
- Emphasize the question `Şu an seni burada tutan ne?` and three large quick-state choices.
- Keep text and voice as secondary routes.
- Loading must feel intentional and short.
- Show one focused AI response card with a clear primary `Bunu deneyeceğim` action and a visually secondary `Yine de devam et` action.
- Preserve crisis routing, response editing, and all callbacks.

#### Daily report

- Present local facts compactly as trustworthy stats.
- Present the generated observation and micro-step as two distinct reflection cards.
- Preserve the insufficient-data state and the rule that numbers are locally computed.

#### System overlay

- Match the Compose intervention design as closely as practical without changing the validated lifecycle.
- Preserve focus/keyboard/voice bridge behavior, dismissal, cooldown, and both final actions.
- Avoid animation or layout changes that make the overlay slow to appear.

## Files likely to change

| Path | Change |
|---|---|
| `app/src/main/java/com/yberkayinci/mola/ui/theme/Theme.kt` | Define explicit light/dark schemes, typography, shapes, and system-bar behavior if needed. |
| `app/src/main/java/com/yberkayinci/mola/ui/theme/Color.kt` | Add product color tokens. |
| `app/src/main/java/com/yberkayinci/mola/ui/theme/Type.kt` | Add product typography tokens if useful. |
| `app/src/main/java/com/yberkayinci/mola/ui/components/*` | Add reusable cards, buttons, status, stats, chips, banners, and screen scaffolding. |
| `app/src/main/java/com/yberkayinci/mola/ui/OnboardingScreen.kt` | Redesign hierarchy while preserving state and callbacks. |
| `app/src/main/java/com/yberkayinci/mola/ui/HomeScreen.kt` | Replace debug-form layout with a product dashboard and discreet developer tools. |
| `app/src/main/java/com/yberkayinci/mola/ui/InterventionScreen.kt` | Redesign quick states, custom input, loading, crisis, and AI-card states. |
| `app/src/main/java/com/yberkayinci/mola/ui/DailyReportScreen.kt` | Redesign stats and reflection hierarchy. |
| `app/src/main/java/com/yberkayinci/mola/overlay/OverlayController.kt` | Apply the same visual language to the real overlay without altering lifecycle logic. |
| `app/src/main/res/values/themes.xml` | Adjust platform window/theme details only if required. |
| `app/src/main/res/values/strings.xml` | Centralize or refine Turkish copy if the file exists/gets introduced. |

## Implementation slices

### Slice 1: Product design system and reusable components

**Intent:** Prevent four inconsistent one-off redesigns.

**Steps:**
1. Define the color, typography, shape, spacing, and elevation direction in the theme package.
2. Add a small set of reusable components used by at least two screens.
3. Keep all components stateless where practical and add Compose previews for key states.

**Validation:** `./gradlew test` and `./gradlew assembleDebug`; inspect previews and one installed screen.

**Acceptance criteria:** Screens can share a consistent background, headings, cards, buttons, status pills, and spacing without changing feature behavior.

### Slice 2: Home screen product dashboard

**Intent:** Replace the screen that most obviously looks like a developer panel.

**Steps:**
1. Create a branded top section with monitoring status.
2. Create a prominent usage/target/progress card.
3. Create a clear report/reflection entry point.
4. Show missing permissions as actionable banners only when needed.
5. Convert monitoring and limit controls to compact settings surfaces.
6. Move hackathon controls to a low-priority/collapsible developer tools area.

**Validation:** Manually verify refresh, report, limit save, monitoring start/stop, app launch, demo-data load, intervention test, and clear-data callbacks.

**Acceptance criteria:** A first-time viewer can understand current usage, target, monitoring status, and the next useful action within five seconds; debug controls do not dominate.

### Slice 3: Narrative-first onboarding redesign

**Intent:** Make AI personalization feel like the product’s beginning rather than a long form.

**Steps:**
1. Establish a short welcome/benefit hierarchy.
2. Make voice/text narrative input the primary card.
3. Reorganize optional fields visually without removing them.
4. Redesign generated profile summary as concise, readable surfaces.
5. Group target app, limit, and permissions into a clear setup section.
6. Preserve validation, crisis handling, loading, fallback, and save behavior.

**Validation:** Test typed narrative, voice result, voice cancellation, generated profile, missing required fields, app picker, permissions, and completion.

**Acceptance criteria:** The user can understand what to say, generate a profile, review it, and finish setup without the screen feeling like a technical questionnaire.

### Slice 4: Intervention screen and real overlay redesign

**Intent:** Make the core demo moment feel intentional and finished.

**Steps:**
1. Redesign the initial quick-state screen around three large one-tap options.
2. Redesign text/voice routes and loading state.
3. Redesign crisis state with calm, direct hierarchy and no AI styling.
4. Redesign generated card and final actions.
5. Port the same visual hierarchy to `OverlayController` while preserving validated behavior.

**Validation:** Test quick reply, typed input, voice input/cancellation, Gemini card, offline fallback, crisis route, response change, `Deneyeceğim`, `Yine de gir`, cooldown, and overlay keyboard behavior.

**Acceptance criteria:** The initial overlay appears immediately, is readable at a glance, and lets the user reach a useful decision in one or two taps.

### Slice 5: Daily report redesign

**Intent:** Turn the report into a credible reflection rather than a statistics/debug page.

**Steps:**
1. Use compact local-number stats with clear labels.
2. Present the observation and micro-step as separate, calm cards.
3. Improve the insufficient-data state without implying failure.
4. Keep the back action clear and consistent.

**Validation:** Test fewer than seven records and seeded eight-record state, with live and fallback report generation.

**Acceptance criteria:** The user can distinguish factual local metrics from AI-generated reflection, and the screen reads cleanly in a demo recording.

### Slice 6: Accessibility, device polish, and visual freeze

**Intent:** Finish the UI without destabilizing the app.

**Steps:**
1. Check touch target sizes, contrast, large font scaling, scrolling, keyboard overlap, and screen-reader labels where relevant.
2. Run the exact five-minute demo route twice on the physical phone.
3. Capture screenshots of all four screens and both major overlay states.
4. Fix only concrete visual or usability issues; freeze afterward.

**Validation:** `./gradlew test`, `./gradlew assembleDebug`, `./gradlew installDebug`, physical-device demo rehearsal.

**Acceptance criteria:** No screen looks like default scaffolding, all callbacks still work, and the complete demo route succeeds twice consecutively.

## Tests and verification

- Unit tests: preserve all existing tests; add pure formatting/state tests only where they provide value.
- Compose tests: optional but useful for visibility/enabled-state of permission, report, quick-state, and developer controls.
- Integration/e2e tests: do not attempt to automate the full system overlay unless already supported; use the physical phone.
- Manual QA: onboarding -> Home -> target app -> system overlay -> quick state -> AI card -> final choice -> seeded report.
- Commands to run: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew installDebug`.

## Edge cases and failure modes

- Small screens / large fonts: all screens must scroll and important actions must remain reachable.
- Missing permissions: show remediation without making the healthy state noisy.
- No API/network: loading must end and fallback card must use the same layout.
- Crisis input: do not render it as an ordinary AI recommendation card.
- Long generated text: cap/scroll gracefully without hiding final actions.
- Overlay keyboard/voice bridge: visual changes must not recreate duplicate windows or break focus.
- Dark mode: either support it intentionally or lock the demo theme deliberately; do not leave accidental default colors.
- Teammate changes: do not overwrite data/AI/core work to achieve visual goals.

## Rollback plan

Keep behavior-preserving commits by slice. If the overlay redesign destabilizes lifecycle behavior, revert only the overlay visual commit while keeping the Compose redesign. If a shared component causes widespread layout issues, screens can temporarily fall back to direct Material components without reverting functional code.

## Executor prompt for fresh session

You are implementing the Mola product UI redesign on branch `feature/ui-product-redesign`. Before editing, inspect the repository and verify this plan against the actual code. If the plan is wrong or stale, briefly update it and explain the mismatch before editing.

Goal:
Turn the current functional but barebones four-screen Android prototype into a calm, coherent, demo-ready digital-wellbeing product while preserving every validated behavior: onboarding/profile generation, usage monitoring, threshold overlay, quick replies, text/voice input, Gemini/fallback cards, final choices, and daily reporting.

Known repo evidence:
- The branch is based on `feature/ai-personalization`, which contains the current validated working flow.
- `ui/theme/Theme.kt` currently uses default Material 3 color schemes only.
- Home is a vertical stack of default controls with a visible `Hackathon testleri` section.
- Onboarding, Intervention, and Daily Report contain the required behavior but mostly use default Material components.
- `OverlayController.kt` is the real validated system overlay.
- `MolaApp.kt` already owns the four-screen callbacks and should not be edited unless unavoidable.

Plan:
1. Add a small product design system and reusable Compose components.
2. Redesign Home into a product dashboard and move debug controls to a discreet developer section.
3. Redesign onboarding around narrative voice/text and a readable AI profile summary.
4. Redesign the Compose intervention and real overlay while preserving lifecycle and all actions.
5. Redesign the daily report around factual stats plus two reflection cards.
6. Run accessibility/device polish and freeze after two successful demo rehearsals.

Ownership boundaries:
- You own `ui/**`, `ui/theme/**`, UI resources, and visual-only edits to `overlay/OverlayController.kt`.
- Do not edit `ai/**`, Gemini prompts/transport, data models, repository/persistence, monitoring, cooldown, or usage logic.
- Do not add a fifth product screen.
- Preserve every existing callback and functional state.

Validation commands:
- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew installDebug`

Manual validation:
- typed and voice onboarding
- generated profile and fallback
- Home refresh/limit/monitoring/permissions
- real target-app overlay
- quick reply/text/voice/crisis/loading/fallback
- both final actions
- fewer-than-seven and seeded report states

Execution rules:
1. Do not assume file paths are correct; verify them first.
2. Report plan/code mismatches before changing code.
3. Preserve existing behavior unless the plan explicitly changes presentation.
4. Implement one slice at a time and commit in reviewable increments.
5. Run the most relevant validation after each meaningful slice.
6. If tests fail, diagnose and fix before moving on unless clearly unrelated.
7. Keep changes minimal and idiomatic for this Compose codebase.
8. Do not overwrite or undo concurrent AI-quality work.

Final response must include:
- Files changed
- Summary of each completed UI slice
- Validation commands run and results
- Physical-device checks completed
- Screenshots or a concise visual QA summary
- Any unresolved issues or follow-up work
