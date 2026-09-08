---
agent: 'agent'
description: 'Implement one optional Mola feature from the validated integration baseline'
---

Implement the requested Mola feature or fix without disturbing the validated baseline.

Before editing:

1. Read `COPILOT_PROMPT.md` completely; it is the canonical executor prompt.
2. Read `AGENTS.md`, `.github/copilot-instructions.md`, `docs/PRODUCT_SPEC.md`, `docs/DATA_SCHEMA.md`, and `docs/FINALIZATION.md`.
3. If the request changes AI behavior, also read `docs/AI_EVALUATION.md` and `docs/PROMPT_DESIGN.md`.
4. Inspect the actual files involved before proposing or making edits.
5. Confirm the work is based on the latest `main`.

Keep the change narrow. Preserve the user-owned threshold, local crisis gate, device-local records, structured/validated AI behavior, deterministic fallback, and existing Android monitoring/overlay behavior unless the requested feature explicitly changes one of those areas.

Run `./gradlew test` and `./gradlew assembleDebug` before finishing. Perform the smallest emulator/physical-device check that actually exercises the changed behavior. Do not repeat unrelated edge-case matrices for a small feature.

Do not merge to `main` yourself. Final output should list the branch, files changed, behavior implemented, validation performed/results, relevant device check, remaining limitation, and whether fallback/privacy/safety behavior remains intact.
