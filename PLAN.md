# PLAN.md — Hirz (حِرز), a private encrypted media vault for Android

> This file is addressed to the **coding agent** that will build the project on the user's computer.
> It is the **single source of truth**. Re-read it at the start of every work session.
> App name: **Hirz** (en) / **حِرز** (ar). Package: **`hirz.codx.app`**. Ready-made icons are in `icons/` next to this file (Section 9).

---

## 0. RULES FOR THE AGENT (read first, obey always)

### 0.1 Access and tools
- You have full access to this computer: terminal, package installs, Android SDK, `adb` to the user's phone, GitHub CLI (`gh`), and so on.
- You control the phone through `adb` and the **mobile-mcp** server. Use every skill you have. If a **Material Design 3** skill exists, use it for all UI work; if not, follow the official M3 guidelines and standard Compose Material 3 components.

### 0.2 Language
- **Talk to the user in simple Egyptian Arabic.** The user is not a technical person. No jargon without a one-line explanation.
- Everything in the repo (code, comments, commit messages, docs) is in **English**.
- Every user-visible string in the app exists in **Arabic and English** (`values/strings.xml` and `values-ar/strings.xml`). Full RTL support.

### 0.3 STRICT PHASE ORDER (most important rule)
- Execute the phases of **Section 11 in numeric order, 0 → 12**.
- **NEVER skip, merge, reorder, or postpone a phase.** NEVER start phase N+1 before every "Done when" item of phase N is **verified on the real phone** and recorded in `PROGRESS.md`.
- If a phase is technically impossible as written: **STOP**, explain in Arabic, propose the closest alternative, and wait for the user's answer. Do not silently change the plan.

### 0.4 The loop you run for every phase
1. Read the phase text fully.
2. Implement it in small steps. Commit often.
3. Build with Gradle. Install on the phone: `adb install -r <apk>`.
4. Run **every** "Done when" check. Write PASS/FAIL and the evidence (command + short output) in `PROGRESS.md`.
5. If anything fails: fix it and re-run all checks of that phase.
6. Commit, then tag: `git tag phase-N-done`.
7. Only now start the next phase.

### 0.5 Decisions
- The decisions in **Section 2 are locked**. Do not change them without asking the user.
- For anything the plan does not specify: choose the best option yourself and write it in `DECISIONS.md` (decision + one-line reason).
- Ask the user only when truly blocked: first try at least 3 different approaches and log them in `PROGRESS.md`.

### 0.6 Files you must maintain in the repo root
- `PROGRESS.md` — per phase: status, checks with evidence, date.
- `DECISIONS.md` — your own technical decisions and reasons.
- `SECURITY-AUDIT.md` — created in phase 12.
- `tools/gen_test_data.py` — generates fake test data (see Section 12).

### 0.7 Data safety rules
- **Before phase 12 is finished and the user has reviewed its report: use ONLY generated test data. Never import the user's real photos or videos.**
- Never ask the user for, store, or log his real PIN or his real recovery key. Use throwaway test values in test installs.
- Never log key material, PINs, file names, or file paths in any build type. `dev` builds may log counts and timings only.
- **No `INTERNET` permission, ever.** No analytics, no crash-reporting, no ad or telemetry libraries. A library that needs `INTERNET` is forbidden.
- Never invent your own cryptography. Use the libraries named in Section 3 only.

### 0.8 Working with limited context
- Work in small steps. Keep each file focused. Commit after each step.
- At the start of every session read: this file, `PROGRESS.md`, `DECISIONS.md`.
- When unsure whether something is done, check `PROGRESS.md` and the git tags, not your memory.

---

## 1. What the app is

An Android app (APK, personal use only, not on Google Play). The user stores **photos, videos, audio, and other files** inside it. Files stay **only inside the app, encrypted**. They do not appear in the gallery or in any file manager. The user can view and play them inside the app, move them back to their original locations whenever he wants, and, if the app is deleted by mistake, restore everything from an encrypted backup using a recovery key.

**Top priority:** photos and videos. Audio is second. The general-files tab is lowest priority.

---

## 2. LOCKED DECISIONS (from the user)

| Topic | Decision |
|---|---|
| Name | **حِرز** (ar) / **Hirz** (en). Displayed name follows the phone language. |
| `applicationId` | **`hirz.codx.app`** for the real app. Fixed forever, never changes. |
| Build flavors | `main` = `hirz.codx.app` (the real app). `dev` = `hirz.codx.app.dev` (label "Hirz Dev", debuggable, `FLAG_SECURE` OFF, **test data only**, used for visual review and the security audit). |
| Min SDK | **30 (Android 11)**. Target SDK: latest available. Test phone: Oppo A38 (Android 13). |
| Lock methods | PIN + fingerprint only. **No pattern.** |
| PIN | **4 to 12 digits, minimum 4.** Show a warning in setup that a longer PIN is safer. |
| Wrong PIN attempts | **No delay and no lockout.** This is the user's explicit decision. Do not add one. |
| Auto-lock | The app locks **when the screen turns off or when the user leaves the app** (goes to home or to another app). **No idle timer.** A video playing while the screen is on and the app is in front **never** locks. |
| Forgot PIN | **Required:** "Forgot PIN?" on the lock screen, the user enters the recovery key, then sets a **new PIN** without losing any data (Section 4.4). |
| Screenshots | Blocked (`FLAG_SECURE`) in the `main` flavor on all screens. |
| Recents screen | A setting with two modes: **Blank** (default, strongest) or **Blur** (Section 4.6). |
| Encryption | Strongest standard encryption available. No homemade crypto (Section 3, 4). |
| View and restore | View photos and play videos and audio **inside the app**. Restore files **to their original locations**. |
| Organization | **4 tabs: Photos / Videos / Audio / Files**, plus **albums**, **favorites**, and **tags**. |
| Adding files | A floating **+** button, multi-select. |
| Original after import | **Deleted automatically** after verification, with a Google Photos warning. If deletion is impossible: explain and offer "Try again" (Section 6.2). The user has Google Photos and accepts that originals are removed from the cloud too. |
| Duplicates | If an imported file has the **same content** (same SHA-256) as an item already in the vault: **ask the user each time** (Skip / Import anyway, with "apply to all remaining in this batch"). |
| GPS and EXIF | **Kept exactly as in the original file.** Never strip metadata. |
| Delete inside vault | **Trash for 30 days.** |
| Backup | **Encrypted, outside the app, from the first release.** Stored on **phone internal storage only** in a public folder. The user accepts that theft, format, or a broken phone also destroys the backup. Restorable with the recovery key after reinstall. |
| Recovery key | Exists from the first release. The user will keep it on paper and in a password manager. |
| Notifications | Import, backup, and restore notifications show **numbers only** (for example "Importing 5 of 40 files"). **Never file names.** Lock-screen visibility = secret. |
| Colors | Follow the system (Material You / Dynamic Color). |
| Design | Material Design 3. |
| Languages | Arabic + English, full RTL. |
| Icons | **All 5 icons** in the first release. User picks in Settings. Default = Shield (Section 9). |
| Video | Mostly plain **mp4**. Subtitles: **SRT, VTT, and ASS**. Skip-forward button default **85 s, adjustable in Settings**. |
| Images | **View only.** No editing, no filters, no crop. (Temporary rotate while viewing is allowed and is not saved.) |
| Expected size | **Under 10 GB** of media. |
| Build | GitHub Actions builds the APK. Repository is **private**. |
| Updates | A new build must install **over** the old one without losing the vault (Section 10). |
| Security audit | Phase 12: the agent attacks the app like a hacker, reports only, **never fixes by itself**, and the user decides. |

---

## 3. Tech stack (agent's choices, follow them)

- **Language/UI:** Kotlin, Jetpack Compose, Material 3.
- **Database:** Room. Metadata only. Sensitive columns are encrypted at field level (Section 6.3).
- **File encryption:** AES-256-GCM through **Tink Streaming AEAD** (`AES256_GCM_HKDF_4KB`), using **seekable channels** so large videos can seek instantly. **One random key per file.** Per-file keys are wrapped by the Master Key.
- **Key protection:** Android Keystore. Use StrongBox when available, fall back to TEE.
- **PIN key derivation:** **Argon2id** through a trusted library (for example libsodium or an Argon2 JNI wrapper). Tune parameters to about **300–500 ms** on the user's phone and record the values in `DECISIONS.md`.
- **Biometrics:** `androidx.biometric`, `BIOMETRIC_STRONG` only, always with a `CryptoObject`.
- **Images:** Coil with a custom fetcher that decrypts into memory.
- **Video and audio:** Media3 (ExoPlayer) with a **custom `DataSource`** that decrypts while playing. Subtitles via Media3's built-in parsers: SubRip, WebVTT, SSA/ASS.
- **PDF:** `PdfRenderer` fed through `StorageManager.openProxyFileDescriptor` with a `ProxyFileDescriptorCallback` that reads from the encrypted seekable channel. **Do not** write a decrypted PDF to disk.
- **Background work:** WorkManager for import, backup, restore, trash emptying. Long jobs run as **foreground work** (`setForeground`) with foreground service type `dataSync` and permissions `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`.
- **Release builds:** R8 minify and shrink enabled for `main` release.
- **Permissions (only these):**
  - API 30–32: `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`).
  - API 33+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`. On API 34+ also handle partial access (`READ_MEDIA_VISUAL_USER_SELECTED`): import only what the user selected and explain it.
  - `POST_NOTIFICATIONS`, `USE_BIOMETRIC`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `REQUEST_DELETE_PACKAGES`.
  - **Forbidden:** `INTERNET`, `MANAGE_EXTERNAL_STORAGE`.

---

## 4. Security requirements

### 4.1 Storage rules
1. Vault files live in the app's private storage (`filesDir`) under **random names**. Real names are stored only encrypted in Room.
2. Every file is **always encrypted on disk**. Decryption happens **in memory at view time only**. **No unencrypted temp files, ever.** Any cache (thumbnails, PDF pages) is encrypted or memory-only and is **cleared on lock**.
3. `allowBackup="false"` and `dataExtractionRules` that block cloud backup and device transfer.
4. The app has **no network code at all**.

### 4.2 Key hierarchy
- **Master Key (MK):** random 256-bit, generated once.
- MK is wrapped (AES-256-GCM, random 96-bit nonce, AAD = a fixed label) **three times**:
  - **(a) Biometric wrap:** a Keystore AES key with user authentication required (`setUserAuthenticationRequired(true)`, `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`, `setInvalidatedByBiometricEnrollment(true)`), used through a `CryptoObject`.
  - **(b) PIN wrap:** KEK = HKDF( Argon2id(PIN, salt) ‖ HMAC(non-exportable Keystore HMAC key, salt) ). The Keystore secret makes PIN guessing possible **only on this phone**.
  - **(c) Recovery wrap:** KEK derived from the recovery key (Section 7).
- Per-file keys are wrapped by MK with the file id as AAD (so blobs cannot be swapped between items).
- A **metadata key** is derived from MK with HKDF (label `"meta"`) and is used for field-level encryption in Room.
- On lock: **zero** MK and every derived key in memory, drop caches, release players.
- If the biometric key is invalidated (fingerprint enrolled or removed in system settings), fall back to the PIN and offer to re-bind the fingerprint.

### 4.3 Lock rules
- Lock immediately on: `ACTION_SCREEN_OFF`, and app moving to background (`ProcessLifecycleOwner` ON_STOP).
- While the screen is on and the app is in front, never lock because of inactivity.
- After process death the app always starts locked.
- Audio and video playback stop on lock.

### 4.4 Forgot-PIN flow
1. Lock screen has a **"Forgot PIN?"** link.
2. User enters the **recovery key**.
3. Derive the recovery KEK, unwrap MK. Wrong key fails safely with no hints.
4. User sets a **new PIN** (4–12 digits). Re-wrap MK with the new PIN KEK. Offer to re-bind the fingerprint.
5. No data is lost or re-encrypted (only MK wraps change).

### 4.5 No PIN lockout (user decision)
- Do not add delays or lockouts after wrong PINs.
- In PIN setup show an honest note in both languages: with a 4-digit PIN, someone holding the unlocked phone and an automation tool could try all 10,000 combinations in roughly an hour, and a longer PIN is much safer.

### 4.6 Recents screen modes (Settings)
- **Blank (default):** `FLAG_SECURE` on all windows. Also blocks screenshots.
- **Blur:** show a blurred full-screen cover when the activity pauses so the system snapshot is blurred. Test on the real phone whether screenshots can still be blocked in this mode. If they cannot, show a clear warning in Settings (ar/en) that screenshots become possible in Blur mode, and record the result in `DECISIONS.md`.
- The `dev` flavor never uses `FLAG_SECURE`.

### 4.7 Other
- Notifications: numbers only, visibility secret.
- Release builds contain no logging of sensitive data. `dev` may log counts and timings only.
- Android system backup and ADB backup must yield nothing useful.

---

## 5. Screens and features

### A) General
- **First-run setup:** choose PIN (with warning), enable fingerprint, show the **recovery key once** (Section 7), choose the backup folder, show a short tip to set battery usage for Hirz to "Unrestricted / allow background activity" (Oppo ColorOS kills background work) with a button that opens the app's system settings page.
- **Lock screen:** number pad, fingerprint button, "Forgot PIN?".
- **Home:** 4 tabs (Photos / Videos / Audio / Files) + floating **+**.
- Search by name and by tag. Sort by date, name, size. Multi-select (restore, delete, add to album, add tag, favorite).
- **Albums:** create, rename, delete album (items stay in vault), add or remove items, an item can be in several albums.
- **Favorites:** star on items, a "Favorites" filter.
- **Tags:** free-text tags, many per item, tag filter and tag search.
- **Settings:** change PIN, Recents mode, language (system / Arabic / English), app icon (Section 9), skip-forward seconds (default 85), backup (folder, last backup, back up now, view or change recovery key after verification, backup warnings), **"Export everything to original places"**, trash, about.

### B) Photo viewer (top priority)
- Pinch zoom and pan, swipe between photos, temporary rotate (not saved), automatic slideshow.
- Fast thumbnail grid with thousands of items: lazy loading, encrypted-or-memory cache.
- View only. No editing.

### C) Video player (top priority)
- Plays directly from the encrypted file with instant seeking.
- **Skip-forward button**: default **85 s**, adjustable in Settings (5–600 s). Forward only, separate from the 10 s double-tap.
- Double-tap ±10 s. Remember position per video. Speed 0.5x–2x. Auto rotation and fullscreen.
- Gestures: vertical swipe on the left for brightness, right for volume.
- **Subtitles: SRT, VTT, ASS.** The user attaches a subtitle file to a video. It is stored **encrypted** and linked to the video. Load it as a Media3 `SubtitleConfiguration` from a decrypting source. Detect text encoding automatically (UTF-8 and Windows-1256 for Arabic). On/off toggle. Per-video, several subtitle files allowed.
- Do not promise formats beyond what the phone can decode. mp4 (H.264) must work perfectly.

### D) Audio tab
- List of audio files and voice notes. Name, duration.
- In-app player (Media3 with the same decrypting `DataSource`): play/pause, seek, speed, simple queue of the current list.
- Audio **stops when the app goes to the background or the screen turns off** (consistent with the lock rules). No background playback in v1.

### E) Files tab (lowest priority)
- **PDF:** internal reader (Section 3).
- **Everything else** (Word, zip, apk…): **export** only (decrypted copy to a folder the user picks, or to the original place). No opening with other apps directly.

---

## 6. Import and restore

### 6.1 Import
- The **+** button opens an **in-app picker** built on `MediaStore` for photos, videos, and audio. Reason: it gives the **original `RELATIVE_PATH`** and the capture date, and gives URIs that can be deleted with **`MediaStore.createDeleteRequest`** (API 30+, one confirmation dialog per batch).
- If the user denies the permission: fall back to the system Photo Picker or SAF and explain that deleting the original and remembering the original path may not work.
- **General files:** `ACTION_OPEN_DOCUMENT` (multiple). Delete with `DocumentsContract.deleteDocument` if supported, otherwise explain.
- **Big files:** streaming encryption, never load a whole file in memory. Progress bar plus notification (numbers only). Runs as foreground work so ColorOS does not kill it. Must resume cleanly if interrupted (track per-file state in Room: pending / encrypted / verified / original-deleted).
- Before starting, check free space is enough for the file **plus** its backup copy.
- **Duplicates:** compute SHA-256 while reading. If it matches an existing item (including trash), ask: Skip / Import anyway, with an "apply to all remaining" checkbox.

### 6.2 Deleting the original after import
1. Compute SHA-256 of the original while reading. After encrypting, **decrypt the stored file and compute its SHA-256 again**. If they differ: **do not delete the original**, retry, tell the user.
2. After verification, request deletion of the originals (one confirmation per batch).
3. If the user refuses or deletion fails: say the original still exists and show **"Try deleting again"**. The vault copy is fine in all cases.
4. After every import show a warning (ar/en): the item may also exist in **Google Photos**, and the **Oppo gallery may keep deleted items in its own "Recently deleted"**, with buttons that open those apps (via Intent, no internet permission).

### 6.3 Data stored per item
Original name, type, size, original `RELATIVE_PATH`, capture and modified dates, SHA-256, tab, trash state, favorite flag, playback position, subtitle links.
Room tables (at least): `Item`, `Album`, `AlbumItem`, `Tag`, `ItemTag`, `Subtitle`, `Settings` if needed.
**Names and paths of items, album names, and tag names are stored encrypted** (field-level, metadata key). For search, decrypt into an in-memory index after unlock and drop it on lock.

### 6.4 Restore to original places
- **Restore item(s):** write back with `MediaStore` insert using the original `RELATIVE_PATH` and capture date (`IS_PENDING=1` while writing, then `0`).
- MediaStore only accepts certain root folders per type (images: `DCIM`, `Pictures`; videos: `DCIM`, `Movies`; audio: `Music`, `Podcasts`, `Recordings`, `Ringtones`, …; other files: `Download` or `Documents`). If the original path is outside the allowed roots (for example another app's private media folder), restore to `Pictures/Hirz-Restored/<original path>` (or the matching root) and tell the user why.
- Options: **Copy** (item stays in vault, default) or **Move** (removed from vault after success and after fingerprint or PIN verification).
- **"Export everything to original places"**: one button in Settings, after PIN or fingerprint, with progress and notification. After it finishes show **"Uninstall the app"** (`REQUEST_DELETE_PACKAGES`).

---

## 7. Backup and recovery key (from the first release)

**Why:** Android never runs app code on uninstall, so "restore on uninstall" is impossible. The solution is an encrypted copy outside the app that survives uninstall.

- **Recovery key:** generated once at setup, **at least 160 random bits**, shown **once** as groups (for example `XXXX-XXXX-...`) with copy and share buttons. To continue, the user must **re-enter two randomly chosen groups** (not always the first and last). The app **does not store the key**. It stores only the MK wrapped by it.
- **Backup folder:** chosen by the user with `ACTION_OPEN_DOCUMENT_TREE`, persistable permission. It must be a **public folder** (default suggestion `Documents/Hirz-Backup` on internal storage), **not** an app-private folder. Reject restricted Android folders and explain.
- **Contents:**
  - `header` file: version, salt, Argon2id parameters, MK wrapped by the recovery key.
  - **Blocks:** the vault's encrypted files **copied as-is** (same ciphertext, no re-encryption), random names, spread into subfolders by the first 2 characters of the name to avoid giant directories.
  - **Encrypted manifest:** original names and paths, dates, tab, trash state, albums, favorites, tags, playback positions, subtitle links, wrapped per-file keys.
  - The folder is visible in a file manager but everything inside is unreadable.
- **Manifest safety (important):** write `manifest.tmp`, flush, then rename to `manifest.bin`, and keep the previous one as `manifest.prev`. Never overwrite in place. On restore, if `manifest.bin` is damaged or truncated, fall back to `manifest.prev` and tell the user.
- **SAF performance:** SAF is slow with many files. List children once per run with `DocumentsContract` queries and cache the listing. Do not call per-file `DocumentFile` lookups in loops.
- **Running:** automatic in the background (WorkManager, foreground work) after every import or change, **incremental**, verified by checksum, mirrors deletions, restores, and trash. Buttons: **Back up now**, last backup time and size. User may pause it with a warning.
- **Restore after reinstall:** first-run screen has **"Restore from backup"** → pick folder → enter recovery key → unwrap MK → re-wrap with the new Keystore → rebuild Room from the manifest → copy blocks into place → user sets a **new PIN and fingerprint**. All files return to the vault.
- **Warnings shown in Settings (ar/en):** the backup uses extra space (files exist twice), and a phone format, theft, or loss also destroys the backup folder. The user has accepted this risk, so show it once as information, do not nag.
- Backup and manifest formats carry a **version number** so they can evolve.

---

## 8. Trash (30 days)

- Deleting from the vault moves the item to trash (`trashedAt`), still encrypted.
- From trash: **restore to vault**, **delete forever**, **empty trash**.
- **Auto-empty after 30 days** (WorkManager and on app open).
- Backup mirrors trash state and permanent deletion.

---

## 9. Icons (already prepared in `icons/`)

Each icon has its own folder named by its id, containing SVG masters and PNGs per layer.

| Resource id | Name (ar / en) | Description | |
|---|---|---|---|
| `ic_hirz_shield` | درع / Shield | White shield with a keyhole on a teal gradient | **Default** |
| `ic_hirz_vault` | خزنة / Vault | Vault door with a wheel on a purple gradient | |
| `ic_hirz_lock_blue` | قفل أزرق / Lock Blue | Blue-gradient lock on a raised dark square | |
| `ic_hirz_vault_green` | خزنة خضراء / Vault Green | Green vault on a raised dark square | |
| `ic_hirz_solid` | سوليد / Solid | White vault on black, no colors | |

Files per folder (`<id>` = icon id):
- `<id>_preview.svg` / `<id>_preview_1024.png`: full final look (use in the icon picker screen).
- `<id>_background.svg` / `.png`: background layer (108x108, full bleed).
- `<id>_foreground.svg` / `.png`: symbol layer inside the **safe zone** (66x66 of 108), transparent.
- `<id>_monochrome.svg` / `.png`: single-color version for themed icons (Android 13+).
- Layer PNGs are 1080x1080 (downscale per density, xxxhdpi = 432). Preview PNG is 1024x1024.

**Implementation:**
1. Convert the layer SVGs to **VectorDrawable** (use the PNGs if conversion breaks a gradient or `evenOdd`). Create `res/mipmap-anydpi-v26/<id>.xml` for each icon:
   ```xml
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
     <background android:drawable="@drawable/<id>_background"/>
     <foreground android:drawable="@drawable/<id>_foreground"/>
     <monochrome android:drawable="@drawable/<id>_monochrome"/>
   </adaptive-icon>
   ```
2. **Switching uses `<activity-alias>`:** one alias per icon pointing to `MainActivity`, each with its own `android:icon` and `android:roundIcon`, intent filter `MAIN` + `LAUNCHER`. The default alias (`ic_hirz_shield`) is `enabled="true"`, the others `enabled="false"`. `MainActivity` itself has **no LAUNCHER filter** (so only one icon appears). `android:label` = `@string/app_name` everywhere.
3. **From Settings:** a picker showing the five icons (from the `*_preview_1024.png` files, scaled down). Switch with `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)`: **enable the new one first, then disable the others.** Save the choice. The launcher may take seconds or drop the old home-screen icon, so show a warning before switching. Switching never deletes vault data.
4. `android:icon` on `<application>` = the default icon (Shield).
5. Because of the `activity-alias` design, a later "disguise" feature (alternate name and icon) will be easy.
6. Check on the real phone that the dark "raised square" icons do not look doubled under the Oppo launcher's icon shape. If they do, report it to the user with a screenshot of the home screen taken from the `dev` flavor, and wait for his decision.

---

## 10. Build, CI, and updating over the old version

- Create a **private** GitHub repo (suggested name `hirz`). GitHub Actions builds: `main` debug (every push), `main` release (on tag), and `dev` (every push).
- **Signing keystore:** generate **once**. Store it as **GitHub Secrets** (base64 plus passwords). **Never commit it.** Give the user **a backup copy** and explain in Arabic why it matters: if it is lost he can never update the installed app without uninstalling it (which deletes the vault).
- Test on the user's phone with `adb`.

### Update-over-update (hard requirement)
1. `applicationId` `hirz.codx.app` never changes (display name is separate from the id).
2. **The same signing key in every build, including debug builds made by CI.** The default per-machine debug keystore breaks updates. Use the one fixed keystore from the secret for all build types and both flavors.
3. `versionCode` increases automatically (from the GitHub run number). `versionName` comes from the tag.
4. **No data loss on update:** explicit Room migrations (**`fallbackToDestructiveMigration` is forbidden**). Put a **format version** in encrypted-file format and in backup format.
5. The Keystore keys keep the **same aliases** across versions (they survive updates, and are lost only on uninstall).
6. **Test it for real:** install version A, import test files, install version B over it, and confirm every file still opens.

---

## 11. PHASES (strict order, see Section 0.3 and 0.4)

### Phase 0 — Project setup
Do: create the Gradle project (`hirz.codx.app`), flavors `main` and `dev`, minSdk 30, Material 3 theme with Dynamic Color and a fallback palette, RTL, Arabic and English strings, private GitHub repo and CI, the fixed signing keystore (and its backup for the user), versionCode automation, and `tools/gen_test_data.py`.
Done when:
- [ ] CI is green and produces `main` and `dev` APKs.
- [ ] Both install on the phone and show the right name in Arabic and English.
- [ ] A newer build installs over an older one **without uninstalling**.
- [ ] Merged manifest has no `INTERNET` and no `MANAGE_EXTERNAL_STORAGE`.

### Phase 1 — Setup, keys, lock
Do: first-run flow, PIN 4–12 with the warning, fingerprint, recovery key (shown once, two-group confirmation), the full key hierarchy of 4.2, lock screen, forgot-PIN flow, lock rules of 4.3, `FLAG_SECURE` (main only).
Done when:
- [ ] Correct PIN unlocks, wrong PIN fails, there is no delay or lockout.
- [ ] The fingerprint really unlocks the key (not just the UI). Changing the system fingerprints sends the user to the PIN.
- [ ] Forgot-PIN works end to end with the recovery key, and a wrong key fails safely.
- [ ] Locks on screen off and on leaving the app. No idle timer.
- [ ] Screenshot attempt is blocked in `main` (black or refused).
- **USER CHECKPOINT:** tell the user (Arabic) to write the **test** recovery key down only if he wants to practice. Never ask for his real one.

### Phase 2 — Encrypted storage and database
Do: Tink Streaming AEAD storage API (`write`, `openSeekable`, delete), random file names, Room schema v1 with field-level encryption, metadata key, instrumented tests.
Done when (on the phone):
- [ ] Round trip passes for 1 KB, 10 MB, and a generated 1 GB file, using streaming (memory stays flat).
- [ ] Random-access seek returns correct bytes.
- [ ] Flipping one byte or truncating a blob is detected (error, no crash). Swapping two blobs is detected.
- [ ] No plaintext name or magic bytes appear in files or in the database file.

### Phase 3 — Import
Do: the **+** button, in-app MediaStore picker, permissions per API level (including partial access on 34+), foreground import work, duplicate prompt, verification, original deletion with `createDeleteRequest`, "Try again", Google Photos and Oppo-gallery warning, numbers-only notifications, resume after interruption.
Done when:
- [ ] Importing 50 generated photos and 3 videos works, originals are deleted after one confirmation.
- [ ] Killing the app mid-import and reopening it resumes without duplicates or loss.
- [ ] A duplicate prompts correctly, including "apply to all remaining".
- [ ] Refusing the deletion keeps the original and "Try again" works.
- [ ] Notification text has no file names, and is hidden on the lock screen.
- [ ] SHA-256 of imported-then-exported test files equals the original.

### Phase 4 — Photos tab and viewer
Done when:
- [ ] Grid with 2,000 generated thumbnails scrolls smoothly.
- [ ] Zoom, pan, swipe, temporary rotate, slideshow work.
- [ ] No plaintext temp files or cache remain after viewing (check storage listing).
- [ ] GPS EXIF of a test photo is intact after a full import and export.

### Phase 5 — Videos tab and player
Done when:
- [ ] A generated 1 GB mp4 plays and seeks instantly.
- [ ] The skip-forward button defaults to 85 s and the value can be changed in Settings. Double-tap ±10 s, speed, gestures, rotation, resume position all work.
- [ ] SRT, VTT, and ASS test files display correctly, including Arabic text in Windows-1256 and UTF-8.
- [ ] Locking stops playback.

### Phase 6 — Audio tab
Done when:
- [ ] mp3, m4a, ogg/opus, wav, and a voice-note file import and play.
- [ ] Audio stops on lock and when the app goes to the background.

### Phase 7 — Organization, trash, restore, export
Do: albums, favorites, tags, search, sort, multi-select, 30-day trash with auto-empty, restore to original places, Copy/Move, "Export everything", uninstall button.
Done when:
- [ ] Albums, favorites, and tags work and survive an app restart. Names are encrypted in the database.
- [ ] Restore puts a test photo back in its original `RELATIVE_PATH` with the same capture date. An original path outside allowed roots uses the fallback and tells the user.
- [ ] Trash: delete, restore, delete forever, and auto-empty (simulate 31 days with a debug time hook in `dev` only).
- [ ] **Wipe scenario:** Export everything → uninstall → all test files are in their original places.

### Phase 8 — Encrypted backup and restore
Do: Section 7 in full, including atomic manifest and `manifest.prev` fallback.
Done when:
- [ ] Backup runs automatically after an import and mirrors deletes, trash, and album changes.
- [ ] **Accidental-wipe scenario:** uninstall without exporting → reinstall → restore with the recovery key → every file, album, tag, favorite, subtitle link, and position returns.
- [ ] A wrong recovery key fails safely. A truncated `manifest.bin` falls back to `manifest.prev`.
- [ ] Backup folder contains no plaintext names or magic bytes.
- [ ] Backup interrupted mid-run (kill the app) leaves a valid state.

### Phase 9 — Files tab
Done when:
- [ ] A generated PDF opens in the internal reader through the proxy file descriptor with **no decrypted file written to disk**.
- [ ] Other file types export correctly (SHA-256 equal).

### Phase 10 — Icons
Do: Section 9 in full.
Done when:
- [ ] All 5 icons switch correctly and vault data is untouched.
- [ ] Themed (monochrome) icons follow system colors on Android 13.
- [ ] The Section 9 step 6 launcher check is done.

### Phase 11 — Settings completion, hardening, performance, update test
Do: complete every Settings item, Recents Blank/Blur modes with the result of 4.6 recorded, language switch, battery tip, R8 release build, remove all sensitive logging, performance pass, final lint.
Done when:
- [ ] Every item listed under Settings in Section 5 works in Arabic and English (RTL checked screen by screen).
- [ ] Release build is minified, not debuggable, no sensitive logs (check logcat during a full session).
- [ ] 2,000 photos plus a 1 GB video: no ANR, acceptable startup after unlock.
- [ ] **Update test:** version A with data → version B over it → everything intact.

### Phase 12 — Security audit ("attack your own app")
Follow Section 13 exactly. This phase **reports only**. It never changes app code.

---

## 12. Test data (used from Phase 0 until Phase 12 is reviewed)

`tools/gen_test_data.py` must generate, and push to the phone with `adb push` into a test folder under `Pictures`, `Movies`, `Music`, and `Documents`:
- 2,000 small images with EXIF (some with GPS), a few HEIC if the phone supports it.
- Videos: a small mp4, a 100 MB mp4, and a 1 GB mp4.
- Audio: mp3, m4a, ogg/opus, wav, and a voice-note-like m4a.
- A PDF, a docx, a zip.
- Subtitles in SRT, VTT, and ASS (one Arabic in Windows-1256, one Arabic in UTF-8).
- Duplicate files (same content, different names).
- Every generated file contains a unique marker string such as `HIRZ-TEST-<random>` where the format allows it, so leaks can be found by searching.

---

## 13. Phase 12 details — security audit

### 13.1 Rules
- Attack **only** the Hirz test installs on the user's phone, using **only test data** and a **throwaway test PIN and recovery key**. Never use the user's real PIN, recovery key, or files.
- Attacker model: someone who holds the phone, can use `adb`, but **does not know the PIN**. Second model: someone who copied the backup folder.
- No network attacks. Never touch other apps' data. No destructive commands outside Hirz (`pm clear hirz.codx.app.dev` is allowed).
- **Do not fix anything. Do not edit app code.** Report only. The user decides.
- Save every dump, log, and output you produce under `audit-artifacts/` and list them in the report, so the user can search them himself.

### 13.2 Canary test (done by the user)
1. Ask the user (Arabic) to connect the phone by USB and enable USB debugging.
2. Ask him to create **his own file** (a photo or text file) containing a secret phrase only he knows, and to **import it himself** into the **test install** (`hirz.codx.app.dev`).
3. He must **not tell you** its name, content, or the phrase. **You must not look for it by opening it in the app.** You try to extract secrets only the way an outsider could (13.3).
4. After the attack he will search everything under `audit-artifacts/`, the backup folder, and logs for his phrase. If it appears anywhere, that is a Critical finding.

### 13.3 Attack checklist (run every item, mark PASS / FAIL / N-A with evidence)

**A. Static analysis of the release APK**
- Decompile (jadx or similar). Check the manifest: `debuggable` false, `allowBackup` false, no `INTERNET`, no cleartext traffic, only the launcher aliases exported, no exported providers or receivers that leak data.
- Search code and resources for hard-coded keys, salts, PINs, or secrets.
- Confirm R8 is on. Confirm no sensitive logging calls.

**B. Shared storage as an outsider (no root)**
- List `/sdcard` recursively and `grep -a` the whole tree (including the backup folder) for: the `HIRZ-TEST-` markers, JPEG (`FFD8FF`), MP4 (`ftyp`), PDF (`%PDF`), ID3, and RIFF headers. Nothing from the vault may appear in plaintext. File names must be random.

**C. Private storage (`dev` flavor, `run-as`)**
- `filesDir` blobs: no magic bytes, high entropy, no plaintext names.
- The Room database: run `strings` and open it. No plaintext item names, paths, album names, tag names, or markers.
- `cache` and `code_cache`: no temp files after viewing images, videos, PDFs, audio.

**D. Logs**
- Capture `logcat` through import, view, restore, backup, lock, unlock, forgot-PIN. No PIN, key, file name, path, or marker may appear.

**E. Backup folder attacks**
- Search for markers: none. Inspect `header`: only version, salt, and KDF parameters.
- Try a wrong recovery key: fails safely with no hints.
- `adb backup` and a cloud-backup attempt return nothing useful.

**F. UI-level checks while locked**
- `adb exec-out screencap` on the `main` build: blank or refused.
- `uiautomator dump` on the lock screen: no file names or content.
- Recents thumbnail: blank (or blurred in Blur mode, with the warning shown).
- `dumpsys notification` after import or backup: no file names, secret visibility.

**G. Lock bypass attempts**
- Start every component with `am start -n` and every alias and deep link. Each must land on the lock screen.
- Kill and relaunch the process, swipe from Recents, rotate, split-screen: stays locked.
- Biometric: confirm the key is bound to authentication (a `CryptoObject` is needed). Confirm re-enrolling a fingerprint invalidates it.
- After screen off or leaving the app, confirm the app is locked and playback has stopped.

**H. PIN guessing measurement**
- Automate up to 30 wrong PIN attempts through the UI and measure time per attempt. Report the estimated time for the full 4-digit space and for 6 digits. **Do not** run the full search.

**I. Memory remnants (`dev`)**
- After lock, dump the heap (`am dumpheap`) and search for markers and key-like material. Report anything found.

**J. Tampering**
- Flip one byte, truncate, and swap two encrypted blobs in the `dev` private storage: the app must detect it, show an error, and not crash or corrupt other items.
- Do the same to a backup block and to `manifest.bin` (fallback to `manifest.prev` must work).

**K. Update**
- Install a newer build over the old one: the vault is intact.

### 13.4 Report (`SECURITY-AUDIT.md`)
For each finding: **ID, severity (Critical / High / Medium / Low / Info), what you did (command and short output), impact, suggested fix.** At the top: a summary table of all A–K items with PASS / FAIL.
Then tell the user in simple Egyptian Arabic the top findings, **and wait for his decision** on what to fix. Only after he decides may you change code (as a new tagged fix phase), and only for the items he approved.

### 13.5 After the audit
Real files may be imported **only after** the user has reviewed this report and decided on every finding.

---

## 14. Deferred ideas (NOT in the first release)

- A **desktop recovery tool** (small Python script) that can open the backup without the app.
- Disguise mode (alternate name and icon). The `activity-alias` base is already there.
- Decoy PIN that opens an empty vault.
- Share-to-Hirz from the gallery.
- A built-in camera that saves straight into the vault.
- Picture-in-Picture and background audio playback.
- Image editing, album sharing, and any cloud sync (cloud sync is excluded forever by the no-`INTERNET` rule).
