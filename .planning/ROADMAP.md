# Roadmap: PDF Scanner — Polish & Quality Pass

## Milestones

- ✅ **v1.0 Polish Pass** — Phases 1-3 (shipped 2026-03-01)
- ✅ **v1.1 Quality Gates** — Phases 4-5 (shipped 2026-03-03)
- **v1.2 Security Hardening** — Phases 6-10 (in progress)

## Phases

<details>
<summary>v1.0 Polish Pass (Phases 1-3) — SHIPPED 2026-03-01</summary>

- [x] Phase 1: Stability (4/4 plans) — completed 2026-03-01
- [x] Phase 2: Design System (8/8 plans) — completed 2026-03-01
- [x] Phase 3: Performance & Polish (3/3 plans) — completed 2026-03-01

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>v1.1 Quality Gates (Phases 4-5) — SHIPPED 2026-03-03</summary>

- [x] Phase 4: Test Coverage (7/7 plans) — completed 2026-03-02
- [x] Phase 5: Release Readiness (3/3 plans) — completed 2026-03-03

Full details: `.planning/milestones/v1.1-ROADMAP.md`

</details>

### v1.2 Security Hardening

- [ ] **Phase 6: Security Foundation & Quick Wins** - FLAG_SECURE, Timber log stripping, network config, temp file hardening, exported component audit
- [ ] **Phase 7: Input Hardening & Encrypted Storage** - Intent/path validation, SharedPreferences encryption with crash-safe migration
- [ ] **Phase 8: File Encryption at Rest** - Tink StreamingAead encryption across all document I/O paths with existing file migration
- [ ] **Phase 9: Biometric App Lock** - Opt-in biometric/PIN lock with configurable auto-lock timeout
- [ ] **Phase 10: Hardening Polish & Audit** - Clipboard protection, accessibility data sensitivity, root detection, cross-cutting security audit

## Phase Details

### Phase 6: Security Foundation & Quick Wins
**Goal**: App prevents information leakage through screenshots, logs, network config, temp files, and exported components
**Depends on**: Phase 5 (v1.1 complete)
**Requirements**: SEC-01, SEC-03, SEC-04, SEC-05, SEC-06
**Success Criteria** (what must be TRUE):
  1. Screenshots and Recents thumbnails show blank/secure content on all screens containing document data
  2. Release APK produces zero Log.v/d/i output in logcat; Log.w/e retained for crash diagnostics
  3. Cleartext HTTP traffic is explicitly blocked via network security config (verified via `adb shell dumpsys`)
  4. Temp files use randomized names and are cleaned up in finally blocks; no stale temp files survive app restart
  5. All non-launcher components in AndroidManifest are explicitly marked `android:exported="false"`
**Plans**: 2 plans
**Research**: Complete — standard Android API patterns

Plans:
- [ ] 06-01-PLAN.md — FLAG_SECURE screenshot prevention + temp file security hardening (SEC-01, SEC-05)
- [ ] 06-02-PLAN.md — ProGuard log stripping + network security config + exported component audit (SEC-03, SEC-04, SEC-06)

### Phase 7: Input Hardening & Encrypted Storage
**Goal**: App validates all external input against path traversal and encrypts SharedPreferences at rest with crash-safe migration
**Depends on**: Phase 6
**Requirements**: SEC-07, SEC-08
**Success Criteria** (what must be TRUE):
  1. Navigation args containing file paths are validated against app-private storage boundaries; paths outside app storage are rejected
  2. Imported content URIs are validated for expected MIME types before processing
  3. Document history and app preferences are encrypted at rest using Tink AEAD with Android Keystore-backed keys
  4. Existing unencrypted SharedPreferences are migrated to encrypted storage without data loss (idempotent, crash-safe with sentinel key)
  5. App gracefully falls back to unencrypted prefs on devices with persistent KeyStore failures (API 24-27 edge cases)
**Plans**: 2 plans
**Research**: Complete — security-crypto:1.1.0, path canonicalization, MIME validation patterns

Plans:
- [ ] 07-01-PLAN.md — InputValidator utility + fragment path validation + MIME validation at import (SEC-07)
- [ ] 07-02-PLAN.md — EncryptedSharedPreferences with SecurePreferences singleton + migration + backup rules (SEC-08)

### Phase 8: File Encryption at Rest
**Goal**: All document images and PDFs are encrypted at rest; existing unencrypted files are migrated transparently
**Depends on**: Phase 7 (SecurePreferences must exist for migration sentinel storage)
**Requirements**: SEC-09, SEC-10
**Success Criteria** (what must be TRUE):
  1. All newly captured, processed, and generated files (scans/, processed/, pdfs/) are AES-256-GCM encrypted on disk
  2. Existing unencrypted document files are migrated to encrypted storage on first launch with progress UI
  3. All document viewing, sharing, and editing workflows function identically to pre-encryption behavior (encryption is transparent to the user)
  4. File deletion of encrypted documents is cryptographically secure (encrypted content with KeyStore-managed key is equivalent to secure erasure on flash storage)
**Plans**: 4 plans
**Research**: Complete — Tink StreamingAead, CameraX encrypt-in-place, Coil decrypt-to-bitmap, PdfRenderer decrypt-to-temp

Plans:
- [ ] 08-01-PLAN.md — SecureFileManager singleton + tink-android dependency + ProGuard + backup rules (SEC-09, SEC-10)
- [ ] 08-02-PLAN.md — Encrypt/decrypt in utility classes and editor components (SEC-09)
- [ ] 08-03-PLAN.md — Encrypt/decrypt in UI fragments, adapters, and DocumentHistory secure delete (SEC-09, SEC-10)
- [ ] 08-04-PLAN.md — Migration flow with progress UI in HomeFragment (SEC-09)

### Phase 9: Biometric App Lock
**Goal**: Users can opt into biometric/PIN authentication with configurable auto-lock when the app is backgrounded
**Depends on**: Phase 7 (SecurePreferences must exist for lock state storage)
**Requirements**: SEC-02, SEC-13
**Success Criteria** (what must be TRUE):
  1. User can enable biometric/PIN app lock via a toggle in Settings (opt-in, not forced)
  2. When app lock is enabled, BiometricPrompt with DEVICE_CREDENTIAL fallback gates app access on cold start and resume from background
  3. App re-requires authentication after configurable timeout when backgrounded (immediate/30s/1min/5min selectable in Settings)
  4. Biometric authentication works correctly across API tiers: API 24-27, API 28-29, and API 30+ (three distinct behavioral paths)
**Plans**: 2 plans
**Research**: Complete — biometric:1.1.0, ProcessLifecycleOwner, API-tiered authenticator selection

Plans:
- [ ] 09-01-PLAN.md — AppLockManager utility + biometric dependency + Settings UI with lock toggle and timeout selector (SEC-02, SEC-13)
- [ ] 09-02-PLAN.md — BiometricPrompt enforcement in MainActivity + ProcessLifecycleOwner + lock overlay (SEC-02, SEC-13)

### Phase 10: Hardening Polish & Audit
**Goal**: Remaining security features are added and a cross-cutting audit verifies no plaintext leaks survive across all prior phases
**Depends on**: Phases 6-9 (all security features implemented)
**Requirements**: SEC-11, SEC-12, SEC-14
**Success Criteria** (what must be TRUE):
  1. OCR text copied to clipboard is marked as sensitive (ClipDescription.EXTRA_IS_SENSITIVE) and not visible in clipboard preview on API 33+
  2. Sensitive views (document names, file paths) are protected from untrusted accessibility services via accessibilityDataSensitive attribute on API 34+
  3. App detects rooted/debuggable device environment and shows a one-time warning dialog (non-blocking, dismissible)
  4. Release APK passes full security audit: no plaintext document data on disk, no log leaks, all encrypted operations verified on physical device
**Plans**: 2 plans
**Research**: Complete — platform APIs, no external dependencies

Plans:
- [ ] 10-01-PLAN.md — Sensitive clipboard (SEC-11) + accessibility data protection (SEC-12) + root/debuggable detection with warning dialog (SEC-14)
- [ ] 10-02-PLAN.md — Release APK build + cross-cutting security audit verification (all SEC requirements)

## Progress

**Execution Order:** Phases 6 -> 7 -> 8 -> 9 -> 10

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Stability | v1.0 | 4/4 | Complete | 2026-03-01 |
| 2. Design System | v1.0 | 8/8 | Complete | 2026-03-01 |
| 3. Performance & Polish | v1.0 | 3/3 | Complete | 2026-03-01 |
| 4. Test Coverage | v1.1 | 7/7 | Complete | 2026-03-02 |
| 5. Release Readiness | v1.1 | 3/3 | Complete | 2026-03-03 |
| 6. Security Foundation & Quick Wins | v1.2 | 0/2 | Planned | - |
| 7. Input Hardening & Encrypted Storage | v1.2 | 0/2 | Planned | - |
| 8. File Encryption at Rest | v1.2 | 0/4 | Planned | - |
| 9. Biometric App Lock | v1.2 | 2/2 | Complete | 2026-03-05 |
| 10. Hardening Polish & Audit | 1/2 | In Progress|  | - |
