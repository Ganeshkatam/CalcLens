# CalcLens User Flows & State Specifications

## 1. Primary User Journeys

### Journey 1: Initial App Launch & Camera Onboarding
* **Entry Condition**: App cold start, first installation.
* **Pre-condition**: Camera permission has not yet been requested or determined.
* **Flow**:
  1. The app launches directly into an immersive onboarding overlay displaying a high-contrast explanation: *"CalcLens requires camera access to recognize and solve arithmetic in your environment."*
  2. The user taps **"Enable Camera"** (or system prompt triggers immediately).
  3. **Permission Granted**: The onboarding layer fades out within 200 ms. The live camera preview initializes at full frame rate with a subtle scanning reticle or instructional prompt: *"Point at a printed equation (e.g. 27 × 14)"*.
  4. **Permission Denied**: The app transitions to an educational empty state with a direct button: **"Open System Settings"** to grant camera permissions.

```text
[App Cold Start]
        │
        ▼
[Check Camera Permission]
        │
   ┌────┴────────────────────────┐
   ▼                             ▼
[Granted]                    [Not Determined]
   │                             │
   │                             ▼
   │                    [System Permission Modal]
   │                             │
   │                    ┌────────┴────────┐
   │                    ▼                 ▼
   │               [Granted]          [Denied]
   │                    │                 │
   ▼                    ▼                 ▼
[Initialize Camera Feed (30+ FPS)]    [Settings Recovery Screen]
```

---

### Journey 2: Instant Single-Equation Calculation
* **Entry Condition**: Camera active and pointed at a surface with printed math (e.g., `27 × 14`).
* **Flow**:
  1. The user points the camera at a printed math problem.
  2. Within 150-200 ms, the frame scheduler dispatches a frame to the Vision Pipeline.
  3. The vision model detects bounding box coordinates `[x, y, w, h]` and outputs text `"27 × 14"` with confidence `0.94`.
  4. The recognition engine places the candidate in `VERIFYING` state.
  5. The next frame confirms the same expression. State transitions to `STABLE`.
  6. The deterministic parser builds AST: `Multiply(27, 14)` and evaluator yields `378`.
  7. The spatial overlay renders an anchored pill `[ 378 ]` directly below the physical text `27 × 14`.
  8. State transitions to `DISPLAYED`. Total elapsed time from aiming to projection: $< 300\text{ ms}$.

```text
Aim at "27 × 14"
      │
      ▼
Vision Detection (Confidence 0.94)
      │
      ▼
Verification Check (Frame N & Frame N+1 Match)
      │
      ▼
Parser & Evaluator (Result: 378)
      │
      ▼
Render Anchored Overlay [ 378 ]
```

---

### Journey 3: Dynamic Device Translation & Spatial Tracking
* **Entry Condition**: Result `[ 378 ]` is currently rendered over `27 × 14`.
* **Flow**:
  1. The user tilts or pans the device to read another part of the page.
  2. Between low-frequency recognition intervals (run at 2-4 Hz), the high-frequency Tracking Engine (running at 30-60 Hz) updates the bounding box coordinates using optical flow / visual feature correlation.
  3. The rendered answer pill `[ 378 ]` shifts smoothly in lockstep with the physical equation on paper without perceptible lag or spatial disconnect.
  4. Root Mean Square (RMS) jitter remains $< 2\text{ px}$.

```text
Physical Camera Movement
      │
      ├── High-Freq Loop (30-60 Hz): Optical Feature Tracking ──▶ Update Overlay Matrix
      │
      └── Low-Freq Loop (2-4 Hz): Vision Re-Detection ──▶ Recalibrate Spatial Anchor
```

---

### Journey 4: Multi-Equation Viewport
* **Entry Condition**: Camera viewport encompasses multiple printed problems simultaneously (e.g., a homework sheet or textbook).
* **Flow**:
  1. The vision system isolates multiple candidate bounding boxes:
     * Candidate A: `"125 + 87"`
     * Candidate B: `"45 − 17"`
     * Candidate C: `"144 ÷ 12"`
  2. Each candidate is assigned a unique UUID and independent state tracker.
  3. Expressions are parsed and evaluated concurrently:
     * Target A: `212`
     * Target B: `28`
     * Target C: `12`
  4. The overlay manager positions individual badges adjacent to each equation.
  5. If the user shifts the camera such that Candidate A exits the frame, Candidate A transitions to `LOST` and is culled, while Candidates B and C maintain stable continuous tracking.

```text
Viewport Encompasses Multiple Equations
┌──────────────────────────────────────────────┐
│  125 + 87                 144 ÷ 12           │
│   [ 212 ]                  [ 12 ]            │
│                                              │
│               45 − 17                        │
│                [ 28 ]                        │
└──────────────────────────────────────────────┘
```

---

### Journey 5: Syntax Errors & Undefined Mathematics
* **Entry Condition**: The camera observes an incomplete expression (e.g., `"27 + "`), unsupported symbols, or division by zero (`"100 ÷ 0"`).
* **Flow**:
  1. **Incomplete Expression (`"27 + "`)**:
     * Parser identifies trailing operator without right operand.
     * System marks candidate as `INCOMPLETE`.
     * Overlay does NOT project an erroneous answer; it either suppresses display or shows a subtle indeterminate indicator `[ ... ]` until the remaining operand enters the frame.
  2. **Division by Zero (`"81 ÷ 0"` or `"15 / (4 - 4)"`)**:
     * Evaluator detects zero divisor.
     * Overlay projects an explicit diagnostic pill: `[ Undefined ]` or `[ Cannot divide by 0 ]`.
     * The system never outputs `NaN`, `Infinity`, or crashes.

---

### Journey 6: Temporary Occlusion & Re-acquisition
* **Entry Condition**: An equation is actively tracked with a rendered answer, and a hand or pen briefly covers it.
* **Flow**:
  1. The user's hand passes over the equation.
  2. Visual tracking confidence drops below threshold.
  3. The tracking state transitions from `TRACKING` to `LOST` with a grace period timer ($T_{\text{grace}} = 500\text{ ms}$).
  4. **Hand removed within 500 ms**: Spatial anchor re-locks to the existing equation, restoring `DISPLAYED` status without re-triggering cold verification.
  5. **Hand remains > 500 ms**: The track is permanently deleted from memory. When the hand is later removed, the equation is processed as a fresh `DISCOVERED` candidate.

---

## 2. Recognition State Machine

Every detected mathematical entity adheres to the formal finite state machine:

```text
                ┌──────────────┐
                │  DISCOVERED  │
                └──────┬───────┘
                       │ Valid bounding box & candidate math glyphs
                       ▼
                ┌──────────────┐
                │  VERIFYING   │◀─────────────────────────┐
                └──────┬───────┘                          │
                       │ Consistent across >= 2 frames    │
                       ▼                                  │
                ┌──────────────┐                          │
                │    STABLE    │                          │
                └──────┬───────┘                          │
                       │ AST & Evaluation successful      │
                       ▼                                  │
                ┌──────────────┐                          │ Re-identified
                │  DISPLAYED   │                          │
                └──────┬───────┘                          │
                       │ Camera moving / feature tracking │
                       ▼                                  │
                ┌──────────────┐                          │
       ┌───────▶│   TRACKING   │                          │
       │        └──────┬───────┘                          │
       │               │ Feature correlation dropped      │
       │               ▼                                  │
       │        ┌──────────────┐                          │
       └────────┤     LOST     ├──────────────────────────┘
  Grace restored└──────┬───────┘
  (<= 500ms)           │ Expired (> 500ms)
                       ▼
                 [ DESTROYED ]
```

### State Definitions & Invariants

| State | Entry Condition | Actions Performed | Exit Criteria |
| :--- | :--- | :--- | :--- |
| `DISCOVERED` | Vision OCR identifies candidate mathematical region | Bounding box allocated, timer started | Transitions to `VERIFYING` if confidence $\ge 0.70$ |
| `VERIFYING` | Candidate matches initial criteria | Compare against next recognition frame ($N+1$) | `STABLE` if text matches; `LOST` if mismatch or lost |
| `STABLE` | Expression confirmed consistent across frames | Run normalizer, AST parser, and evaluator | `DISPLAYED` if evaluation succeeds; `LOST` if invalid syntax |
| `DISPLAYED` | Result computed and coordinate matrix ready | Render answer badge adjacent to bounding box | `TRACKING` upon frame translation; `LOST` if occluded |
| `TRACKING` | Frame translation detected | High-frequency optical tracking updates coordinates | `DISPLAYED` if stationary; `LOST` if visual features lost |
| `LOST` | Feature correlation lost or off-screen | Start $500\text{ ms}$ grace timer; hold last position | Restore to `TRACKING` if regained; `DESTROYED` if timeout |
| `DESTROYED` | Grace timer expired or explicitly dismissed | Release memory, de-allocate UUID and canvas elements | Terminal state |

---

## 3. Edge Cases & Resiliency Matrix

| Scenario | Risk | Mitigation Strategy |
| :--- | :--- | :--- |
| **Motion Blur (Fast Pan)** | OCR produces garbled strings (e.g. `27 * 14` reads as `2? x l4`) | Accelerometer / optical flow gate: Pause OCR trigger when angular velocity $> 45^\circ/\text{s}$. Rely on tracking or pause until camera stabilizes. |
| **Partial Occlusion** | Part of equation hidden (e.g. finger covers `14` in `27 × 14`) | Strict grammar rules reject trailing operator; anti-flicker delay prevents projecting partial evaluation. |
| **Specular Reflection / Glare** | Contrast washout causes dropped digits | Adaptive local thresholding in vision pre-processing; low-confidence scores suppressed. |
| **Dense Multi-Line Columns** | Nearby equations merge into single bounding box | Horizontal whitespace projection profile splits expressions separated by $> 2\times$ character height or width. |
| **Curved or Angled Paper** | Perspective distortion skews text alignment | Bounding quadrilateral extraction with affine deskewing prior to OCR text ingestion. |
| **Digit Confusion (O vs 0, l vs 1)** | False syntax rejection or wrong numbers | Mathematical context sanitization: characters flanked by numbers or arithmetic operators map to digits. |
