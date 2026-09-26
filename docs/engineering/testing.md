# CalcLens Testing & Quality Assurance

## 1. Testing Philosophy & Test Pyramid

CalcLens enforces high test coverage with strict boundary isolation. Pure domain logic (mathematical parsing and arithmetic evaluation) is tested completely independent of hardware or UI frameworks.

```text
                  ▲
                 / \
                /   \     E2E Synthetic Frame Tests
               / E2E \    (Mock Video Stream ──▶ Answer Pill)
              /───────\
             /         \   Tracking & Transform Tests
            / Integrat. \  (IoU, Coordinate Projection, State Transitions)
           /─────────────\
          /               \ Unit Tests
         /   Unit Tests    \ (Math Engine, Lexer, Parser, Normalizer)
        /───────────────────\ (100% Deterministic Coverage)
```

---

## 2. Test Suites

### 2.1 Math Engine Unit Tests
* **Precedence Tests**:
  * `2 + 3 * 4` evaluates to `14` (not `20`).
  * `10 - 4 - 2` evaluates to `4` (left-associative: `(10 - 4) - 2`).
  * `(2 + 3) * 4` evaluates to `20`.
* **Basic Operations**:
  * Addition: `0 + 0 = 0`, `125 + 87 = 212`.
  * Subtraction: `25 - 8 = 17`, `10 - 20 = -10`.
  * Multiplication: `27 * 14 = 378`, `0 * 100 = 0`.
  * Division: `144 / 12 = 12`, `10 / 4 = 2.5`.
* **Edge Cases & Errors**:
  * Division by zero: `100 / 0` throws `DIVISION_BY_ZERO`.
  * Malformed syntax: `27 + `, `* 14`, `5 ++ 2` throw `SYNTAX_ERROR`.
  * Numeric overflow: Extremely large values trigger bounded `OVERFLOW` exceptions.

### 2.2 Expression Normalizer Unit Tests
* **Operator Sanitization**:
  * `"27 x 14"`, `"27 X 14"`, `"27 × 14"`, `"27 * 14"` all normalize to `"27 * 14"`.
  * `"81 ÷ 9"`, `"81 / 9"`, `"81 : 9"` all normalize to `"81 / 9"`.
  * `"25 − 8"` (Unicode minus) normalizes to `"25 - 8"`.
* **Digit Ambiguity Sanitization**:
  * `"2O + 14"` (letter O) normalizes to `"20 + 14"`.
  * `"l2 + 8"` (lowercase L) normalizes to `"12 + 8"`.
* **Whitespace Sanitization**:
  * `"  125   +   87  "` normalizes to `"125 + 87"`.

### 2.3 Coordinate Transformation Tests
* **AspectFill Geometry**:
  * Verifies sensor coordinate $(0.5, 0.5)$ maps to exact viewport center $(W/2, H/2)$ across 16:9, 19.5:9, and 4:3 screen aspect ratios.
  * Validates offset subtraction preventing horizontal/vertical visual drift.

### 2.4 Tracking & State Machine Tests
* **State Progression**:
  * Validates candidate transitions: `DISCOVERED -> VERIFYING -> STABLE -> DISPLAYED -> TRACKING -> LOST`.
* **Anti-Flicker Verification**:
  * Guarantees that single-frame corruptions (e.g. `27 * 14` -> `27 * 1A` for 1 frame) do not clear or alter active displays.
* **Grace Period Timing**:
  * Confirms an active track survives $400\text{ ms}$ occlusion without deletion, but purges after $> 500\text{ ms}$.

---

## 3. Automated Test Execution Commands

```bash
# Run all unit test suites
npm test

# Run math engine tests with coverage
npm run test:math -- --coverage

# Run vision normalizer tests
npm run test:normalizer

# Run coordinate transformation & tracking tests
npm run test:tracking

# Run synthetic frame end-to-end integration tests
npm run test:e2e
```
