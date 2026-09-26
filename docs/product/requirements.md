# CalcLens Product Requirements Document (PRD)

## 1. Document Control & Overview

This document specifies the functional and non-functional requirements for **CalcLens (Version 1.0 MVP)**. It defines system inputs, behavioral constraints, performance limits, and acceptance criteria.

---

## 2. Functional Requirements (FR)

### FR-1: Camera Layer & Frame Acquisition
* **FR-1.1 Permission Handling**: The system must request camera permissions with explicit rationale prior to initiating stream capture. If denied, the system must display an instructional fallback state guiding the user to system settings.
* **FR-1.2 Stream Initialization**: The camera stream must initialize within 500 ms of permission grant, defaulting to the primary rear-facing wide-angle camera sensor.
* **FR-1.3 Sensor Lifecycle**: The camera stream must pause immediately when the application moves to the background and resume within 300 ms upon foreground return.
* **FR-1.4 Orientation Adaptation**: The camera feed and viewport transformation matrix must adapt automatically to portrait, landscape-left, and landscape-right orientations without distortion or aspect-ratio stretching.
* **FR-1.5 Target Resolution**: The camera pipeline must capture frames at 1080p (1920x1080) or 720p (1280x720) depending on hardware profile, targeting a continuous 30 FPS minimum.

### FR-2: Frame Scheduling
* **FR-2.1 Dual-Rate Scheduling**: The system must decouple vision recognition from spatial tracking.
  * **Tracking Loop**: Must execute at the camera frame rate (30 to 60 Hz).
  * **Recognition Loop**: Must execute intermittently (2 to 5 Hz or upon significant frame displacement).
* **FR-2.2 Concurrency Isolation**: Heavy vision inference must run on dedicated worker threads or GPU acceleration buffers, never blocking UI rendering or camera preview threads.

### FR-3: Mathematical Region Detection & OCR
* **FR-3.1 Candidate Region Detection**: The vision pipeline must isolate text regions containing numerical and operator glyphs from background noise or unrelated prose text.
* **FR-3.2 Symbol Recognition**: The system must recognize:
  * Digits: `0`, `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`
  * Operators: `+`, `-`, `−`, `*`, `x`, `X`, `×`, `/`, `÷`
* **FR-3.3 Geometric Bounding Box**: Each detected candidate must output normalized bounding coordinates: `[x_min, y_min, width, height]` relative to camera frame dimensions.
* **FR-3.4 Confidence Scoring**: Each token and candidate expression must provide an aggregate confidence score between `0.0` and `1.0`.

### FR-4: Expression Normalization
* **FR-4.1 Operator Normalization**: The normalizer must map visually varied operator symbols into canonical mathematical tokens:
  * Addition: `+`
  * Subtraction: `-`, `−`, `—` (em-dash/en-dash OCR errors) `-> -`
  * Multiplication: `*`, `x`, `X`, `×`, `•` `-> ×`
  * Division: `/`, `÷`, `:` `-> ÷`
* **FR-4.2 Whitespace Stripping**: The normalizer must collapse variable whitespace between numbers and operators (e.g., `"27    +  14"` `->` `"27 + 14"`).
* **FR-4.3 Digit Disambiguation**: Common OCR confusion pairs in mathematical context must be sanitized (e.g., uppercase `O` surrounded by digits mapped to `0`, lowercase `l` or uppercase `I` surrounded by digits mapped to `1`).

### FR-5: Mathematical Parsing
* **FR-5.1 Deterministic Lexer**: The lexer must tokenize normalized strings into discrete typed tokens: `NUMBER(value)`, `OPERATOR(type)`, `LPAREN`, `RPAREN`.
* **FR-5.2 Grammar & AST Generation**: The parser must generate an Abstract Syntax Tree (AST) respecting standard mathematical operator precedence (BODMAS/PEMDAS):
  * Multiplication and Division bind tighter than Addition and Subtraction.
  * Left-associative evaluation for identical precedence tiers (`a - b - c` parsed as `(a - b) - c`).
* **FR-5.3 Syntax Validation**: Incomplete or malformed sequences (e.g., `"27 + "`, `"× 14"`, `"5 ++ 2"`) must be flagged as `SYNTAX_INVALID` and rejected before reaching evaluation.

### FR-6: Deterministic Math Evaluator
* **FR-6.1 Arithmetic Precision**: The evaluation engine must compute arithmetic deterministically using exact rational or bounded IEEE-754 arithmetic with decimal formatting up to 6 decimal places.
* **FR-6.2 Division by Zero**: Division by zero (`a ÷ 0`) must return a discrete `DIVISION_BY_ZERO` error condition without crashing or stalling the pipeline.
* **FR-6.3 Numeric Boundaries**: Integer operations exceeding safe 64-bit bounds must return an `OVERFLOW` condition rather than producing silent truncation.

### FR-7: Coordinate Transformation Matrix
* **FR-7.1 Centralized Pipeline**: A dedicated coordinate transform utility must convert coordinates across spaces:
  $$\text{Camera Frame Space } (W_c \times H_c) \longrightarrow \text{Normalized Space } [0, 1] \longrightarrow \text{Screen Viewport Space } (W_s \times H_s)$$
* **FR-7.2 Aspect Ratio Handling**: Transformations must support `AspectFill` and `AspectFit` camera preview modes, correctly offsetting letterboxing or crop margins.

### FR-8: Real-Time Spatial Tracking
* **FR-8.1 Inter-Frame Tracking**: Once an expression is verified, its bounding box position must update frame-to-frame using lightweight spatial tracking (bounding box velocity extrapolation and feature correlation) between OCR recognition intervals.
* **FR-8.2 Discard Threshold**: If an expression bounding box exits the visible viewport or tracking correlation drops below threshold for > 500 ms, the track must be marked `LOST` and culled.

### FR-9: Temporal Stability & State Machine
Every detected candidate must transition through the formal lifecycle:
```text
[DISCOVERED] ──(Passes initial heuristic)──▶ [VERIFYING]
                                                 │
                   ┌─────────────────────────────┘
                   │ (Observed across >= 2 frames with consistent expression)
                   ▼
               [STABLE] ──(AST & Evaluator succeed)──▶ [DISPLAYED]
                                                           │
                   ┌───────────────────────────────────────┘
                   │ (Camera moving, high-frequency track updates)
                   ▼
               [TRACKING] ──(Lost tracking > 500ms)──▶ [LOST] ──▶ (Purged)
```
* **FR-9.1 Anti-Flicker Consistency**: A result may not be rendered to the user until the expression text has remained identical across at least 2 consecutive recognition frames or met a confidence threshold $\ge 0.90$.

### FR-10: Multi-Expression Concurrency
* **FR-10.1 Multiple Targets**: The system must simultaneously detect, track, and display results for up to 8 distinct expressions within the active camera frame.
* **FR-10.2 Spatial Disambiguation**: Detections must maintain unique UUIDs and spatial anchors, preventing overlay collisions or swapped answer projections.

### FR-11: Overlay Rendering
* **FR-11.1 Non-Obstructive Placement**: Answer badges must project directly adjacent (default: directly below or right-aligned) to the detected expression bounding box without obscuring the original physical formula.
* **FR-11.2 High-Contrast Legibility**: Answer pills must use a high-contrast theme (dark frosted glass with crisp white/accent typography) legible across varied paper and table backgrounds.

### FR-12: Error Diagnostics & Feedback
* **FR-12.1 Visible Failure**: The system must fail visibly and informatively rather than silently producing incorrect outputs.
* **FR-12.2 Status Indicators**:
  * High Confidence: Displays evaluated result `[ 378 ]`.
  * Uncertain / Parsing: Displays subtle indeterminate pulse `[ ... ]`.
  * Division by Zero / Undefined: Displays clear indicator `[ Undefined ]`.

---

## 3. Non-Functional Requirements (NFR)

### NFR-1: Performance & Frame Budget
* **Camera Throughput**: $\ge 30\text{ FPS}$ sustained on reference hardware.
* **Overlay Render Time**: $\le 16.6\text{ ms}$ per render frame (60 FPS ceiling).
* **Recognition Latency**: $\le 200\text{ ms}$ from expression entry into frame to answer projection.
* **Evaluation Latency**: $\le 1\text{ ms}$ execution time for AST construction and arithmetic computation.
* **Visual Jitter**: Spatial drift of rendered overlays must be $< 2\text{ px RMS}$ during stationary device hold.

### NFR-2: Memory, Power & Thermals
* **Memory Ceiling**: Runtime memory footprint of CalcLens core must remain below 150 MB.
* **Thermal Throttling Prevention**: Continuous camera and tracking operation must not induce device thermal throttling within 15 minutes of uninterrupted use.
* **Leak-Free Resource Lifecycle**: Video buffers, canvas contexts, and tracking caches must be deterministically released on frame destruction.

### NFR-3: Privacy, Security & Data Governance
* **100% On-Device Processing**: In Version 1.0, zero bytes of camera stream, OCR tokens, or mathematical results may leave the physical device over network interfaces.
* **Zero Persistent Storage of Imagery**: Camera frames may exist only in volatile runtime RAM/VRAM during active processing.
* **No Telemetry of Camera Content**: Error tracking and crash reporting must never serialize image pixels or recognized text.

### NFR-4: Code Quality & Architectural Integrity
* **Strict Decoupling**: The math parser and evaluator must have zero dependencies on camera, UI, or vision libraries, enabling 100% automated headless unit testing.
* **Deterministic Testability**: 100% of mathematical grammar rules, operator precedences, edge cases, and normalization routines must be verified via automated unit test suites.

---

## 4. MVP Scope & Boundaries

### Included in Version 1.0 (MVP)
* Printed single-line arithmetic on paper, screens, or whiteboards.
* Operands: Non-negative integers (`0` to `999,999,999`).
* Operators: `+`, `-`, `×`, `÷`.
* Live spatial overlay with temporal stabilization.
* Up to 8 concurrent equations in camera viewport.

### Excluded from Version 1.0 (MVP)
* Handwritten mathematics (deferred to V3).
* Multi-line fractions and stacked column arithmetic (deferred to V3).
* Variables, algebra, and equations with equality signs (`x + 2 = 10`) (deferred to V4).
* User accounts, historical calculation logs, or export integrations.
* Cloud API processing or external neural inference services.
