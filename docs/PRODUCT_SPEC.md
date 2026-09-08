# Product Specification: Mola

## One sentence

Mola is an Android digital-wellbeing app that creates a short personalized pause after the user reaches a daily usage threshold they set for one app, then helps them choose intentionally whether to continue or do something else.

## Product principles

1. **The threshold belongs to the user.** Mola and Gemini never define what “too much” means.
2. **Context before advice.** The intervention asks what is happening now instead of treating all high-use moments as the same.
3. **AI supports a decision; it does not make the decision.** Intentional rest/use is allowed.
4. **No diagnosis or person-labeling.** Mola does not diagnose addiction, mental-health conditions, personality, or motives.
5. **Local facts stay local where possible.** Usage, records, numeric report facts, and the crisis gate are device-side.
6. **Failure must remain usable.** Provider/network/validation failure returns a deterministic local card.

## Product surfaces

There are four in-app product screens plus the real Android system overlay.

### 1. Onboarding

The user provides:

- name;
- a narrative in their own words by text or Android speech recognition;
- optional field/department, hobbies/activities, improvement area, and goal;
- target app;
- daily minute threshold they choose themselves.

Gemini structures the narrative into a personalization profile containing:

- a concise grounded profile summary;
- concrete focus targets the user wants to begin, return to, or protect;
- explicit goals;
- recurring contexts rather than personality labels;
- explicitly supplied preferred activities;
- realistic low-energy alternatives;
- tone;
- six concise quick states.

Generated profile fields are sanitized against the supplied intake before they are stored.
The user sees a lightweight confirmation of the summary, focus targets, recurring moments, and alternatives before finishing onboarding.

The UI explains that records stay on-device and that relevant text is sent to Gemini only when live AI is used.

### 2. Home

Home shows:

- selected target app;
- today’s local usage minutes;
- the user’s own threshold;
- monitoring state;
- required Usage Access / overlay permission repair when needed;
- optional Android 13+ notification permission so background monitoring is visible in the notification area;
- threshold editing;
- daily-report entry with a visible loading state;
- a visible local-data section with destructive confirmation;
- discreet developer/demo controls for the hackathon build.

The user can start/stop monitoring and open the selected target app. Notification permission is requested contextually when monitoring is started if it has not already been granted, but denial does not block the core tracking flow.

### 3. Intervention

The intervention exists in two presentations:

- Compose screen for direct testing;
- real `TYPE_APPLICATION_OVERLAY` above the selected target app.

Initial question:

> Şu an seni burada tutan ne?

Input options:

- three personalized quick states selected locally from the stored profile;
- custom text;
- voice-to-text through Android speech recognition.

Quick states do not require a network request to appear.

Before any Gemini call, a local crisis-language gate checks external text. Crisis-signalling input never enters the normal AI card/repair path.

For normal input, the app locally compiles:

- resolved need;
- energy expectation;
- interaction objective;
- allowed strategies;
- maximum duration;
- allowed user-supplied personalization anchors;
- forbidden recommendation patterns.

Gemini returns a structured internal card containing need, strategy, question, alternative, duration, and personalization anchor. Application-side validation checks policy fit, grounding, actionability, duration, tone, and safety. One bounded repair attempt is allowed; otherwise the deterministic local gateway is used.

Visible output contains:

- one short supporting reflection;
- one short tentative/open question;
- one titled, duration-bounded concrete micro-alternative that can begin now.

Final decisions:

- **Bunu deneyeceğim** — save a stopped/try-alternative decision and leave the target-app moment;
- **Yine de devam et** — save an intentional continue decision and dismiss the overlay.

After a successful overlay display, the monitor uses a 15-minute cooldown.

#### When an intervention is offered

The user's own threshold remains the primary trigger and behaves exactly as before. In addition, the
monitor may offer a pause during a difficult moment before the threshold is reached, because total
minutes describe an amount rather than a situation: an hour of deliberate viewing is not the same as
twelve minutes of opening and closing the same app.

| Trigger | Condition |
|---|---|
| `threshold` | Local usage reached the user-defined limit |
| `immediate_reopen` | The target app was reopened within 30 seconds of being left |
| `rapid_reopen_loop` | Three or more opens of the target app within ten minutes |
| `session_drift` | The current session is at least three times the user's own median session |

Pattern triggers are deliberately hard to fire, because an interruption at the wrong moment costs
more trust than one that never happens:

- nothing fires below ten minutes of use that day;
- they have their own 45-minute cooldown, separate from the threshold cooldown;
- at most two pattern interventions per local day;
- the target app must be in the foreground;
- the session-drift baseline is ignored until at least five completed sessions exist.

A pattern trigger never changes the threshold, and the user's threshold is never inferred from
behaviour.

#### Reading the situation before asking

Typing at a difficult moment is the highest-friction thing the product can ask for. When behaviour
is unambiguous enough, Mola offers its reading of the situation and asks for one tap instead:

> Bu, "Biraz yoruldum" gibi görünüyor. Öyle mi?

Rules:

- the guess is produced by transparent local rules, not a model, and every rule carries the reason
  it fired;
- at least two independent supporting signals are required, otherwise the open question is shown;
- **Hayır, başka bir şey** returns to the normal quick-state list, and the rejection is recorded;
- the app measures its own hit rate per state from those answers and stops offering a guess it keeps
  getting wrong;
- signals are read on the device, interpreted on the device, and never sent anywhere;
- no additional Android permission is used: the signals come from the usage-access data the user
  already granted, plus the clock and charging state.

The guess is a situation offered as a question. It is never a statement about the person, and never
applied silently.

### 4. Daily report

The daily report uses only records from the current local date.

If there are fewer than seven records:

- show an insufficient-data state;
- do not call the report model.

At seven or more records:

1. the app computes numeric facts locally;
2. the app builds evidence aggregates/candidate patterns locally;
3. Gemini may reference only supplied evidence;
4. the report validator rejects unsupported/overconfident patterns.

Visible report content:

- local usage/threshold counts;
- intervention/continue/stop counts;
- one tentative observation phrased as a question;
- exactly one two-to-five-minute micro-experiment for tomorrow.

## Runtime flow

```text
onboarding
  -> local profile + user-defined target/threshold saved
  -> user starts monitoring
  -> optional notification permission request if needed
  -> foreground service polls every 60 seconds
  -> target app is foreground
  -> screen is active/unlocked
  -> overlay permission available
  -> local usage >= user threshold, or a behavioural pattern trigger fires
  -> matching cooldown expired
  -> local signals interpreted into an optional one-tap guess
  -> real overlay appears
  -> confirmed guess / quick state / text / voice context
  -> local crisis gate
  -> local intervention policy compiler
  -> Gemini structured card OR deterministic fallback
  -> semantic validation / at most one repair
  -> user intentionally continues or tries the alternative
  -> record saved locally
  -> eligible daily report uses current-date local evidence
```

The Compose intervention and report screens also route Android system Back to Home instead of unexpectedly exiting the activity.

## AI task split

| Task | Validated demo model | What the model is allowed to do |
|---|---|---|
| Profile structuring | `gemini-2.5-flash-lite` | Structure supplied onboarding evidence into constrained profile fields |
| Intervention card | `gemini-2.5-flash-lite` | Write one short question + one policy-compatible micro-alternative |
| Daily reflection | `gemini-3.6-flash` | Write one cautious evidence-backed question + one small next-day experiment |

Models remain configurable through ignored `local.properties`.

## AI language and behavior rules

The AI must not:

- set, recommend, judge, or change the threshold;
- call the user’s usage “too much,” “excessive,” “aşırı,” or make a moral judgment from the number;
- diagnose addiction, anxiety, depression, or another condition;
- label the person based on a context such as procrastination;
- accuse, shame, moralize, or present continuation as failure;
- claim causality from limited observations;
- pretend certainty about motives;
- invent a hobby, goal, task detail, media title, episode, artist, product, notification, or current event.

The AI may use only user-supplied profile/context anchors and locally compiled factual/evidence inputs.

## Data and privacy

Stored locally:

- user profile;
- target app/package and threshold;
- personalization profile;
- intervention records;
- generated visible question/alternative text;
- monitoring/cooldown preferences.

The profile/records file and monitoring preferences are excluded from Android cloud backup and device transfer.

The app provides a visible one-action data deletion flow with confirmation; deletion stops monitoring, clears cooldown state, and deletes local repository data.

The Gemini API key is never committed, but the hackathon-only mobile-direct architecture embeds the local key in the APK. Production requires a server-side/short-lived credential architecture.

## Safety and ethics

| Risk | Mitigation |
|---|---|
| Judgment/shame | prompt constraints, local policy, banned-language/display validator, deterministic fallback |
| Medical framing | no diagnosis/person labels; tentative questions only |
| Overclaiming | local evidence aggregation; no report below seven records; report semantic validation |
| Crisis language | local crisis gate; no normal Gemini/repair request |
| Hallucinated personalization | profile grounding sanitizer + allowed anchor checks |
| Effort mismatch | state/energy-specific local policy and allowed strategy set |
| Provider/network failure | bounded request/repair and deterministic local fallback |
| Privacy | app-private storage, backup exclusions, visible delete action, privacy-safe diagnostics |
| Monitoring transparency | foreground service, explicit Mola notification icon, contextual optional notification permission |
| Secret exposure | ignored local key; direct mobile integration explicitly hackathon-only |

## Current baseline and optional features

The validated baseline is documented in `docs/FINALIZATION.md`.

Features should be added only through small branches from `main`, with the relevant affected path retested before the pull request merges.
