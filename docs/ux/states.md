# CalcLens UX States & Visual Feedback

## 1. System-Level Lifecycle States

```text
[UNINITIALIZED] ──▶ [CHECKING_PERMISSIONS]
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
    [PERMISSION_REQUIRED]        [STREAM_INITIALIZING]
             │                           │
             ▼                           ▼
    [PERMANENTLY_DENIED]         [ACTIVE_STREAMING]
                                         │
                                   ┌─────┴─────┐
                                   ▼           ▼
                            [BACKGROUNDED]  [ERROR_FAULT]
```

### State Specifications
* **`ACTIVE_STREAMING`**: Camera sensor running at target FPS, frame scheduler actively dispatching tracking and recognition tasks.
* **`BACKGROUNDED`**: Camera stream immediately halted, video buffers cleared, tracking state preserved in memory for rapid resumption.
* **`ERROR_FAULT`**: Unrecoverable hardware or camera failure (e.g. sensor occupied by another app); displays friendly reset action.

---

## 2. Mathematical Entity UX States

Every detected equation is rendered according to its recognition lifecycle:

| State | Visual Treatment | Animation / Behavior |
| :--- | :--- | :--- |
| **`DISCOVERED`** | Invisible to user. | Internal memory allocation; candidate evaluates confidence. |
| **`VERIFYING`** | Subtle pulsing bounding dot or subtle reticle (optional). | Held in buffer across $\ge 2$ consecutive frames. |
| **`STABLE`** | Answer pill fades in over $120\text{ ms}$. | Smooth cubic-bezier entry animation. |
| **`DISPLAYED`** | Crisp high-contrast frosted badge with calculated answer. | Rigidly anchored adjacent to equation. |
| **`TRACKING`** | Sub-pixel coordinate translation in lockstep with frame delta. | Low-pass filter active ($< 2\text{ px}$ jitter). |
| **`LOST`** | Badge remains in last verified position with decaying opacity ($1.0 \to 0.0$ over $500\text{ ms}$). | Prevents abrupt visual disappearance on brief occlusion. |

---

## 3. Mathematical Result States & Visual Badges

### 3.1 Valid Calculation (High Confidence)
* **Appearance**: Frosted dark glass container with pure white typography.
* **Format**: `[ 378 ]`
* **Style**: Semi-bold, green accent pulse on initial reveal.

### 3.2 Uncertain / Parsing
* **Appearance**: Muted glass container with three breathing dots.
* **Format**: `[ ... ]`
* **Trigger**: Expression partially occluded or confidence score between $0.60$ and $0.75$.

### 3.3 Division by Zero
* **Appearance**: Amber/crimson tinted glass badge.
* **Format**: `[ Undefined ]`
* **Trigger**: Division operator with zero divisor (`a ÷ 0`). Fails gracefully and visibly.

### 3.4 Incomplete Expression
* **Appearance**: Suppressed or minimal hint dot.
* **Format**: Suppressed by default. If held in frame for $> 1.5\text{ s}$, shows `[ Incomplete ]`.
* **Trigger**: Missing right-hand operand (e.g. `27 × `).

### 3.5 Numeric Overflow
* **Appearance**: Warning badge.
* **Format**: `[ Overflow ]`
* **Trigger**: Exceeds safe 64-bit float/integer limits.
