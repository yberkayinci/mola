# Frozen Data Contract — Schema v4

This document defines the persisted device-local contract and the current structured AI contracts. Existing schema-v1/v2/v3 files remain readable: newer fields have defaults and old records are never deleted or rewritten.

## Persisted device state

```json
{
  "profil": {
    "sema_surum": 4,
    "isim": "Ayşe",
    "bolum": "İstatistik",
    "hobiler": ["gitar", "koşu"],
    "gelisim_alani": "daha düzenli çalışmak",
    "neden": "gece daha rahat uyumak istiyorum",
    "hedef_app": "Instagram",
    "hedef_paket": "com.instagram.android",
    "limit_dk": 60,
    "biyografi": "Derslerden sonra yoruluyorum ve çalışmaya başlamak yerine Instagram'da oyalanıyorum. Gitarı ve koşmayı seviyorum.",
    "kisisellestirme": {
      "hedefler": ["daha düzenli çalışmak", "gece daha rahat uyumak"],
      "tekrarlayan_baglamlar": ["yorgunluk", "erteleme"],
      "tercih_edilen_aktiviteler": ["gitar", "koşu"],
      "dusuk_enerji_aktiviteleri": [
        "bir şarkı boyunca telefonu bırakmak",
        "bir bardak su içip ekrandan uzaklaşmak"
      ],
      "ton": "supportive_direct",
      "profil_ozeti": "Özellikle bir işe başlamadan önce ve enerjin düştüğünde Instagram'a kaydığını anlattın.",
      "odak_hedefleri": ["ders çalışmak"],
      "hizli_durumlar": [
        {
          "id": "tired",
          "etiket": "Biraz yoruldum",
          "emoji": "😴",
          "kategori": "low_energy"
        },
        {
          "id": "procrastinating",
          "etiket": "Bir şeyi erteliyorum",
          "emoji": "🫠",
          "kategori": "avoidance"
        },
        {
          "id": "relaxing",
          "etiket": "Sadece kafa dağıtıyorum",
          "emoji": "😌",
          "kategori": "intentional_rest"
        }
      ]
    }
  },
  "kayitlar": [
    {
      "zaman_ms": 1788034200000,
      "saat": "23:10",
      "kullanim_dk": 78,
      "metin": "Bir şeyi erteliyorum",
      "durum_id": "procrastinating",
      "durum_etiket": "Bir şeyi erteliyorum",
      "girdi_yontemi": "quick_reply",
      "ai_soru": "Ertelediğin şeyin yalnızca ilk iki dakikasını yapmak daha ulaşılabilir olabilir mi?",
      "ai_alternatif": "Ödevin için yalnızca ilk iki dakikalık adımı başlatabilirsin.",
      "ai_yansima": "Başlamak şu anda işin kendisinden daha zor geliyor olabilir.",
      "ai_aktivite_basligi": "İlk 3 dakika",
      "ai_sure_dk": 3,
      "ai_strateji": "micro_start",
      "tetikleyici": "rapid_reopen_loop",
      "tahmin_durum": "habit",
      "tahmin_kabul": false,
      "secim": "vazgectim"
    }
  ]
}
```

The persisted intervention record stores visible AI fields plus the selected strategy metadata, not the compiled local policy.

## Behavioural signals

`tetikleyici` records why the moment was interrupted. `tahmin_durum` and `tahmin_kabul` record the
guess the app offered about the current state and whether the user accepted it.

`tahmin_kabul` is **absent** when no guess was offered. It is never written as `false` for a moment
the user was simply not asked about: silence is not a rejection, and the calibration that decides
whether to keep guessing counts only explicit answers.

The signals behind a guess — session lengths, open counts, the personal median session, the gap
since the last visit, the app used immediately before, the current unbroken run of device use, the
hour, and charging state — are derived on the device from the usage-access data the user already
granted, interpreted on the device, and never persisted or transmitted. Only the resulting state ID
is stored.

## Stable persisted enums

### `girdi_yontemi`

- `quick_reply`
- `text`
- `voice`

### `secim`

- `yine_de_gir`
- `vazgectim`

### `tetikleyici`

- `threshold` — the user reached the limit they set
- `immediate_reopen` — reopened within 30 seconds of leaving
- `rapid_reopen_loop` — three or more opens within ten minutes
- `session_drift` — session at least three times the user's own median

An empty or unrecognised value means no trigger was recorded, and is never guessed at.

### `tahmin_durum`

A canonical quick-state ID, or empty when no guess was offered. Inference currently produces only
`late_night`, `habit`, `tired` and `bored`; every other canonical state is reached by the user
choosing it.

### `ton`

- `supportive_direct`
- `gentle`
- `practical`

Quick-state IDs are stable lowercase ASCII identifiers. Labels and emoji are display values and may change without breaking historical grouping.

## Profile AI input

Representative payload:

```json
{
  "name": "Ayşe",
  "department": "İstatistik",
  "biography": "Derslerden sonra yoruluyorum...",
  "explicit_hobbies": ["gitar", "koşu"],
  "explicit_improvement_area": "daha düzenli çalışmak",
  "explicit_reason": "gece daha rahat uyumak istiyorum"
}
```

## Profile AI output

```json
{
  "profile_summary": "Özellikle bir işe başlamadan önce ve enerjin düştüğünde Instagram'a kaydığını anlattın.",
  "focus_targets": ["ders çalışmak"],
  "goals": ["daha düzenli çalışmak", "gece daha rahat uyumak"],
  "recurring_contexts": ["yorgunluk", "erteleme"],
  "preferred_activities": ["gitar", "koşu"],
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

Generated profile content is passed through application-side grounding/sanitization. Missing safe defaults may be completed deterministically; at most six quick states are stored. Quick-state IDs come only from the canonical taxonomy; labels and emoji may be personalized.

### Canonical quick-state IDs

```text
tired
procrastinating
relaxing
bored
habit
waiting
low_motivation
overwhelmed
late_night
other
```

## Intervention local policy contract

Before Gemini is called, the app resolves the current context locally. The dynamic payload includes an authoritative `compiled_policy` with fields equivalent to:

```json
{
  "resolved_state_id": "procrastinating",
  "need": "activation",
  "energy": "normal",
  "objective": "micro_start",
  "allowed_strategies": ["micro_start"],
  "max_duration_minutes": 5,
  "anchors": {
    "goals": ["daha düzenli çalışmak"],
    "activities": ["gitar", "koşu"],
    "low_energy_activities": ["bir şarkı boyunca telefonu bırakmak"]
  },
  "forbidden_patterns": [],
  "evidence_summary": "..."
}
```

Exact enum availability is defined in the AI implementation; this JSON illustrates the contract shape rather than a persisted object.

## Intervention AI structured output

The current internal model response is:

```json
{
  "need": "activation",
  "strategy": "micro_start",
  "reflection": "Başlama anı şu anda işin kendisinden daha zor geliyor olabilir.",
  "question": "Ertelediğin şeyin yalnızca ilk iki dakikasını yapmak daha ulaşılabilir olabilir mi?",
  "activity_title": "İlk 3 dakika",
  "alternative": "İlk adımı iki dakika boyunca açıp yapmayı deneyebilirsin.",
  "duration_minutes": 2,
  "personalization_anchor": "daha düzenli çalışmak"
}
```

Application-side semantic validation checks the returned need/strategy/duration/anchor against the local policy, plus field length, actionability, style, safety, and near-duplicate history. One bounded repair attempt is allowed. The visible `AiCard` contains the reflection, question, activity title, alternative, and duration; internal strategy metadata is retained for intervention history.

### Recent intervention context

Only the latest six intervention records are summarized for card generation. The model receives state, a normalized choice, and a bounded previous alternative, not the full raw history or old custom text. Past choices are weak interaction signals only and never establish that an intervention worked or failed.

The overlay opens with three stored quick states before any network request. Crisis-signalling external text is blocked locally and never enters the normal Gemini/repair path.

## Daily report local evidence

The application computes all counts and candidate patterns locally from current-date records. The model does not discover or recalculate numeric facts.

If fewer than seven current-date records exist, the model is not called.

## Daily report AI structured output

```json
{
  "evidence_state_id": "procrastinating",
  "observation_question": "“Bir şeyi erteliyorum” dediğin anlarla devam kararların arasında bir örüntü olabilir mi?",
  "micro_step": "Yarın ilk erteleme anında yalnızca iki dakikalık bir başlangıç yapmayı dene."
}
```

`evidence_state_id` must be a locally supplied supported candidate or empty when evidence is mixed/weak. The application validates the reflection before display. The persisted repository currently stores intervention records, not generated daily reports.

## Compatibility rules

- Do not rename/remove persisted v2 fields during the hackathon.
- New stored fields require backward-compatible defaults and a schema/documentation update.
- Internal Gemini structured fields may evolve without changing persisted schema only when the visible/persisted contract remains compatible.
- Numeric usage, threshold, counts, dates, and evidence aggregates remain application-computed.
- The model never sets a threshold, diagnoses the user, or states unsupported causation as fact.
