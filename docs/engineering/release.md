# CalcLens Release Engineering & Verification

## 1. Release Philosophy & Versioning Strategy

CalcLens follows Semantic Versioning (`MAJOR.MINOR.PATCH`):
* **MAJOR**: Structural evolution of mathematical capabilities (e.g. V1 Arithmetic -> V2 Extended Notation -> V3 Handwriting -> V4 Algebra).
* **MINOR**: New platform features, tracking improvements, or UX additions.
* **PATCH**: Bug fixes, performance optimizations, and stability improvements.

---

## 2. Release Acceptance Criteria

Before any build is promoted to production release candidate, it must satisfy all gates:

| Verification Gate | Requirement | Mandatory Threshold |
| :--- | :--- | :--- |
| **Math Correctness** | Automated arithmetic test suite | 100% Pass rate (0 regressions) |
| **Code Quality** | Static analysis & linter | 0 errors, 0 warnings |
| **Type Integrity** | TypeScript / strict compilation | 0 type errors |
| **Performance Benchmark** | Continuous camera streaming | Sustained $\ge 30\text{ FPS}$ on reference devices |
| **Recognition Latency** | Synthetic benchmark suite | $\le 200\text{ ms}$ average latency |
| **Memory Soak Test** | 15-minute continuous camera execution | Heap delta $\le 10\text{ MB}$, 0 leaks |
| **Privacy Audit** | Network traffic monitor | Zero network calls during operation |

---

## 3. Build & Packaging Pipeline

```text
[Source Code]
      │
      ▼
[Lint & Type Check]
      │
      ▼
[Automated Unit & Integration Tests]
      │
      ▼
[Production Minification & Asset Bundling]
      │
      ▼
[Security & Network Leak Audit]
      │
      ▼
[Final Production Artifacts]
```

### Build Commands
```bash
# Verify formatting
npm run format:check

# Run static analysis & type checks
npm run lint
npm run typecheck

# Execute complete test suite
npm test

# Build production bundle
npm run build
```

---

## 4. Rollback & Hotfix Protocols

* **Immediate Rollback**: If an arithmetic regression or crash loop is detected in production, the release is rolled back immediately to the previous stable release tag.
* **Hotfix Workflow**: Hotfixes are branched directly from the release tag, verified via the math test suite, and deployed without modifying unrelated subsystems.
