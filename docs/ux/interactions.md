# CalcLens UX Interactions & Gestures

## 1. Interaction Principles

CalcLens is fundamentally a **hands-free** interface: the primary action is aiming the camera. However, intuitive micro-interactions and gestures empower users to interact with projected mathematical entities without breaking immersion.

```text
Aim Camera ──▶ Answer Appears Automatically (Zero Tap Required)
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
      [Tap]     [Long Press]  [Pinch]
   Copy Result   Pin Badge     Zoom Camera
```

---

## 2. Touch Gestures & Actions

### 2.1 Tap on Answer Badge
* **Action**: Single tap on a floating answer badge (e.g. `[ 378 ]`).
* **Behavior**:
  1. Triggers a light haptic tap.
  2. Copies the numerical result (`378`) or equation string (`27 × 14 = 378`) to the device clipboard.
  3. Badge displays a temporary confirmation checkmark: `[ 378 Copied ]` for $1.2\text{ seconds}$.

### 2.2 Long Press on Answer Badge (Pinning)
* **Action**: Press and hold an answer badge for $\ge 400\text{ ms}$.
* **Behavior**:
  1. The badge enters **Pinned Mode**.
  2. A small pin icon appears on the badge.
  3. The answer remains anchored to the viewport even if the camera pans away from the original physical paper.
  4. Allows users to save intermediate results while referencing other sections of a worksheet.

### 2.3 Pinch-to-Zoom
* **Action**: Two-finger pinch / spread on the camera viewfinder.
* **Behavior**:
  1. Controls smooth digital or optical camera zoom from $1.0\times$ to $5.0\times$.
  2. Essential for recognizing printed math on distant classroom blackboards or whiteboards.
  3. Coordinate transformer automatically accounts for zoom scaling factors in real time.

### 2.4 Double Tap Viewfinder
* **Action**: Double tap anywhere in empty camera preview area.
* **Behavior**:
  1. Toggles device torch/flashlight on or off to illuminate low-light reading environments.

---

## 3. Haptic & Sensory Feedback

Haptic feedback establishes tactile confidence without requiring the user to look away from their physical notebook:

* **Equation Solved**: Light, crisp haptic impact ($15\text{ ms}$) when an equation reaches `DISPLAYED` status.
* **Copy to Clipboard**: Subtle double-tap haptic pulse.
* **Math Error (Division by Zero)**: Soft cautionary rumble ($40\text{ ms}$).
* **User Control**: Haptic feedback can be toggled on/off in the Settings drawer.
