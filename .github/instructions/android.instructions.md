---
applyTo: "app/src/main/**/*.kt,app/src/main/AndroidManifest.xml,app/build.gradle.kts"
---

Use Android platform APIs conservatively and verify behavior against the project target SDK. Keep foreground-service startup user-initiated. `SYSTEM_ALERT_WINDOW` and Usage Access are Settings-granted special permissions, not ordinary runtime permissions. Do not use Accessibility Service as a shortcut.

The validated Android baseline already covers Usage Access, foreground monitoring, target detection, threshold triggering, the real overlay, cooldown, lock/unlock behavior, restart/persistence, voice, and both final decisions on physical hardware.

For a new Android change, validate proportionally:

- Pure UI/layout change: build + emulator/preview/device visual check as appropriate.
- Overlay interaction change: test the changed overlay path on a physical phone when possible.
- Monitor/permission/service change: test the specific eligibility/restart/permission behavior affected by the edit.
- Voice change: test the real recognizer path on physical hardware when possible.

Do not require unrelated OEM, midnight, battery, lock-screen, or permission-denial matrices for every small Android feature. Those remain deferred robustness scenarios unless the change touches them. Never claim a changed Android-core path is validated solely because it compiles.
