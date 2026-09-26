# CalcLens Product Vision

## 1. Executive Summary

CalcLens is a camera-first mathematical interface that transforms the physical environment into an interactive computational canvas. By pointing a camera at printed arithmetic expressions, computed answers appear directly overlaid on the physical paper or surface in real time.

CalcLens rejects the traditional utility workflow:
* No photo capture step
* No upload screen or progress spinner
* No modal dialogs
* No manual keyboard entry

The camera preview remains continuously active. Math is detected, verified, evaluated, and tracked in place with zero user-initiated capture friction.

---

## 2. The Core Problem & Philosophy

### 2.1 The Friction of Conventional Tools

Calculators and educational apps typically enforce high cognitive overhead:
1. **Manual calculators**: Require transcribing numbers and operators from paper into digital buttons, introducing transcription errors and mental friction.
2. **"Scan and Solve" apps**: Treat the camera as a flatbed document scanner. The user must aim, tap a shutter button, crop a rectangle, wait for cloud upload, read an advertisement, and review a static result card on a new screen.

### 2.2 The CalcLens Paradigm: Augmented Spatial Computation

CalcLens treats the camera viewport as an augmented window onto reality. Mathematics is not extracted away from its context; instead, computation is projected back onto the physical world.

```text
Physical Page               CalcLens Camera Feed
┌─────────────────┐         ┌────────────────────────┐
│  27 × 14        │   ──▶   │  27 × 14               │
│                 │         │     [ 378 ]            │
│                 │         │                        │
│  125 + 87       │         │  125 + 87              │
│                 │         │     [ 212 ]            │
└─────────────────┘         └────────────────────────┘
```

The physical environment is the direct input.

---

## 3. The Cardinal Architectural Rule

**Vision systems must never calculate arithmetic.**

```text
Incorrect Paradigm (Hallucination-Prone):
Camera Frame ──▶ Vision / Generative AI ──▶ "378"

Correct Paradigm (Deterministic & Auditable):
Camera Frame ──▶ Vision Recognition ──▶ "27 × 14" ──▶ Parser ──▶ AST ──▶ Evaluator ──▶ 378
```

1. **Vision answers:** *"What characters and symbols exist at what coordinates with what confidence?"*
2. **Grammar & Parser answers:** *"Do these tokens form a syntactically valid mathematical expression?"*
3. **Math Engine answers:** *"What is the mathematically verified result?"*

Under no circumstances may an OCR engine, neural network, or large language model be tasked with evaluating arithmetic results directly. Arithmetic evaluation must remain deterministic, verifiable, and isolated from visual heuristics.

---

## 4. The Continuous Real-Time Loop

```text
              ┌───────────────┐
              │  Camera Feed  │
              └───────┬───────┘
                      │
                      ▼
           ┌─────────────────────┐
           │   Frame Scheduler   │
           └──────────┬──────────┘
                      │
         ┌────────────┴────────────┐
         │                         │
         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│ Vision Pipeline  │      │ Tracking Engine  │
│ (2 - 5 Hz)       │      │ (30 - 60 Hz)     │
└────────┬─────────┘      └────────┬─────────┘
         │                         │
         ▼                         │
┌──────────────────┐               │
│  Math Normalizer │               │
│  & AST Parser    │               │
└────────┬─────────┘               │
         │                         │
         ▼                         │
┌──────────────────┐               │
│  Deterministic   │               │
│  Math Evaluator  │               │
└────────┬─────────┘               │
         │                         │
         ▼                         ▼
┌────────────────────────────────────────────┐
│      Spatial Coordinate Transformer        │
└─────────────────────┬──────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────┐
│       Overlay Rendering Surface            │
│       (Anchored Answers & Feedback)        │
└────────────────────────────────────────────┘
```

The split between high-frequency tracking (smooth movement at camera frame rate) and lower-frequency recognition (intermittent OCR) ensures responsive tracking without draining the device battery or creating thermal bottlenecks.

---

## 5. Visual Mathematics Evolution

CalcLens begins with arithmetic and systematically expands into higher-order mathematics across major versions:

```text
                    CalcLens Platform
                            │
       ┌────────────────────┼────────────────────┐
       ▼                    ▼                    ▼
   Version 1            Version 2            Version 3
   Core Arithmetic      Extended Notation    Spatial Handwriting
   (0-9, +, -, x, /)    (Decimals, Negatives, (Multi-line fractions,
                         Parentheses, Powers,  stacked equations)
                         Roots, Percentages)
                            │
                            ▼
                        Version 4
                        Structural Math
                        (Algebra, Geometry, Word Problems)
```

### Version 1 (MVP)
* Printed single-line arithmetic.
* Basic operations: addition (`+`), subtraction (`-`, `−`), multiplication (`*`, `x`, `×`), division (`/`, `÷`).
* Integer numbers.
* Zero cloud dependence (100% on-device).

### Version 2 (Extended Notation)
* Decimals, explicit negative numbers, and parenthetical sub-expressions.
* Exponents, square roots, and basic percentages.
* Order of operations verification across complex chains.

### Version 3 (Handwriting & Spatial Structures)
* Spatial math layouts: stacked multiplication, vertical addition, two-dimensional fraction bars (`numerator / denominator`).
* Non-linear bounding geometry and character stroke clustering.

### Version 4 (Algebra & Symbolic Reasoning)
* Single-variable linear equations (`2x + 5 = 15`).
* Geometric shape labeling (angle calculation, perimeter, area overlays).
* Step-by-step resolution toggles.

---

## 6. Privacy & Trust Principles

A camera feed captures personal spaces, confidential documents, and private surroundings. CalcLens adheres to a zero-compromise privacy posture:

1. **Local-First Execution**: Optical character recognition, expression parsing, mathematical evaluation, and tracking run entirely on-device by default.
2. **Zero Media Retention**: Camera frames exist solely in volatile GPU/CPU memory buffers for processing and are discarded immediately upon frame cycle completion. Frames are never written to persistent local storage or uploaded.
3. **No Account Requirement**: CalcLens requires no user registration, email address, external authentication provider, or cloud profile for core functionality.
4. **Transparent Boundaries**: If cloud-assisted models are ever introduced in future versions for complex structural reasoning, cloud processing must require explicit user opt-in, clear visual indicators, and minimal bounding-box cropping.

---

## 7. Performance & Quality Benchmarks

To achieve the sensation of an augmented physical calculator, CalcLens enforces strict latency and stability metrics:

| Metric | Target | Hard Limit | Rationale |
| :--- | :--- | :--- | :--- |
| Camera Preview Rate | >= 30 FPS | >= 24 FPS | Smooth visual continuity |
| Overlay Render Rate | >= 30 FPS | >= 24 FPS | Lock-step rendering with camera frames |
| Math Engine Latency | < 1 ms | < 5 ms | Zero perceived computation lag |
| First Recognition Latency | <= 200 ms | <= 400 ms | Instantaneous appearance on target acquisition |
| Tracking Jitter | < 2 px RMS | < 4 px RMS | Rock-solid visual anchoring over printed text |
| Memory Overhead | < 120 MB | < 200 MB | Sustainable continuous background execution |

---

## 8. Anti-Goals: What CalcLens Is Not

To maintain clarity and protect the core user experience, CalcLens explicitly rejects:
* **No Social Features**: No profiles, friend feeds, sharing networks, or badges.
* **No Advertising or Monetization Interruptions**: No pre-roll ads, full-screen popups, or interstitial barriers.
* **No Mandatory Cloud Sync**: Calculations do not require database persistence or network round trips.
* **No Conversational AI Chat**: The primary interface is visual augmentation, not conversational Q&A.
* **No Cluttered Utility Dashboards**: The application launches directly into the camera lens with immediate operational readiness.
