---
afad: "5.0.1"
version: "0.61.0"
domain: DEVELOPER_ARCHITECTURE
updated: "2026-07-17"
scope:
  paths: [architecture/build.gradle.kts, architecture/src/test/java/dev/erst/fingrind/architecture/FinGrindArchitectureTest.java, settings.gradle.kts]
  symbols: [FinGrindArchitectureTest]
route:
  keywords: [fingrind, archunit, architecture, module-boundaries, dependency-direction, public-path-hint]
  questions: ["how does fingrind verify module architecture", "which dependency directions does fingrind enforce", "where are cli responsibility boundaries checked"]
---

# Architecture Verification Reference

**Purpose**: Explain the independent architecture verification module that protects FinGrind's production dependency direction and CLI responsibility boundaries.
**Companion references**: [DEVELOPER.md](./DEVELOPER.md), [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md), and [DOC_02_MachineContractAndDescriptors.md](./DOC_02_MachineContractAndDescriptors.md).

## Ownership

`architecture/` is a test-only Gradle module included by [settings.gradle.kts](../settings.gradle.kts). It depends on every production module only for test analysis, so cross-module rules run independently of CLI tests and no production module acquires an architecture-testing dependency.

`FinGrindArchitectureTest` uses ArchUnit's `@AnalyzeClasses` and `@ArchTest` model. ArchUnit imports the production graph once per test class and evaluates every rule against that shared graph. `./gradlew :architecture:test` runs the focused check; root `./gradlew check` and `./check.sh` include its normal Java test and static-quality gates. Because this module intentionally owns no production source, it has no production line or branch denominator; production modules remain subject to the repository's full JaCoCo coverage verification.

## Enforced Direction

The architecture module owns this permitted production dependency direction:

```text
core -> contract -> executor -> {sqlite, report-pdf} -> cli
```

The rules reject a lower layer accessing a higher layer. SQLite and PDF are sibling adapters, and CLI is the only outer adapter that may depend on both. The graph is deliberately narrow: a new production module must be placed in this topology explicitly, not silently accepted by an unowned package boundary.

The module also enforces CLI responsibilities by class suffix instead of a hand-maintained class list. Parsers cannot depend on renderers, response writers, or command executors; response writers cannot depend on parsers or executors; command executors and output renderers cannot cross into the other named responsibilities; and renderers cannot depend directly on SQLite. Naming a new `*Parser`, `*Renderer`, `*ResponseWriter`, or `*CommandExecutor` therefore brings it under the same rule immediately.

Machine JSON is additionally prohibited from depending on `PublicPathHint`. Filesystem path hints belong to human-facing diagnostics and must not cross into a machine payload boundary.

## Scope Limit

ArchUnit checks bytecode structure. It cannot prove chart data, SQL trigger behavior, message grammar, discovery completeness, or accounting invariants. Those concerns remain owned by domain tests, SQLite integration tests, rendered-contract tests, documentation checks, and the full verification gate. Do not replace a direct business invariant test with an architecture rule.

When introducing a production module or responsibility suffix, update the architecture rule and its focused tests in the same change. A rule that cannot be green is a design defect to resolve; the architecture module does not maintain a baseline of tolerated violations.
