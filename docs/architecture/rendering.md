# CalcLens Overlay Rendering Architecture

## 1. Rendering Architecture Overview

The Overlay Renderer is responsible for projecting computed mathematical answers, diagnostic indicators, and spatial reticles directly over the live camera preview. It operates as a GPU-accelerated drawing layer (HTML5 Canvas 2D / WebGL / Native Metal/OpenGL surface) synchronized to display refresh rates ($\ge 30\text{ to }60\text{ FPS}$).

```text
Tracked Detections Map
         │
         ▼
[Coordinate Transformation Engine]
(Camera Sensor Coordinates ──▶ Physical Viewport Pixels)
         │
         ▼
[Collision Avoidance & Spatial Layout Manager]
         │
         ▼
[State-Driven Badge Staging (Alpha, Pulse, Color)]
         │
         ▼
[Hardware-Accelerated Canvas / GPU Draw Call]
```

---

## 2. Centralized Coordinate Transformation Engine

Sensor frame dimensions (e.g., $1920 \times 1080$) rarely match device screen geometries (e.g., $1170 \times 2532$). Scattering coordinate calculations across components introduces orientation and scaling bugs. All conversions must pass through `CoordinateTransformer`:

### 2.1 Transformation Pipeline
1. **Normalized Sensor Space**: The vision pipeline outputs coordinates normalized between $0.0$ and $1.0$:
   $$u = \frac{x_{\text{sensor}}}{W_{\text{sensor}}}, \quad v = \frac{y_{\text{sensor}}}{H_{\text{sensor}}}$$
2. **AspectFill Calculation**: To fill the viewport without geometric distortion, the scale factor $S$ and crop offsets $(O_x, O_y)$ are computed:
   $$S = \max\left(\frac{W_{\text{viewport}}}{W_{\text{sensor}}}, \frac{H_{\text{viewport}}}{H_{\text{sensor}}}\right)$$
   $$O_x = \frac{(W_{\text{sensor}} \times S) - W_{\text{viewport}}}{2}, \quad O_y = \frac{(H_{\text{sensor}} \times S) - H_{\text{viewport}}}{2}$$
3. **Viewport Pixel Mapping**:
   $$x_{\text{screen}} = (u \times W_{\text{sensor}} \times S) - O_x$$
   $$y_{\text{screen}} = (v \times H_{\text{sensor}} \times S) - O_y$$

---

## 3. Spatial Badge Layout & Placement Rules

### 3.1 Non-Obstructive Placement
The overlay must never obscure the physical equation written on paper.
* **Primary Placement**: Directly below the bottom edge of the expression bounding box with an $8\text{ px}$ vertical margin.
* **Boundary Fallback**: If the bottom placement clips the screen viewport footer, the badge flips to the right or above the expression.

```text
Physical Expression Bounding Box
┌────────────────────────┐
│        27 × 14         │
└────────────────────────┘
            │
            ▼ 8px Margin
     ┌──────────────┐
     │    [ 378 ]   │  ──▶ High-contrast answer pill
     └──────────────┘
```

### 3.2 Multi-Badge Collision Avoidance
When multiple equations sit in close proximity (e.g. columns on a worksheet):
* The layout manager computes overlapping bounding rectangles of proposed badge positions.
* A vertical or horizontal nudge algorithm repositions adjacent badges by their bounding height plus padding, avoiding visual overlap.

---

## 4. Visual Styling & Component Specification

CalcLens uses a modern, high-contrast, distraction-free aesthetic:

* **Answer Badge Container**:
  * Dark frosted glass (`rgba(15, 23, 42, 0.85)` / `backdrop-filter: blur(12px)`).
  * Border: $1\text{ px}$ subtle stroke (`rgba(255, 255, 255, 0.15)`).
  * Corner Radius: Fully rounded pill (`9999px`) or compact badge (`8px`).
* **Typography**:
  * Font Family: Modern geometric monospace / sans-serif (e.g. JetBrains Mono, Inter).
  * Color: Crisp high-contrast white (`#FFFFFF`) with accent tinting for verified operations.
  * Weight: Semi-bold (`600`).
* **State Badges**:
  * High-Confidence Answer: `[ 378 ]` in solid white text with subtle green focus ring.
  * Parsing / Uncertain: `[ ... ]` with gentle breathing opacity animation ($0.5 \leftrightarrow 0.9$).
  * Error (Division by Zero): `[ Undefined ]` with soft amber/red stroke.

---

## 5. Performance Invariants

* **Draw Call Budget**: Sub-frame render execution must finish within $\le 2\text{ ms}$ on mobile GPUs.
* **Zero Garbage Collection**: Pre-allocated path buffers and geometry caches prevent allocation churn during render loops.
