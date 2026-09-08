# Mola

**A short, self-authored pause — right at the limit you set yourself.**

Mola is an Android digital-wellbeing app. You pick one app, you set your own daily minute
limit, and when you cross it Mola opens a brief pause over that app: one question, one
reflection, and one realistic small alternative. Then you decide — keep going, or stop.

Mola does not diagnose addiction, does not decide what "too much" means for you, and never
blocks anything. It opens a moment of thought and hands the decision straight back to you.

User-facing copy is Turkish. Code, comments and documentation are English.

> **Status:** working app, validated on physical hardware, preparing for a Play Store
> release. Not yet published.

---

## Why it might be different from other screen-time apps

Most screen-time tools either report numbers at you or lock you out. Mola sits in the
narrow gap between the two: it interrupts *once*, at a threshold **you** chose, and it
tries to say something specific enough to be worth reading.

- **You set the number.** The app never suggests, nudges or adjusts your limit.
- **It reads the shape of use, not only the amount.** Rapid re-opens, long unbroken
  sessions and late-night stretches are treated differently from the same total spread
  across a day.
- **It asks what's going on first.** Three personalized quick states, or your own words by
  keyboard or voice.
- **It stays tentative.** Generated language is checked against a safety validator before
  it is ever shown; nothing is allowed to shame, diagnose or command.
- **Crisis language never leaves the device.** A local filter short-circuits before any
  network request is even considered.
- **No account, no server, no analytics.** Your profile and your records live in this app's
  private storage and nowhere else.

---

## How the AI works — and what it costs you

Mola has **two engines behind one interface** (`AiGateway`), and the important one needs no
network at all.

### 1. On-device engine — the default

`LocalAiGateway` is the shipped default and the product's floor. It composes profiles,
intervention cards and daily reflections from your own onboarding narrative using local
inference over need signals, a state taxonomy and grounded templates. It needs **no
internet, no account and no API key**, works in airplane mode, and nothing you type ever
leaves the phone.

Install Mola and it works, completely, with this engine. **Cost to you: nothing.**

### 2. Optional Gemini engine — your key, your quota

If you want more fluent, more specifically-worded cards, you can paste **your own** Google
AI Studio key into Mola's settings (*Yazı motoru → Kendi anahtarını ekle*). A free key from
[aistudio.google.com/apikey](https://aistudio.google.com/apikey) is generous enough for one
person's use.

What that changes:

| | On-device engine | Your own key |
|---|---|---|
| Cost | none | your own free-tier quota |
| Network | never used | onboarding text, cards and reflections go to Google |
| Works offline | yes | falls back to the on-device engine |
| Crisis text sent | never | never — filtered locally first |
| Numbers in your daily report | computed locally | computed locally |

**Mola ships with no API key of its own.** The build contains no credential to extract,
there is no proxy server, and no request is ever billed to the developer. Your key is
stored in this app's private preferences and is excluded from cloud backup and device
transfer. Delete it in one tap and the app returns to the on-device engine.

### Guardrails that apply to both engines

Even with a key configured, generated output is not trusted blindly:

1. Crisis-language filter runs **before** any request.
2. Output must satisfy a JSON schema, then a semantic validator (grounding, tone,
   autonomy-preserving language).
3. One bounded repair attempt on rejection.
4. Any remaining failure, block, timeout or rejection falls through to the on-device
   engine — so a bad response degrades quality, never the experience.
5. Daily-report numbers are always computed on-device; the model only writes prose around
   evidence it is handed.

---

## Architecture

```text
Compose UI  +  TYPE_APPLICATION_OVERLAY
        |
        v
MolaRepository ................ device-local JSON, no network
        |
        +-- UsageStatsReader ....... daily minutes, foreground app
        +-- UsageMonitorService .... foreground service, 60s polling
        +-- InterventionTriggerPolicy / NeedSignals / CooldownPolicy
        |
        +-- AiGatewayFactory
                |
                +-- LocalAiGateway ....... default; on-device, offline
                |
                +-- GeminiAiGateway ...... only when the user supplied a key
                        +-- local context / strategy compiler
                        +-- structured prompt + JSON schema
                        +-- semantic + safety validation
                        +-- one repair attempt
                        +-- falls back to LocalAiGateway
```

| Path | Responsibility |
|---|---|
| `app/src/main/java/com/yberkayinci/mola/ui/` | Compose screens |
| `app/src/main/java/com/yberkayinci/mola/overlay/` | The real system overlay |
| `app/src/main/java/com/yberkayinci/mola/monitor/` | Foreground monitoring service |
| `app/src/main/java/com/yberkayinci/mola/usage/` | UsageStats reads, triggers, cooldown |
| `app/src/main/java/com/yberkayinci/mola/ai/` | Both engines, prompts, validators, safety |
| `app/src/main/java/com/yberkayinci/mola/data/` | Models and local persistence |

---

## Permissions, and why each one is needed

Mola asks for sensitive permissions because a pause that arrives *over the app you are
actually using* cannot be built without them.

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access) | Read today's minutes for the **one** app you selected |
| `SYSTEM_ALERT_WINDOW` | Draw the pause above that app |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Poll usage every 60s while you have monitoring on |
| `POST_NOTIFICATIONS` | Show the ongoing-monitoring notification (optional) |
| `INTERNET` | Only used if you entered your own API key |

Mola reads usage for the single app you chose — not your whole device history — and holds
everything on-device. See [`PRIVACY.md`](PRIVACY.md).

---

## Build

Requirements:

- JDK 17
- Android SDK Platform 37
- Android Gradle Plugin 9.3.0 / Gradle 9.5.0 / Kotlin 2.3.21
- `compileSdk 37`, `targetSdk 36`, `minSdk 26`

```bash
cp local.properties.example local.properties
./gradlew test
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

No secrets are needed to build. There is no API key in the build at all — the optional key
is entered at runtime, by the user, in the app.

### Release build

`assembleRelease` is minified (R8 + resource shrinking) and succeeds unsigned, so CI can
verify the release path without any credentials. To produce a signed build, copy
`keystore.properties.example` to `keystore.properties` (git-ignored) or set
`MOLA_KEYSTORE_FILE`, `MOLA_KEYSTORE_PASSWORD`, `MOLA_KEY_ALIAS` and `MOLA_KEY_PASSWORD`.

```bash
./gradlew bundleRelease
```

A physical Android phone is required to validate Usage Access, foreground detection,
overlays, voice recognition and service behaviour. Emulators do not exercise these
faithfully.

---

## Diagnostics

```bash
adb logcat -s MolaUsageMonitor:D "*:S"
```

```bash
adb logcat -s MolaAi:D "*:S"
```

Debug logs record task / model / source / outcome / latency only. They deliberately never
contain your biography, your intervention text, crisis text, or API keys.

---

## Testing

```bash
./gradlew test
```

The suite covers the parts where being wrong would matter: the crisis filter, the safety
and semantic validators, profile grounding, need inference and its calibration, trigger and
cooldown policy, session analysis, the on-device engine's outputs, and the persistence
schema contract.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) | Product boundary and core flow |
| [`docs/PROMPT_DESIGN.md`](docs/PROMPT_DESIGN.md) | Prompt design and structured-output rationale |
| [`docs/AI_EVALUATION.md`](docs/AI_EVALUATION.md) | Quality/safety rubric and scenario suite |
| [`docs/AI_DEVICE_QA.md`](docs/AI_DEVICE_QA.md) | Live/offline device test procedure |
| [`docs/DATA_SCHEMA.md`](docs/DATA_SCHEMA.md) | Local persistence contract |
| [`docs/VALIDATION.md`](docs/VALIDATION.md) | Android/core validation record |
| [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) | Store submission checklist and Data safety answers |
| [`PRIVACY.md`](PRIVACY.md) | Privacy policy |

`docs/README.md` maps current source-of-truth documents against retained historical
material.

---

## Contributing

Issues and pull requests are welcome. Two things worth knowing before you open one:

- **The product boundary is not a style preference.** The user sets the threshold; the app
  does not diagnose, shame, or block. A change that crosses those lines will be declined
  however well it is implemented.
- **Generated language is validated, not trusted.** New AI output paths need matching
  validator coverage and a deterministic fallback.

Run `./gradlew test` before pushing.

## License

[MIT](LICENSE)
