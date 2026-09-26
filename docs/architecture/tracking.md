# CalcLens Tracking & Spatial Stabilization

## 1. The Tracking Problem

When a user points a mobile device at a physical surface, continuous hand tremors, device re-orientation, and panning shift the position of text in camera coordinate space every frame ($30\text{ to }60\text{ times per second}$).

Without tracking:
```text
Frame 1: Equation at (200, 400) ──▶ Answer rendered at (200, 430)
Frame 2: Hand moves. Equation is now at (180, 390).
         Answer remains stuck at (200, 430) until next OCR completes (250 ms later).
         Result: Laggy, disconnected, floating overlay.
```

With continuous spatial tracking:
```text
Frame 1: Equation at (200, 400) ──▶ Answer rendered at (200, 430)
Frame 2: Optical tracking updates delta: dx = -20, dy = -10.
         Answer moves in lockstep to (180, 420).
         Result: Physical equation and answer badge appear welded together.
```

---

## 2. Tracking Architecture & Dual-Loop Synchronization

```text
High-Frequency Loop (30 - 60 Hz)            Low-Frequency Loop (2 - 5 Hz)
Camera Frame N                              Camera Frame N (Sampled)
      │                                           │
      ▼                                           ▼
Optical Feature Tracking                     OCR & Region Detection
(Lucas-Kanade / Motion Estimation)           (Text, Confidence, Geometry)
      │                                           │
      ▼                                           ▼
Compute Frame Displacement (dx, dy)          New Candidate Bounding Boxes
      │                                           │
      ▼                                           ▼
Apply Delta to Active Anchors                IoU Matching & Calibration
      │                                           │
      └─────────────────────┬─────────────────────┘
                            │
                            ▼
              Exponential Position Smoothing
                            │
                            ▼
                Render Anchored Overlays
```

---

## 3. Spatial Association & Data Association (IoU)

When the vision pipeline generates a new set of bounding boxes $B_{\text{new}}$, the tracker must correlate them with existing tracked entities $E_{\text{active}}$:

1. **Intersection over Union (IoU)**:
   $$\text{IoU}(A, B) = \frac{\text{Area}(A \cap B)}{\text{Area}(A \cup B)}$$
2. **Matching Criteria**:
   * If $\text{IoU} \ge 0.40$ and the normalized expression matches: The existing track is calibrated with the new visual bounding box.
   * If $\text{IoU} < 0.40$: A spatial distance metric and text similarity check determines if the target shifted rapidly or represents a new equation.
   * Unmatched existing tracks enter `LOST` state.
   * Unmatched new detections enter `DISCOVERED` state.

---

## 4. Jitter Elimination & Smoothing Filters

Raw coordinates from visual bounding boxes or optical flow can introduce high-frequency visual vibration ("jitter"). CalcLens applies an Exponential Moving Average (EMA) or Low-Pass filter:

$$\hat{P}_t = \alpha P_t + (1 - \alpha) \hat{P}_{t-1}$$

* **Stationary Mode ($\alpha = 0.25$)**: When the device is held relatively still, $\alpha$ is reduced, prioritizing visual stability and achieving $< 2\text{ px RMS}$ jitter.
* **Dynamic Translation Mode ($\alpha = 0.85$)**: When rapid panning is detected, $\alpha$ dynamically increases, eliminating perceived drag and lag behind the physical page.

---

## 5. Occlusion Recovery & Track Lifecycle

```text
Active Track (DISPLAYED / TRACKING)
             │
             ▼
Visual Features Lost (Occlusion / Fast Pan)
             │
             ▼
Transition to LOST State
             │
             ├── Timer < 500 ms ──▶ Features Re-acquired? ──▶ Return to TRACKING
             │
             └── Timer >= 500 ms ──▶ Deallocate Entity & Purge from Memory
```

* **Grace Period ($500\text{ ms}$)**: During brief occlusions (e.g. a finger or pencil passing across the line), the overlay holds its projected position based on last known velocity rather than abruptly popping out of existence.
* **Purge Threshold**: If features are not recovered within $500\text{ ms}$, the entity is deallocated to prevent ghost overlays.
