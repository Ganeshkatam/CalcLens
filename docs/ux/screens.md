# CalcLens Screen Specifications

## 1. Overview

CalcLens adheres to a zero-friction, camera-first screen architecture. There are no secondary tab bars, photo review screens, or modal calculation pages. The application launches immediately into the camera viewfinder.

---

## 2. Screen 1: The Primary Lens (Live Viewfinder)

The primary surface where all user interaction occurs.

```text
┌────────────────────────────────────────────────────────┐
│ [?] Help                [⚡] Flash         [⚙] Info     │ ──▶ Minimalist Top Header
│                                                        │
│                                                        │
│                    27 × 14                             │
│                   ┌─────────┐                          │
│                   │   378   │                          │ ──▶ Anchored Answer Overlay
│                   └─────────┘                          │
│                                                        │
│                                                        │
│           125 + 87                                     │
│          ┌─────────┐                                   │
│          │   212   │                                   │ ──▶ Concurrent Answer Overlay
│          └─────────┘                                   │
│                                                        │
│                                                        │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │  Point at printed math (e.g. 27 × 14)              │ │ ──▶ Dynamic Status / Guidance Pill
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

### Component Hierarchy
1. **Camera Background Surface**: Fullscreen, zero-margin hardware video feed running at $\ge 30\text{ FPS}$.
2. **Overlay Drawing Canvas**: High-framerate transparent canvas aligned to screen bounds.
3. **Floating Top Bar**:
   * **Torch/Flash Toggle**: Enables device flash in low-light environments.
   * **Info/Settings Toggle**: Opens non-modal diagnostic drawer.
   * **Help Modal Trigger**: Shows quick gesture guide.
4. **Bottom Guidance Pill**: Non-intrusive contextual hint that dissolves once expressions are acquired.

---

## 3. Screen 2: Camera Permission Onboarding

Displayed only on fresh installations or when camera permissions are not yet granted.

```text
┌────────────────────────────────────────────────────────┐
│                                                        │
│                                                        │
│                      [ Camera Icon ]                   │
│                                                        │
│                    Camera Access Needed                │
│                                                        │
│      CalcLens needs camera access to recognize and     │
│      solve printed arithmetic directly in your         │
│      physical surroundings.                            │
│                                                        │
│      Zero video or imagery ever leaves your device.    │
│                                                        │
│              ┌───────────────────────────┐             │
│              │       Enable Camera       │             │
│              └───────────────────────────┘             │
│                                                        │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### Interactive States
* **Default**: Explains permission necessity and privacy guarantee.
* **Denied State**: Changes button label to **"Open Device Settings"** and provides step-by-step instructions if permanently denied.

---

## 4. Screen 3: Settings & Diagnostic Drawer

A slide-over drawer accessible from the top bar for technical inspection and configuration.

```text
┌────────────────────────────────────────────────────────┐
│ Settings & Diagnostics                             [X] │
├────────────────────────────────────────────────────────┤
│ PREFERENCES                                            │
│  Haptic Feedback on Solve                      [ ON ]  │
│  Sound Effect on Solve                        [ OFF ]  │
│  Keep Screen Awake                             [ ON ]  │
├────────────────────────────────────────────────────────┤
│ DIAGNOSTICS & TELEMETRY                                │
│  Show Bounding Box Reticles                   [ OFF ]  │
│  Display Performance Overlay                  [ OFF ]  │
│                                                        │
│  Camera Preview FPS: 30.1                              │
│  Overlay Render FPS: 60.0                              │
│  Recognition Latency: 142 ms                           │
│  Math Evaluation Time: 0.08 ms                         │
│  Active Tracking Anchors: 2                            │
├────────────────────────────────────────────────────────┤
│ ABOUT                                                  │
│  CalcLens Version 1.0.0 (MVP)                          │
│  100% On-Device Processing                             │
└────────────────────────────────────────────────────────┘
```
