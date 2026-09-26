# CalcLens Performance Engineering & Optimization

## 1. Frame Budget & Latency Architecture

CalcLens enforces strict latency budgets to prevent visual stutter, dropped camera frames, and battery drain.

```text
60 FPS Display Budget: 16.6 ms per frame
┌───────────────────────┬───────────────────────────────────┐
│ Overlay Render: 2 ms  │ Idle / Headroom: 14.6 ms          │
└───────────────────────┴───────────────────────────────────┘

30 FPS Camera Budget: 33.3 ms per frame
┌───────────────────────┬───────────────────┬───────────────┐
│ Sensor Capture: 8 ms  │ Tracking: 4 ms    │ Idle: 21.3 ms │
└───────────────────────┴───────────────────┴───────────────┘

Asynchronous Vision Budget (Off-Main-Thread Worker):
┌───────────────────────────────────────────────────────────┐
│ Downsample: 15 ms │ OCR Inference: 120 ms │ Norm: 2 ms    │  ──▶ Total: ~137 ms
└───────────────────────────────────────────────────────────┘
```

---

## 2. Quantitative Performance Targets

| Operational Metric | Target Threshold | Maximum Acceptable | Measurement Method |
| :--- | :--- | :--- | :--- |
| **Camera Preview Rate** | $\ge 30\text{ FPS}$ | $24\text{ FPS}$ | Sensor frame timestamp delta |
| **Overlay Render Rate** | $60\text{ FPS}$ | $30\text{ FPS}$ | `requestAnimationFrame` timing |
| **Tracking Pipeline Latency** | $< 5\text{ ms}$ | $10\text{ ms}$ | High-res monotonic clock (`performance.now()`) |
| **OCR Recognition Latency** | $< 150\text{ ms}$ | $250\text{ ms}$ | Worker round-trip benchmark |
| **Math AST & Evaluation** | $< 0.1\text{ ms}$ | $1.0\text{ ms}$ | Pure CPU execution time |
| **Spatial Jitter** | $< 2\text{ px RMS}$ | $4\text{ px RMS}$ | Centroid drift under mechanical mount |
| **Memory Allocation** | $< 120\text{ MB}$ | $180\text{ MB}$ | Resident heap profiling over 15 min |

---

## 3. Memory Architecture & Zero-GC Invariants

Frequent object allocation inside 30-60 FPS render loops triggers Garbage Collection (GC) pauses, causing visible frame drops. CalcLens enforces:

1. **Object Pooling**:
   * Pre-allocated pools for bounding boxes, vectors, and token structures.
   * Entities are recycled via `reset()` rather than discarded for garbage collection.
2. **Typed Array Buffers**:
   * Video frame pixel data and transform matrices utilize pre-allocated `Uint8ClampedArray` and `Float32Array` buffers.
3. **No Closure Allocations in Loops**:
   * Iterators and callback closures are avoided in high-frequency tracking loops; standard bounded `for` loops are mandated.

---

## 4. Power & Thermal Management

Continuous camera sensor and neural inference execution generates heat and drains mobile batteries. CalcLens employs adaptive duty cycling:

### 4.1 Motion-Gated Recognition
* Optical flow and device IMU (accelerometer/gyroscope) monitor device velocity.
* When angular velocity $> 45^\circ/\text{s}$, OCR inference is temporarily suspended because frames are blurred and targets are in transit.
* Recognition resumes immediately once velocity falls below $15^\circ/\text{s}$.

### 4.2 Thermal State Throttling
When device thermal state rises:
* **Nominal**: OCR runs at $4\text{ Hz}$. Tracking at $60\text{ Hz}$.
* **Warm**: OCR runs at $2.5\text{ Hz}$. Tracking at $30\text{ Hz}$.
* **Hot**: OCR runs at $1.5\text{ Hz}$. Camera sensor switches to 720p stream.
