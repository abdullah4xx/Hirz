# PROGRESS.md

Session date: 2026-11-26
Device: OPPO CPH2579 (Oppo A38), Android 15, connected over wireless ADB at 192.168.1.5:37165.

## Phase 0 — Project setup
- Status: IN PROGRESS (3 of 4 checks PASS)

### Checks

- [x] **CI is green and produces `main` and `dev` APKs.** — PASS
  - Evidence: `gh run list` → run `45286901326` `completed success` (1m58s), artifact `hirz-apks-3` containing
    `prod/debug/app-prod-debug.apk` and `dev/debug/app-dev-debug.apk`.
  - Note: the release flavor is named `prod` instead of `main` because a Gradle product flavor named
    `main` collides with the default `main` source set (build failed with
    `Multiple entries with same key: main=[] and main=[]`). See `DECISIONS.md`.

- [x] **Both install on the phone and show the right name in Arabic and English.** — PASS
  - Evidence: `adb install -r` → `Success` for both APKs.
  - `aapt2 dump badging prod-installed.apk` → `application-label:'Hirz'`, `application-label-ar:'حِرز'`
  - `aapt2 dump badging dev-installed.apk` → `application-label:'Hirz Dev'`, `application-label-ar:'حِرز Dev'`

- [ ] **A newer build installs over an older one without uninstalling.** — PENDING
  - Plan: installed build is versionCode 3 (from CI). The local build is versionCode 10.
  - Evidence: (pending)

- [x] **Merged manifest has no `INTERNET` and no `MANAGE_EXTERNAL_STORAGE`.** — PASS
  - Evidence: `adb shell dumpsys package hirz.codx.app | grep -A25 "requested permissions"`
    ```
    requested permissions:
      hirz.codx.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
    ```
    Only the auto-generated AndroidX permission is present. No `INTERNET`, no
    `MANAGE_EXTERNAL_STORAGE`.

### Notes
- GitHub Actions still builds on push, but per the user's instruction no code is pushed until Phase 12 is
  complete. Local builds use the same fixed signing keystore.

## Phases 1-12
- Not started.
