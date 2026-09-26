# CalcLens

CalcLens is a camera-first mathematical interface that continuously recognizes arithmetic expressions in a live video feed and projects computed answers directly over the physical equations in real time.

CalcLens is not a conventional "photo-and-calculate" or "scan-and-wait" application. The camera preview remains active at full frame rate, recognition occurs continuously in the background, and results are spatially anchored to physical equations as the device moves.

---

## Core Invariant: Separation of Vision and Arithmetic

Vision systems must never perform mathematical computation.

* The **Vision Pipeline** is responsible exclusively for answering: *"What mathematical expression is visible, where is it located, and with what confidence?"*
* The **Math Engine** is responsible exclusively for answering: *"Given this structured mathematical expression, what is the exact, deterministic mathematical result?"*

By strictly enforcing this boundary, CalcLens guarantees mathematical accuracy and prevents arithmetic hallucinations.

---

## System Workflow Loop

```text
Camera Frame Stream (>= 30 FPS)
       │
       ▼
Frame Scheduler (High-freq Tracking vs. Low-freq Recognition)
       │
       ├─────────────────────────────────┐
       ▼                                 ▼
Vision Pipeline (OCR & Geometry)   Tracking Pipeline (Optical Flow / Feature Bounding)
       │                                 │
       ▼                                 │
Expression Normalizer                    │
       │                                 │
       ▼                                 │
Math Parser (Grammar -> AST)             │
       │                                 │
       ▼                                 │
Deterministic Evaluator                  │
       │                                 │
       ▼                                 ▼
Coordinate Transformer (Camera Space -> Screen Space)
       │
       ▼
Overlay Renderer (Real-time Spatial Result Projection)
```

---

## Repository Structure

```text
CalcLens/
├── README.md
├── docs/
│   ├── product/
│   │   ├── vision.md            # Product thesis, core interaction, and privacy
│   │   ├── requirements.md      # Functional, non-functional, and boundary requirements
│   │   ├── user-flows.md        # State machines, user journeys, and edge cases
│   │   └── roadmap.md           # Milestones M0 through M10 and multi-version roadmap
│   ├── architecture/
│   │   ├── system-architecture.md
│   │   ├── vision-pipeline.md
│   │   ├── math-engine.md
│   │   ├── tracking.md
│   │   └── rendering.md
│   ├── ux/
│   │   ├── screens.md
│   │   ├── states.md
│   │   └── interactions.md
│   └── engineering/
│       ├── performance.md
│       ├── security.md
│       ├── testing.md
│       └── release.md
├── app/                         # Application shells and platform entry points
├── src/
│   ├── camera/                  # Frame capture, device orientation, and stream control
│   ├── vision/                  # Region detection, OCR, and expression normalization
│   ├── math/                    # Lexer, recursive-descent parser, AST, and evaluator
│   ├── tracking/                # Temporal stabilization and spatial coordinate tracking
│   ├── overlay/                 # Canvas/GPU rendering and projection logic
│   └── state/                   # Recognition state machine and detection lifecycle
├── tests/
│   ├── math/                    # Unit tests for arithmetic evaluation
│   ├── parser/                  # Syntax trees, operator precedence, and invalid grammar
│   ├── vision/                  # Normalization, tokenization, and synthetic test frames
│   └── tracking/                # Coordinate projection, stability, and decay metrics
└── assets/                      # Test fixtures, benchmarks, and static resources
```

---

## Product Documentation Index

* [Vision & Core Thesis](file:///e:/CalcLens/docs/product/vision.md)
* [Requirements Specification](file:///e:/CalcLens/docs/product/requirements.md)
* [User Flows & State Transitions](file:///e:/CalcLens/docs/product/user-flows.md)
* [Development Roadmap](file:///e:/CalcLens/docs/product/roadmap.md)

---

## Development Milestones Summary

| Milestone | Scope | Key Deliverable |
| :--- | :--- | :--- |
| M0 | Specification | Complete product, architectural, and test documentation |
| M1 | Camera Prototype | High-framerate frame acquisition and lifecycle management |
| M2 | Overlay Prototype | Coordinate transform pipeline and low-jitter overlay canvas |
| M3 | Math Engine | AST-based deterministic arithmetic evaluator with unit test coverage |
| M4 | Vision Recognition | On-device text detection, bounding extraction, and normalization |
| M5 | Real-Time Result Overlay | First complete loop: live camera to spatially rendered answer |
| M6 | Tracking & Stabilization | Optical flow / bounding tracking and temporal confidence filtering |
| M7 | Multi-Expression Support | Independent tracking and rendering of concurrent equations |
| M8 | Performance Optimization | Frame scheduler tuning, latency minimization, and battery profiling |
| M9 | Device Benchmark Testing | Low-, mid-, and high-end hardware validation |
| M10 | MVP Launch | Polished, privacy-first, on-device arithmetic lens |
