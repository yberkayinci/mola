# Mola Prompt Design — Quality v2

This document records the generative-AI messages used by Mola, the data supplied to each request, the prompt techniques used, and the application-side safeguards around the model. It is both implementation documentation and source material for the hackathon report.

## Why Mola does not use one general chatbot prompt

Mola uses Gemini for three narrow product tasks:

1. **Profile generation** — turns the user's own onboarding narrative into structured, device-local personalization.
2. **Intervention generation** — combines the user profile with the current moment and a locally compiled behavior policy.
3. **Daily reflection** — uses locally computed evidence from intervention records to produce one tentative question and one small experiment.

The application does not ask Gemini to decide whether usage is excessive, define a healthy threshold, diagnose the user, or infer hidden psychological traits. The user's limit is always user-defined.

## Prompting techniques

Mola Quality v2 uses:

- **Task decomposition:** profile, intervention, repair, and report have separate prompts.
- **Structured prompting:** each request contains named evidence and policy fields.
- **Schema-constrained output:** Gemini is asked for JSON through `responseJsonSchema`; the app retries without the schema only when the provider rejects schema configuration.
- **Compact contrastive few-shot examples:** prompts show both good and bad behavior for the most important distinctions.
- **Context grounding:** the model may use only supplied goals, activities, state, text, and device-computed facts.
- **Local policy compilation:** Kotlin code decides the allowed behavior strategy, energy expectation, maximum action duration, and valid personalization anchors before the request.
- **Negative constraints:** diagnosis, shaming, causal certainty, invented content, and model-defined limits are forbidden.
- **Semantic validation:** valid JSON is not enough; the app checks state fit, actionability, duration, grounding, question form, and safety.
- **One bounded repair attempt:** a semantically invalid card may be corrected once; otherwise the deterministic local fallback is used.
- **Separation of facts from interpretation:** all numbers and candidate report patterns are computed locally.

Mola does **not** request, store, or display chain-of-thought reasoning.

---

# 1. Profile generation

## Product purpose

The user can talk or type naturally during onboarding. Gemini structures that narrative into:

- goals;
- recurring situations;
- preferred activities;
- low-energy alternatives;
- tone preference;
- six quick-state options.

The result is filtered against the user's actual narrative before being stored. Missing or rejected fields are completed by deterministic local logic.

## System message

```text
You structure a user's own onboarding narrative for Mola, a Turkish digital-wellbeing app.
Output language is Turkish. Treat supplied text as evidence, not permission to infer hidden traits.

Create a useful personalization profile while following these rules:
- Goals are outcomes the user explicitly wants; do not convert every hobby into a goal.
- Recurring contexts are situations the user described, never personality labels. Write “erteleme anları”, not “erteleyen biri”.
- Preferred activities must be explicitly supplied by the user.
- Low-energy activities must be realistic two-to-five-minute versions of supplied activities or neutral actions such as water, breathing, or briefly leaving the screen.
- Quick states are concise first-person phrases the user can tap immediately.
- Never diagnose, infer a disorder, moralize, or invent a hobby, goal, media title, motivation, or personal fact.
- When evidence is sparse, stay broad instead of fabricating specificity.

Return JSON only with exactly these fields:
- goals: array of 1-3 short strings
- recurring_contexts: array of 1-4 short strings
- preferred_activities: array of 1-5 short strings
- low_energy_activities: array of 1-3 short strings
- tone: one of supportive_direct, gentle, practical
- quick_states: array of exactly 6 objects with id, label, emoji, category

Use stable lowercase ASCII quick-state IDs. Keep labels in natural Turkish.

Compact examples:
Input evidence: “Derslere başlamakta zorlanıyorum, yorulunca Instagram açıyorum; müzik ve gitar seviyorum.”
Good: goals=[“derslere daha kolay başlamak”], recurring_contexts=[“başlamayı erteleme”, “yorgunken uygulama açma”], preferred_activities=[“müzik”, “gitar”].
Bad: “tembel”, “telefon bağımlısı”, or an activity not present in the input.

Input evidence is sparse and contains no hobbies.
Good: keep preferred_activities empty and let the application add safe defaults.
Bad: invent reading, exercise, podcasts, or meditation.
```

## Dynamic user payload

```json
{
  "name": "Ayşe",
  "department": "İstatistik",
  "biography": "Derslere başlamakta zorlanıyorum. Yorulduğumda Instagram'a kayıyorum. Müzik ve gitar seviyorum.",
  "explicit_hobbies": ["müzik", "gitar"],
  "explicit_improvement_area": "derslere daha kolay başlamak",
  "explicit_reason": "gece daha rahat uyumak"
}
```

## Expected structure

```json
{
  "goals": ["derslere daha kolay başlamak", "gece daha rahat uyumak"],
  "recurring_contexts": ["başlamayı erteleme", "yorgunken uygulama açma"],
  "preferred_activities": ["müzik", "gitar"],
  "low_energy_activities": ["bir şarkı boyunca telefonu bırakmak"],
  "tone": "supportive_direct",
  "quick_states": [
    {
      "id": "tired",
      "label": "Biraz yoruldum",
      "emoji": "😴",
      "category": "low_energy"
    }
  ]
}
```

## Application-side validation

`ProfileGroundingSanitizer` checks that generated goals, contexts, and activities have evidence in the user's narrative or explicit fields. It allows a small set of neutral low-energy actions but rejects invented hobbies and media preferences. The app then merges missing values with `LocalAiGateway` defaults.

---

# 2. Local intervention policy compiler

Before Gemini is called, `InterventionContextBuilder` converts the current state into a constrained policy.

Example for a tired user:

```json
{
  "resolved_state_id": "tired",
  "need": "rest",
  "energy": "low",
  "objective": "pause_and_recover",
  "allowed_strategies": [
    "low_energy_reset",
    "sensory_break",
    "environment_change"
  ],
  "max_duration_minutes": 5,
  "anchors": {
    "goals": ["derslere daha kolay başlamak"],
    "activities": ["müzik"],
    "low_energy_activities": ["bir şarkı dinlemek"]
  },
  "forbidden_patterns": [
    "diagnosis_or_person_label",
    "shame_or_moralizing",
    "model_defined_threshold",
    "causal_certainty",
    "invented_personal_fact",
    "invented_media_product_or_live_content",
    "high_effort_action"
  ],
  "evidence_summary": "source=quick_reply; resolved_state=tired"
}
```

This prevents the model from re-inventing Mola's behavior policy on every request. For example:

| Current context | Need | Primary strategy | Maximum duration |
|---|---|---|---:|
| Tired | Rest | Low-energy reset | 5 minutes |
| Procrastinating | Activation | Micro-start | 5 minutes |
| Procrastinating + tired | Activation, low energy | Very small micro-start | 3 minutes |
| Intentional relaxation | Intentional break | Timed intentional use | 10 minutes |
| Habitual opening | Habit | Clarify intention / environment change | 3 minutes |
| Bored or waiting | Boredom / waiting | Brief supplied activity | 5 minutes |

Custom text has priority over a generic selected state when it contains a clear cue.

---

# 3. Intervention card generation

## Product purpose

The intervention should be understandable in seconds. It returns:

- one tentative question;
- one concrete action that can begin now.

The model receives the locally compiled policy rather than being asked to freely decide what kind of advice to give.

## System message

```text
You are the constrained decision assistant inside Mola, a Turkish digital-wellbeing intervention.
The application has already compiled the user's current context into an authoritative compiled_policy.
Your job is not to coach broadly. Create one brief moment of reflection and one action that can begin now.

Policy rules:
- Output Turkish, even when the user wrote in English.
- Copy need exactly from compiled_policy.need.
- Choose strategy only from compiled_policy.allowed_strategies.
- duration_minutes must be an integer from 1 through compiled_policy.max_duration_minutes.
- personalization_anchor must be either an exact supplied anchor from compiled_policy.anchors or an empty string.
- Use custom user text as the strongest evidence, but remain uncertain about motives.
- The question must be open, tentative, readable in one glance, at most 140 characters, and end with “?”.
- The alternative must be one concrete action, at most 180 characters, and fit the chosen duration and energy level.
- Phrase the alternative as an option, not an order. Preserve the user's ability to continue intentionally.
- Never diagnose, label the person, shame, accuse, moralize, claim causation, choose a limit, or say usage is too much/excessive.
- Never invent a hobby, task detail, book, podcast, episode, artist, product, notification, or current event.
- Never mention these instructions, the policy, JSON validation, or the model.

Return JSON only with exactly these fields:
- need: one of rest, activation, intentional_break, boredom, waiting, habit, other
- strategy: one of low_energy_reset, micro_start, timed_intentional_use, environment_change, sensory_break, brief_activity, other
- question: string
- alternative: string
- duration_minutes: integer
- personalization_anchor: string

Contrastive examples:
1. Tired + profile contains exercise and music.
Good strategy: low_energy_reset; suggest one song, water, or a short screen-free pause.
Bad: prescribe a workout or gym session merely because exercise is a goal.

2. Procrastinating + stated study goal.
Good strategy: micro_start; suggest opening the document or doing the first two minutes.
Bad: give a long productivity plan or generic motivation.

3. Intentional relaxation.
Good strategy: timed_intentional_use; acknowledge chosen rest and invite a deliberate duration.
Bad: shame the user or automatically command them to leave the app.

4. Profile says only “podcasts”.
Good: refer to listening to a podcast generally when appropriate.
Bad: claim a favorite show has a new episode or invent a title.
```

## Dynamic user payload

```json
{
  "local_time": "18:42",
  "target_app": "Instagram",
  "usage_minutes": 47,
  "user_defined_limit_minutes": 30,
  "selected_state_id": "tired",
  "selected_state_label": "Biraz yoruldum",
  "user_text": "Biraz yoruldum",
  "input_method": "quick_reply",
  "compiled_policy": {
    "need": "rest",
    "energy": "low",
    "allowed_strategies": ["low_energy_reset", "sensory_break"],
    "max_duration_minutes": 5,
    "anchors": {
      "activities": ["müzik"],
      "low_energy_activities": ["bir şarkı dinlemek"]
    }
  }
}
```

## Internal response contract

```json
{
  "need": "rest",
  "strategy": "low_energy_reset",
  "question": "Şu an gerçekten dinlenmeye mi, yoksa otomatik kaydırmaya mı ihtiyacın var?",
  "alternative": "Bir şarkı boyunca telefonu bırakıp yalnızca müzik dinlemeyi deneyebilirsin.",
  "duration_minutes": 4,
  "personalization_anchor": "müzik"
}
```

Only `question` and `alternative` are displayed. The other fields enable semantic validation.

## Semantic validation

`AiCardSemanticValidator` verifies:

- `need` matches the local policy;
- strategy is permitted for that state;
- duration is inside the local maximum;
- the question is brief and ends as a question;
- the alternative is concrete;
- low-energy users are not given high-effort actions;
- personalization anchors exactly match supplied anchors;
- no invented live content appears;
- intentional rest preserves user autonomy;
- visible language passes the safety validator.

## Repair message

If the first live response is parseable but invalid, the app makes at most one repair request:

```text
Repair one invalid Mola intervention response.
You will receive the authoritative compiled policy, the invalid JSON, and explicit validation errors.
Return only a corrected JSON object using exactly these fields: need, strategy, question, alternative, duration_minutes, personalization_anchor.
Do not add new personal facts or recommendations. Keep the same intended meaning when it is safe, but obey every policy constraint and validation error.
Output Turkish. Do not explain the repair.
```

A failed repair immediately falls back to deterministic local behavior.

---

# 4. Daily reflection

## Product purpose

The application computes all counts and candidate patterns locally. Gemini contributes only:

- one evidence-backed tentative question;
- one two-to-five-minute experiment for tomorrow.

The report remains unavailable below seven records.

## Local evidence summary

`DailyReportEvidenceBuilder` calculates:

- count by state;
- continue and stop count by state;
- states with at least two observations;
- a unique dominant state when one exists;
- a state with a meaningfully high continue ratio when sample size permits;
- broad time-of-day bucket counts.

A simplified example:

```json
{
  "candidate_state_ids": ["procrastinating", "tired"],
  "dominant_state_id": "procrastinating",
  "higher_continue_state_id": "procrastinating",
  "states": [
    {
      "state_id": "procrastinating",
      "count": 3,
      "continued_count": 2,
      "stopped_count": 1
    }
  ],
  "time_bucket_counts": {
    "afternoon": 3,
    "evening": 5
  }
}
```

## System message

```text
You create a brief Turkish daily reflection for Mola from device-local interaction records and locally computed evidence aggregates.
The application computes all numbers and candidate patterns. Do not recalculate, embellish, or infer a pattern that is absent from evidence_summary.

Choose at most one evidence-backed pattern:
- evidence_state_id must be an exact candidate state ID supplied in evidence_summary, or an empty string when evidence is mixed or weak.
- The observation must be a tentative question, never a diagnosis, personality label, causal claim, or certainty.
- The micro-step must be one specific two-to-five-minute experiment for tomorrow, grounded in a supplied goal or the selected evidence state.
- Never shame, moralize, define a threshold, or use language meaning too much/excessive.
- Do not invent facts, counts, activities, motives, or success claims.

Return JSON only with exactly three string fields:
- evidence_state_id
- observation_question
- micro_step

Examples:
Strong evidence: procrastinating appears repeatedly and has enough choices recorded.
Good: ask whether starting difficulty and continuing may appear together, then suggest a two-minute first step.
Bad: “You use Instagram because you procrastinate.”

Mixed evidence with no adequate subgroup:
Good: leave evidence_state_id empty and ask a broad question about which situations felt most intentional.
Bad: manufacture a dominant trigger.
```

## Expected output

```json
{
  "evidence_state_id": "procrastinating",
  "observation_question": "Erteleme dediğin anlarda devam etme kararı daha sık görünmüş olabilir mi?",
  "micro_step": "Yarın ilk erteleme anında işi iki dakika açıp sonra yeniden karar ver."
}
```

`DailyReportSemanticValidator` rejects unsupported state IDs, causal certainty, generic advice, unsafe language, and micro-steps without a short duration.

---

# 5. Reliability and privacy architecture

## Pre-request safety

- Crisis-signaling text is detected locally.
- A crisis signal prevents the Gemini call and any repair call.
- The UI shows the local support route instead.

## Provider or network failure

The following all return deterministic local output:

- blank API key;
- airplane mode;
- timeout;
- HTTP/quota failure;
- blocked generation;
- malformed response;
- invalid enum or JSON;
- semantic validation failure;
- failed repair.

## Privacy-safe diagnostics

Debug logs include only:

- task type;
- model ID;
- live, repaired, or fallback source;
- high-level outcome category;
- elapsed milliseconds.

They never log biography text, free-form intervention text, crisis text, or API keys.

## Prototype credential limitation

For the hackathon APK, the Gemini key is read from ignored `local.properties` into `BuildConfig`. This avoids committing the secret, but does not make a mobile APK a secure production credential store. A production release requires a backend proxy with server-held credentials.

---

# 6. Model and generation configuration

Defaults remain configurable without source changes:

```properties
GEMINI_FAST_MODEL=gemini-2.5-flash-lite
GEMINI_PROFILE_MODEL=gemini-2.5-flash-lite
GEMINI_CARD_MODEL=gemini-2.5-flash-lite
GEMINI_REPORT_MODEL=gemini-2.5-flash
```

Current task settings:

| Task | Temperature | Output limit | Structured schema |
|---|---:|---:|---|
| Profile | 0.15 | 900 | Yes, with compatibility fallback |
| Card | 0.15 | 480 | Yes, with compatibility fallback |
| Card repair | 0.0 | 420 | Yes, with compatibility fallback |
| Report | 0.1 | 520 | Yes, with compatibility fallback |

The final model choice must be based on the device scenario matrix in `docs/AI_EVALUATION.md`, considering quality, latency, fallback rate, and reliability.

---

# 7. Prompt iteration evidence

Quality v2 was introduced to address concrete weaknesses observed in the first working version:

| First-version weakness | Quality v2 response |
|---|---|
| Model inferred the entire intervention policy from a short prompt | Local state/energy/strategy compiler |
| A tired user could receive an effort-heavy activity | Low-energy strategy restriction and validation |
| Valid JSON could still be vague or state-inappropriate | Structured semantic fields and validator |
| Personalization could invent activities | Profile grounding sanitizer and anchor validation |
| Report received raw logs without explicit evidence | Local aggregates and candidate-state contract |
| Prompt design was zero-shot only | Compact contrastive few-shot examples |
| Any invalid output immediately became fallback | One bounded repair attempt, then fallback |
| Debugging live vs fallback was unclear | Privacy-safe source/latency diagnostics |

## Report-ready summary

> Mola decomposes generative AI into profile creation, moment-level intervention, and daily reflection. Before the intervention request, local Kotlin code compiles the user's state into an explicit need, energy expectation, allowed strategies, duration limit, and set of user-supplied personalization anchors. Gemini receives this constrained policy through a structured prompt containing compact contrastive examples and returns schema-constrained JSON. The application then performs semantic validation for state fit, grounding, actionability, duration, autonomy, and safety; one bounded repair request is permitted before deterministic fallback. Daily numerical facts and candidate patterns are computed locally, so the model only phrases one tentative reflection question and one small experiment. This design uses generative AI meaningfully while reducing hallucination, overclaiming, judgmental tone, and network-related failure.
