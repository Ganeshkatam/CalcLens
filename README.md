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

## Technology Stack

| Layer | Choice |
| :--- | :--- |
| **Platform** | Native Android (Min SDK 26, Target SDK 34) |
| **Language** | Kotlin 1.9+ |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Camera Feed** | AndroidX CameraX (Preview + ImageAnalysis) |
| **Vision / OCR** | Google ML Kit Text Recognition (On-Device) |
| **Math Engine** | Pure Kotlin Deterministic Lexer, Parser & Evaluator |
| **Tracking** | Spatial IoU Correlation + Adaptive EMA Jitter Smoothing |
| **Build Tooling** | Gradle 8.7 (Kotlin DSL) |

---

## Repository Structure

```text
CalcLens/
├── README.md
├── build.gradle.kts             # Root Gradle build configuration
├── settings.gradle.kts          # Module and repository definitions
├── gradle.properties            # JVM parameters and AndroidX flags
├── gradlew / gradlew.bat        # Gradle wrappers for Linux/macOS and Windows
├── docs/                        # Complete Phase M0 specification suite
│   ├── product/                 # Vision, requirements, user flows, and roadmap
│   ├── architecture/            # System architecture, vision, math, tracking, rendering
│   ├── ux/                      # Screens, states, and touch interactions
│   └── engineering/             # Performance, security, testing, and release criteria
└── app/                         # Native Android Application Module
    ├── build.gradle.kts         # CameraX, ML Kit, and Jetpack Compose dependencies
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/calclens/
        │   │   ├── MainActivity.kt
        │   │   ├── camera/      # CameraManager, FrameScheduler
        │   │   ├── vision/      # TextRecognitionAnalyzer, ExpressionNormalizer, MathRegionFilter
        │   │   ├── math/        # Token, ASTNode, MathLexer, MathParser, MathEvaluator, MathEngine
        │   │   ├── tracking/    # TrackedEquation, SpatialTracker
        │   │   ├── overlay/     # CoordinateTransformer, CollisionAvoidance, BadgeLayout
        │   │   └── ui/          # CalcLensScreen, Theme, Color
        │   └── res/values/      # Strings, colors
        └── test/java/com/calclens/math/
            ├── MathEngineTest.kt
            └── ExpressionNormalizerTest.kt
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
