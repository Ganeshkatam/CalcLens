# CalcLens Development Roadmap

## 1. Development Milestones (M0 - M10)

```text
M0 ── Specification & Architectural Freeze
 │
 ▼
M1 ── Camera Stream & Lifecycle Layer
 │
 ▼
M2 ── Overlay Canvas & Coordinate Transform Pipeline
 │
 ▼
M3 ── Deterministic Math Parser & AST Evaluator
 │
 ▼
M4 ── Vision Recognition & Normalization Engine
 │
 ▼
M5 ── Real-Time Result Overlay (First End-to-End Loop)
 │
 ▼
M6 ── Spatial Tracking & Anti-Flicker Stabilization
 │
 ▼
M7 ── Multi-Expression Viewport Management
 │
 ▼
M8 ── Performance Tuning & Battery/Thermal Profiling
 │
 ▼
M9 ── Multi-Device Benchmark Testing
 │
 ▼
M10 ─ Version 1.0 (MVP) Production Release
```

---

## 2. Milestone Detailed Deliverables

### Milestone 0: Specification & Product Behavior Freeze (Current)
* **Goal**: Establish ironclad requirements, UX flows, system invariants, and architectural specifications before implementation begins.
* **Deliverables**:
  * Product Vision (`docs/product/vision.md`)
  * Product Requirements (`docs/product/requirements.md`)
  * User Flows & State Machine (`docs/product/user-flows.md`)
  * Roadmap & Release Criteria (`docs/product/roadmap.md`)
* **Success Criteria**: Zero ambiguity regarding module responsibilities and the boundary between Vision and Mathematics.

### Milestone 1: Camera Prototype & Stream Acquisition
* **Goal**: High-throughput, stable camera capture across device orientations with zero memory leaks.
* **Deliverables**:
  * Camera permission handler and fallback educational screen.
  * Continuous video frame capture pipeline ($\ge 30\text{ FPS}$).
  * Orientation adaptation and lifecycle pausing/resuming.
* **Success Criteria**: Stable 30 FPS stream with continuous memory utilization under 50 MB over 10 minutes.

### Milestone 2: Overlay Canvas & Coordinate Transform Engine
* **Goal**: Centralized coordinate mapping from camera sensor space to screen viewport coordinates with arbitrary aspect ratios.
* **Deliverables**:
  * Mathematical transform matrix: `CameraSpace -> NormalizedSpace -> ScreenSpace`.
  * Support for `AspectFill` and `AspectFit` preview geometries.
  * Render test harness displaying synthetic bounding boxes anchored to animated targets.
* **Success Criteria**: Sub-millisecond coordinate projection with zero aspect distortion.

### Milestone 3: Deterministic Math Parser & AST Evaluator
* **Goal**: Fully tested, 100% deterministic mathematical evaluation engine completely isolated from vision or camera code.
* **Deliverables**:
  * Mathematical Lexer and Token stream generator.
  * Recursive descent AST parser enforcing operator precedence (BODMAS/PEMDAS).
  * Evaluator supporting integers, `+`, `-`, `×`, `÷`, division-by-zero detection, and numeric overflow guards.
  * Comprehensive automated unit test suite with 100% branch coverage.
* **Success Criteria**: 0 math evaluation regressions, $< 1\text{ ms}$ evaluation latency across 10,000 synthetic test expressions.

### Milestone 4: Printed-Expression Vision Recognition & Normalization
* **Goal**: Extract candidate mathematical text and bounding geometries from camera frames.
* **Deliverables**:
  * Text detection and OCR integration (on-device engine).
  * Operator normalizer converting visual variants (`*`, `x`, `X`, `•` `-> ×`; `/`, `÷`, `:` `-> ÷`).
  * Mathematical context sanitization (e.g., `O` to `0`, `l` to `1`).
* **Success Criteria**: Accurate extraction of printed expressions at $\ge 90\%$ confidence in well-lit conditions.

### Milestone 5: Real-Time Result Overlay (The "CalcLens Moment")
* **Goal**: The first integrated end-to-end loop: pointing the camera at a printed equation renders the correct answer over the page in real time.
* **Deliverables**:
  * Frame scheduler orchestrating frame capture, vision processing, parsing, evaluation, and rendering.
  * End-to-end integration: `Camera -> Vision -> Normalizer -> Parser -> Evaluator -> Transform -> Overlay`.
* **Success Criteria**: Pointing at `27 × 14` visibly displays `[ 378 ]` over the text within 300 ms.

### Milestone 6: Spatial Tracking & Anti-Flicker Stabilization
* **Goal**: Anchor answers rigidly to physical equations as the camera moves and eliminate result flickering.
* **Deliverables**:
  * Temporal verification buffer (consistency required across $\ge 2$ frames).
  * Optical flow / lightweight feature tracking between OCR frames.
  * Exponential smoothing on overlay bounding box coordinates.
* **Success Criteria**: RMS spatial jitter $< 2\text{ px}$ during hand-held viewing; zero flickering on stable equations.

### Milestone 7: Multi-Expression Management
* **Goal**: Track and solve multiple equations simultaneously across the field of view.
* **Deliverables**:
  * Multi-target state manager tracking up to 8 distinct equation IDs concurrently.
  * Independent lifecycle tracking (`DISCOVERED`, `VERIFYING`, `STABLE`, `DISPLAYED`, `TRACKING`, `LOST`).
  * Spatial collision avoidance preventing overlapping answer badges.
* **Success Criteria**: Seamless real-time overlay of 4+ simultaneous expressions on a printed worksheet.

### Milestone 8: Performance Tuning & Thermal Profiling
* **Goal**: Minimize battery consumption and eliminate thermal throttling during extended use.
* **Deliverables**:
  * Dynamic frame scheduler adjusting OCR frequency based on device motion (accelerometer / gyro).
  * Offloading computation to background threads / web workers / hardware acceleration.
  * Memory profiling and buffer recycling to eliminate GC pauses.
* **Success Criteria**: Sustained $\ge 30\text{ FPS}$ operation for 15 consecutive minutes without thermal throttling or frame drops.

### Milestone 9: Device Benchmark Testing
* **Goal**: Validate stability across low-tier, mid-tier, and high-tier hardware configurations.
* **Deliverables**:
  * Standardized benchmark test card (varying fonts, print sizes, lighting, and angles).
  * Automated latency measurement harness.
  * Performance regression reports.
* **Success Criteria**: Meets all non-functional requirements across all target device tiers.

### Milestone 10: Version 1.0 (MVP) Production Release
* **Goal**: Ship the polished, zero-friction, privacy-first camera calculator.
* **Deliverables**:
  * Final release builds.
  * Offline-capable on-device distribution package.
  * Complete documentation and user guides.
* **Success Criteria**: Public release ready with 100% on-device processing and 0 cloud dependencies.

---

## 3. Product Evolution Versions (V1 - V4)

| Version | Focus | Core Capabilities |
| :--- | :--- | :--- |
| **Version 1.0 (MVP)** | Basic Printed Arithmetic | Integers, `+`, `-`, `×`, `÷`, single-line printed math, live spatial tracking, multi-target support. |
| **Version 2.0** | Extended Notation | Decimals, negative numbers, parentheses, exponentiation (`a^b`), square roots (`√`), percentages (`%`). |
| **Version 3.0** | Spatial Handwriting | Handwritten numbers and operators, stacked column arithmetic, two-dimensional fraction bars. |
| **Version 4.0** | Structural Mathematics | Linear algebra (`2x + 5 = 15`), geometric shape perimeter/area overlays, word problem extraction. |
