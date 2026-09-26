# CalcLens Vision Pipeline

## 1. Vision Architecture Overview

The Vision Pipeline is responsible for identifying, transcribing, and bounding printed mathematical expressions within incoming camera frames. It bridges the gap between raw pixel buffers and structured textual mathematics.

```text
Camera Frame (RGB/YUV)
         │
         ▼
[Stage 1: Pre-processing & Grayscale Conversion]
         │
         ▼
[Stage 2: Text Region Proposal & Bounding]
         │
         ▼
[Stage 3: Optical Character Recognition (OCR)]
         │
         ▼
[Stage 4: Mathematical Filtering & Token Extraction]
         │
         ▼
[Stage 5: Expression Normalization & Sanitization]
         │
         ▼
Normalized Expression Candidate + Bounding Box
```

---

## 2. Pipeline Stages

### Stage 1: Frame Pre-Processing
1. **Luminance Extraction**: Incoming RGBA/YUV frames are converted to single-channel 8-bit grayscale to eliminate chrominance noise and reduce memory bandwidth.
2. **Contrast Normalization**: Adaptive histogram equalization or local thresholding is applied to normalize lighting gradients caused by shadows, uneven room illumination, or flash reflection.
3. **Downscaling for Region Proposal**: High-resolution frames (1080p) are downscaled to 540p for initial bounding box localization, conserving compute cycles.

### Stage 2: Mathematical Region Proposal
* Text lines are segmented using connected component analysis or horizontal projection profiling.
* Regions whose aspect ratio, height, or character count violate mathematical bounds (e.g. single isolated periods or sprawling paragraphs of natural language) are filtered out early.

### Stage 3: Character & Symbol Recognition
The optical recognition model extracts text sequences and character confidence scores from isolated regions.

Supported Symbol Taxonomy (Version 1.0):
* **Digits**: `0`, `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`
* **Addition**: `+`
* **Subtraction**: `-`, `−`, `–`
* **Multiplication**: `*`, `×`, `x`, `X`, `•`
* **Division**: `/`, `÷`, `:`

### Stage 4: Mathematical Filtering
Candidate text strings are scanned for mathematical viability before reaching the parser:
* Must contain at least one valid arithmetic operator.
* Must contain at least two numeric operands.
* Must contain no unrecognized alphabetical characters outside of multiplication glyphs (`x`/`X`).

### Stage 5: Confidence Calculation
Each candidate expression is assigned an aggregate confidence score $C_{\text{expr}}$:
$$C_{\text{expr}} = \min_{i} (c_i)^{0.3} \times \left( \frac{1}{N} \sum_{i=1}^{N} c_i \right)^{0.7}$$
where $c_i$ is the OCR confidence of individual glyph $i$. If $C_{\text{expr}} < 0.70$, the candidate is dropped immediately.

---

## 3. Expression Normalization

Raw OCR strings frequently exhibit visual variations, spacing irregularities, and character misidentifications. The Expression Normalizer applies strict deterministic transformations:

### 3.1 Operator Canonicalization
| Input Variants | Canonical Token | Representation |
| :--- | :--- | :--- |
| `+`, `＋` | `+` | Addition |
| `-`, `−`, `–`, `—` | `-` | Subtraction |
| `*`, `×`, `x`, `X`, `•` | `*` | Multiplication |
| `/`, `÷`, `:` | `/` | Division |

### 3.2 Contextual OCR Disambiguation
In mathematical contexts flanked by digits or arithmetic operators:
* Letter `O` or `o` is mapped to digit `0`.
* Letter `l` (lowercase L) or `I` (uppercase i) or `|` (pipe) is mapped to digit `1`.
* Letter `S` or `s` flanked by digits is mapped to digit `5`.
* Letter `B` flanked by digits is mapped to digit `8`.

### 3.3 Whitespace Normalization
All interior whitespace (spaces, tabs, non-breaking spaces) is sanitized:
* Leading and trailing spaces are trimmed.
* Multi-space runs between operands and operators are collapsed to single spaces:
  ```text
  "27    x    14"  ──▶  "27 * 14"
  ```

---

## 4. Concurrency & Performance Strategy

* **Off-Main-Thread Execution**: All OCR operations run inside background Web Workers, Native Threads, or GPU compute shaders. The camera preview never waits on OCR completion.
* **Throttled Dispatch**: New frames are submitted to the vision worker only when the prior recognition pass has resolved, preventing task queuing and memory accumulation.
* **Target Execution Latency**: Frame capture to normalized expression candidate must complete within $\le 150\text{ ms}$.
