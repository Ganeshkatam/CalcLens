# CalcLens Security & Privacy Specification

## 1. Threat Model & Privacy Posture

CalcLens interacts with a highly sensitive sensory interface: the live mobile camera. The system treats camera frames as confidential, ephemeral sensor signals.

```text
Camera Sensor
      │
      ▼ (Volatile RAM/VRAM Buffer Only)
[On-Device Vision Pipeline]
      │
      ▼
[Deterministic Parser & Evaluator]
      │
      ▼
[Temporary GPU Canvas Display]
      │
      ▼
(Buffer Overwritten in Next Cycle)
```

---

## 2. Core Privacy Principles

### 2.1 Zero Persistent Storage of Camera Imagery
* Video frames and OCR pixel crops exist solely in volatile memory during active analysis.
* Frames are never written to file systems, application caches, SQLite databases, or local flash storage.
* Closing or backgrounding the application immediately releases and zeroes frame buffers.

### 2.2 Zero Network Transmission
* Version 1.0 (MVP) operates entirely offline without network dependencies.
* No network requests are made during camera streaming or calculation.
* No images, mathematical strings, or evaluated numbers are transmitted to remote servers or analytics backends.

### 2.3 Strict Least-Privilege Permissions
* The application requests only the `CAMERA` permission.
* No access is requested for Contacts, Location, Microphone, Photo Library (unless explicit user export is added in future versions), or External Storage.

---

## 3. Application Security & Input Sanitization

### 3.1 Mathematical Grammar Hardening
Untrusted visual text input is processed through a strict parser. To prevent memory exhaustion or stack overflows:
* **Recursion Depth Limit**: The recursive-descent parser enforces a hard maximum recursion depth of 32 levels, rejecting deeply nested malicious or corrupt expressions.
* **Token Length Bounds**: Operand strings are capped at 32 characters, preventing buffer overruns.
* **Safe Floating-Point Arithmetic**: Safe bounds checking prevents floating-point exceptions and IEEE-754 abnormal termination.

### 3.2 Safe Telemetry & Crash Reporting
If crash reporting or performance metrics are enabled:
* Raw OCR transcription text is scrubbed from crash reports to prevent leaking sensitive written notes.
* Diagnostic data is restricted strictly to non-identifying operational counters: frame rates, CPU temperature tiers, and parser error categories (`DIVISION_BY_ZERO`, `SYNTAX_ERROR`).
