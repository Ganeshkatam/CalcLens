# CalcLens System Architecture

## 1. High-Level Architecture Overview

CalcLens decouples visual perception from mathematical computation. The system is designed around a continuous, non-blocking pipeline where high-frequency spatial tracking operates in parallel with intermittent, asynchronous mathematical recognition.

```text
                                  ┌──────────────────────────┐
                                  │   Camera Sensor Buffer   │
                                  └─────────────┬────────────┘
                                                │ YUV / RGBA Frame (>= 30 FPS)
                                                ▼
                                  ┌──────────────────────────┐
                                  │     Frame Scheduler      │
                                  └──────┬────────────┬──────┘
                                         │            │
             High-frequency stream       │            │ Lower-frequency capture
             (30 - 60 Hz)                │            │ (2 - 5 Hz on motion / interval)
                                         ▼            ▼
                             ┌───────────────┐   ┌──────────────────────────┐
                             │    Tracking   │   │     Vision Pipeline      │
                             │   Subsystem   │   │  (Detection, OCR, BBox)  │
                             └───────┬───────┘   └────────────┬─────────────┘
                                     │                        │
                                     │                        ▼
                                     │           ┌──────────────────────────┐
                                     │           │   Expression Normalizer  │
                                     │           └────────────┬─────────────┘
                                     │                        │
                                     │                        ▼
                                     │           ┌──────────────────────────┐
                                     │           │ Recursive-Descent Parser │
                                     │           └────────────┬─────────────┘
                                     │                        │
                                     │                        ▼
                                     │           ┌──────────────────────────┐
                                     │           │ Deterministic Evaluator  │
                                     │           └────────────┬─────────────┘
                                     │                        │
                                     └───────────┬────────────┘
                                                 │ State updates & Tracked BBoxes
                                                 ▼
                                  ┌──────────────────────────┐
                                  │ Detection State Manager  │
                                  └──────────────┬───────────┘
                                                 │
                                                 ▼
                                  ┌──────────────────────────┐
                                  │ Coordinate Transformer   │
                                  │ (Camera -> Viewport)     │
                                  └──────────────┬───────────┘
                                                 │
                                                 ▼
                                  ┌──────────────────────────┐
                                  │  Overlay GPU/Canvas UI   │
                                  └──────────────────────────┘
```

---

## 2. Core Architectural Principles & Invariants

### 2.1 The Cardinal Invariant: Separation of Vision and Arithmetic
Vision systems (neural networks, OCR engines, pattern recognizers) are heuristic and probabilistic. Mathematical evaluation must be formal, deterministic, and exact. Under no circumstances may visual models or neural networks predict the numerical answer.

### 2.2 Dual-Rate Frame Scheduling
Processing every raw 30 FPS camera frame through text recognition exhausts device battery and induces thermal throttling. CalcLens solves this by splitting workloads:
* **Perception Loop (Lower-frequency: 2 to 5 Hz)**: Analyzes video frames to detect new text regions, transcribe mathematical glyphs, and verify syntax.
* **Tracking Loop (High-frequency: 30 to 60 Hz)**: Tracks known bounding box centroids using lightweight optical feature tracking or bounding box motion vectors.

### 2.3 Single Source of Truth for Spatial Transformations
Camera sensor buffers often feature different resolutions, coordinate origins, and aspect ratios compared to mobile screen viewports (e.g. 1920x1080 sensor vs 1170x2532 display). All coordinate conversions are isolated within a dedicated, centralized `CoordinateTransformer` module to eliminate spatial drift and projection misalignment.

---

## 3. Module Boundaries & Data Contracts

```text
[CameraLayer] ──(CameraFrame)──▶ [FrameScheduler]
                                         │
        ┌────────────────────────────────┴───────────────────────────────┐
        ▼                                                                ▼
[VisionPipeline] ──(RawDetections)──▶ [Normalizer]             [TrackingPipeline]
                                              │                                  │
                                              ▼                                  │
                                       [MathParser]                              │
                                              │                                  │
                                              ▼                                  │
                                       [MathEvaluator]                           │
                                              │                                  │
                                              ▼                                  │
                                       [EvaluatedMath]                           │
                                              │                                  │
                                              └─────────────────┬────────────────┘
                                                                ▼
                                                   [DetectionStateManager]
                                                                │
                                                                ▼
                                                   [CoordinateTransformer]
                                                                │
                                                                ▼
                                                        [OverlayRenderer]
```

### 3.1 Data Model: CameraFrame
```typescript
interface CameraFrame {
  id: number;
  timestamp: number;
  width: number;
  height: number;
  orientation: 'portrait' | 'portrait-upside-down' | 'landscape-left' | 'landscape-right';
  buffer: ArrayBufferView; // GPU texture reference or raw pixel buffer
}
```

### 3.2 Data Model: BoundingBox
```typescript
interface BoundingBox {
  x: number;      // Normalized horizontal coordinate [0.0, 1.0]
  y: number;      // Normalized vertical coordinate [0.0, 1.0]
  width: number;  // Normalized width [0.0, 1.0]
  height: number; // Normalized height [0.0, 1.0]
}
```

### 3.3 Data Model: MathematicalDetection
```typescript
interface MathematicalDetection {
  id: string; // Unique entity identifier
  rawText: string;
  normalizedExpression: string;
  ast: ASTNode | null;
  evaluationResult: string | null;
  evaluationError: 'DIVISION_BY_ZERO' | 'SYNTAX_ERROR' | 'OVERFLOW' | null;
  boundingBox: BoundingBox;
  confidence: number;
  firstSeenTimestamp: number;
  lastSeenTimestamp: number;
  state: 'DISCOVERED' | 'VERIFYING' | 'STABLE' | 'DISPLAYED' | 'TRACKING' | 'LOST';
}
```

---

## 4. Subsystem Detailed Specifications

### 4.1 Frame Scheduler
The Frame Scheduler buffers incoming frames from the sensor driver. It measures device rotational velocity from hardware IMU sensors (accelerometer and gyroscope).
* When the device is in rapid transit ($> 45^\circ/\text{s}$ angular motion), vision recognition is paused to avoid processing motion-blurred frames.
* When motion stabilizes, frames are dispatched to OCR at a baseline period of $T = 250\text{ ms}$.

### 4.2 Detection State Manager
The state manager prevents screen flickering by maintaining spatial and temporal continuity across successive frames.
* **Association**: Associates newly detected bounding boxes with existing tracks using Intersection over Union (IoU $\ge 0.40$) and Levenshtein string similarity.
* **Hysteresis**: Ensures expressions are held across temporary occlusions up to $500\text{ ms}$ before garbage collection.

### 4.3 Coordinate Transformer
Encapsulates affine transforms, viewport scaling, orientation adjustments, and aspect-fill cropping. Normalizes sensor coordinates $(x_c, y_c) \in [0, 1]$ into physical screen pixels $(x_s, y_s)$ via:
$$x_s = (x_c \times S_x) - O_x, \quad y_s = (y_c \times S_y) - O_y$$
where $S$ represents scale factors and $O$ represents letterbox or crop offsets.

### 4.4 Overlay Renderer
A lightweight canvas or GPU-accelerated drawing surface overlaid directly on top of the native camera preview. Renders translucent pill containers with computed results, status indicators, and bounding reticles with sub-pixel alignment.
