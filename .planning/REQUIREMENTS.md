# Requirements: PDF Scanner — Security Hardening

**Defined:** 2026-03-03
**Core Value:** Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.

## v1.2 Requirements

Requirements for security hardening milestone. Each maps to roadmap phases.

### Security Foundation

- [x] **SEC-01**: App sets FLAG_SECURE on all screens to prevent screenshots and Recents thumbnails of sensitive documents
- [x] **SEC-03**: Production log calls (Log.v/d/i) stripped via ProGuard rule; Log.w/e retained for crash diagnostics
- [x] **SEC-04**: Network security config XML explicitly disables cleartext traffic (defense-in-depth for offline app)
- [x] **SEC-05**: Temp files in cacheDir cleaned immediately after use via finally blocks with randomized names
- [x] **SEC-06**: All non-launcher components explicitly marked `android:exported="false"` in manifest

### Input Hardening

- [ ] **SEC-07**: Navigation args containing file paths validated against app-private storage boundaries; imported URIs validated for expected MIME types
- [x] **SEC-08**: Document history SharedPreferences encrypted at rest using Tink AEAD with Android Keystore-backed keys

### App Lock

- [ ] **SEC-02**: User can enable biometric/PIN app lock via Settings; BiometricPrompt with DEVICE_CREDENTIAL fallback
- [ ] **SEC-13**: App re-requires authentication after configurable timeout when backgrounded (immediate/30s/1min/5min)

### Data Protection

- [ ] **SEC-09**: All document images and PDFs encrypted at rest using Tink StreamingAead; existing files migrated on first launch
- [ ] **SEC-10**: File deletion overwrites content with random bytes before removing filesystem reference
- [ ] **SEC-11**: Clipboard content marked as sensitive via ClipDescription.EXTRA_IS_SENSITIVE flag
- [ ] **SEC-12**: Sensitive views (document names, paths) protected from untrusted accessibility services via accessibilityDataSensitive attribute

### Detection

- [ ] **SEC-14**: App detects rooted/debuggable device and shows one-time warning dialog (not blocking)

## Future Requirements

### Extended Security

- **QUAL-01**: JaCoCo hard enforcement gate in CI — add after coverage is stable across multiple milestones
- **QUAL-02**: CI/CD pipeline (GitHub Actions) — v2+ milestone

## Out of Scope

| Feature | Reason |
|---------|--------|
| Certificate pinning | App is offline-only; no backend to pin against |
| Custom encryption implementation | Use Tink (Google-maintained) instead of rolling own crypto |
| Blocking rooted devices | False positives from custom ROMs; warn-only approach (SEC-14) |
| DRM-style content protection | Conflicts with core sharing/export functionality |
| Per-document passwords | Requires iTextPDF/PDFBox (out of scope); app-level encryption covers this |
| SQLCipher/Room encrypted database | App uses SharedPreferences, not SQLite; Tink encryption is sufficient |
| Biometric-only auth (no PIN fallback) | Accessibility concern; BiometricPrompt with DEVICE_CREDENTIAL is correct |
| Full binary obfuscation (DexGuard) | R8 provides adequate obfuscation; commercial tool adds cost/complexity |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEC-01 | Phase 6 | Complete |
| SEC-02 | Phase 9 | Pending |
| SEC-03 | Phase 6 | Complete |
| SEC-04 | Phase 6 | Complete |
| SEC-05 | Phase 6 | Complete |
| SEC-06 | Phase 6 | Complete |
| SEC-07 | Phase 7 | Pending |
| SEC-08 | Phase 7 | Complete |
| SEC-09 | Phase 8 | Pending |
| SEC-10 | Phase 8 | Pending |
| SEC-11 | Phase 10 | Pending |
| SEC-12 | Phase 10 | Pending |
| SEC-13 | Phase 9 | Pending |
| SEC-14 | Phase 10 | Pending |

**Coverage:**
- v1.2 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0

---
*Requirements defined: 2026-03-03*
*Last updated: 2026-03-03 after roadmap creation*
