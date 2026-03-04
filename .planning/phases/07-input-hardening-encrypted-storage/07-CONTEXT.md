# Phase 7: Input Hardening & Encrypted Storage - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Validate all external input against path traversal and MIME type attacks, and encrypt SharedPreferences at rest using Tink AEAD with Android Keystore-backed keys. Includes crash-safe migration from unencrypted to encrypted storage with graceful fallback on problematic devices (API 24-27).

</domain>

<decisions>
## Implementation Decisions

### Validation failure behavior
- Navigate back + Snackbar on path validation failure (consistent with existing Snackbar pattern from v1.0)
- Error messages are security-neutral: generic "Document not available" — no information leakage about validation mechanism
- Path validation checks storage boundary only (not file existence — that's already handled by DocumentEntry.exists() and other existing checks)
- Centralized InputValidator utility class in util/ package (consistent with ImageUtils, PdfUtils pattern)

### MIME type acceptance
- Image imports: accept image/* (covers JPEG, PNG, WebP, HEIC from camera/gallery)
- PDF imports: MIME check only via contentResolver.getType() — PdfRenderer already fails gracefully on corrupted files
- application/octet-stream URIs: reject per banking-app security stance — users can use gallery picker instead of file managers
- MIME failure message: "Unsupported file type" — clear but doesn't reveal accepted types

### Encryption scope
- Encrypt BOTH prefs files (document_history + pdf_scanner_prefs) — banking-app stance, encrypt everything
- Merge into single EncryptedSharedPreferences file — simpler key management, one migration path, keys namespaced by prefix
- Migration is silent — SharedPreferences are small, near-instant migration, no UI needed
- Old unencrypted files retained through v1.2 cycle (already decided) — delete in Phase 10 audit

### KeyStore failure handling
- Silent fallback to unencrypted prefs — no user notification, log for diagnostics only
- 3 retries with brief delay before declaring persistent failure
- Retry KeyStore each app launch — if system update fixes it, encryption kicks in automatically
- Fallback scope: prefs only (this phase) — Phase 8 file encryption handles its own fallback separately

### Claude's Discretion
- InputValidator internal implementation (canonicalization approach, etc.)
- Exact retry delay strategy for KeyStore attempts
- Migration sentinel key naming and verification logic
- R8 keep rules specifics for Tink classes
- Prefs key prefix scheme for merged file

</decisions>

<specifics>
## Specific Ideas

- Validation must cover 3 nav args: PreviewFragment.imageUri, PdfViewerFragment.pdfPath, PdfEditorFragment.pdfUri
- All file storage uses filesDir (scans/, processed/, pdfs/) — clear canonical boundary for path validation
- PdfPageExtractor.isPdfFile() already checks MIME — extend pattern, don't duplicate
- DocumentHistoryRepository is a singleton with PREFS_NAME = "document_history" and AppPreferences uses "pdf_scanner_prefs" — both need migration
- Existing pattern: DocumentEntry stores filePaths as absolute paths in JSON — these are the sensitive data to encrypt

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- PdfPageExtractor.isPdfFile(): existing MIME check pattern to extend for image URIs
- DocumentHistoryRepository singleton: migration target, already centralizes all document_history access
- AppPreferences: migration target, simple key-value wrapper
- Snackbar extension from v1.0: reuse for validation error display

### Established Patterns
- Utility classes in util/ package (ImageUtils, PdfUtils, ImageProcessor) — InputValidator follows this
- Singleton pattern with synchronized block (DocumentHistoryRepository) — use for SecurePreferences
- SafeArgs for navigation (PreviewFragmentArgs, PdfViewerFragmentArgs, PdfEditorFragmentArgs) — validation intercepts here
- All fragments use findNavController().navigate() with Directions classes

### Integration Points
- PreviewFragment.onViewCreated(): validate args.imageUri before use
- PdfViewerFragment.onViewCreated(): validate args.pdfPath before use
- PdfEditorFragment.onViewCreated(): validate args.pdfUri before use
- PagesFragment, PreviewFragment, HomeFragment: validate content URIs at import point
- DocumentHistoryRepository constructor: swap SharedPreferences for EncryptedSharedPreferences
- AppPreferences constructor: swap SharedPreferences for EncryptedSharedPreferences
- Application.onCreate() or first access: trigger migration

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-input-hardening-encrypted-storage*
*Context gathered: 2026-03-03*
