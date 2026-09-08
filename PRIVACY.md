# Mola — Privacy Policy

**Last updated:** 2026-09-08
**Applies to:** the Mola Android application (`com.yberkayinci.mola`)
**Contact:** berkayinci@4dimension.io

Mola is built so that there is very little to write in a policy like this. There is no
account, no Mola server, no analytics SDK, no advertising SDK, and no crash-reporting
service. By default nothing you enter or generate leaves your device.

---

## 1. What Mola stores, and where

All of the following is written to Mola's **private app storage on your device**. It is not
transmitted to the developer, and it is not accessible to other apps.

| Data | Purpose |
|---|---|
| Your onboarding narrative (name, what you told the app about yourself, goals, hobbies) | Personalizing the pause you are shown |
| The app you selected and the daily minute limit you set | Knowing when to open a pause |
| Intervention records — timestamp, minutes at the time, the state you picked, any text you wrote, what was shown, and your continue/stop choice | Your daily reflection and avoiding repetition |
| Monitoring state and cooldown timestamps | Not interrupting you twice in a row |
| Your own AI API key, if you chose to enter one | Calling Google's API as you |

**Deletion.** *Verilerin → Tüm verileri sil* erases the profile, all intervention records
and monitoring state from the device. Uninstalling the app removes everything as well.
Both are immediate and irreversible.

**Backup.** Your profile, your records, your monitoring state and your API key are all
explicitly excluded from Android cloud backup and device-to-device transfer.

---

## 2. Usage data Mola reads

With Usage Access permission (`PACKAGE_USAGE_STATS`), Mola reads:

- today's total foreground minutes for **the single app you selected**;
- which app is in the foreground right now, to know when to show the pause;
- recent session boundaries for that one app, to distinguish a long unbroken stretch from
  the same minutes spread across a day.

Mola does not build a history of your other apps, does not read message, browsing, contact
or location data, and does not read the content of any app. These reads happen on-device
and the results are never transmitted anywhere.

---

## 3. Network use

**By default, Mola makes no network requests at all.** The on-device engine generates every
profile, pause card and daily reflection locally, and the app is fully functional in
airplane mode.

Mola contains **no API key of its own** — there is no credential inside the app and no
developer-operated server or proxy.

### If you enter your own API key

Mola's settings let you add a personal Google AI Studio key. This is entirely optional. If
you add one, then when a pause is generated Mola sends to **Google's Generative Language
API**, under your own key and your own quota:

- the structured profile summary derived from your onboarding narrative;
- the state you selected and any text you wrote for that pause;
- locally computed numeric evidence for the daily reflection;
- a short summary of recent pauses, so the wording does not repeat.

Google's handling of that data is governed by Google's own terms for the API key you
created — not by this policy. Review the
[Gemini API Additional Terms of Service](https://ai.google.dev/gemini-api/terms) before
adding a key; note in particular that data sent on a **free** API tier may be used by
Google to improve its products, which is a reason to prefer the on-device engine if that
matters to you.

Removing the key in Mola's settings returns the app to fully offline operation.

**Text containing crisis or self-harm language is never sent anywhere.** A local filter
runs before any request is prepared and forces on-device handling.

---

## 4. What Mola never does

- No user account, sign-in, or identifier of any kind.
- No analytics, telemetry, advertising, or tracking SDKs.
- No selling or sharing of personal data with third parties.
- No transmission of your data to the developer, in any build.
- No diagnosis of addiction, mental-health conditions, or personality.

Debug logging (available only in debug builds via `adb`) records task, model, source,
outcome and latency. It deliberately excludes your narrative, your written text, crisis
text, and API keys.

---

## 5. Children

Mola is not directed at children under 13 and collects no data that would identify anyone.

---

## 6. Your rights

Because all data stays on your device and is tied to no identity, Mola holds nothing to
export or delete on your behalf. You have direct and complete control: delete your data
in-app at any time, or uninstall.

If you added your own API key, your rights regarding data processed under that key are
exercised with Google, through your own Google account.

---

## 7. Changes

Material changes to this policy will be published in this file in the project repository,
with the date above updated. The repository's git history is the change record.

---

## 8. Not a medical service

Mola is a digital-wellbeing tool, not a medical device or therapy, and it does not provide
diagnosis or treatment. If you are struggling, please contact a qualified professional or a
local crisis line.
