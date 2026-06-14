# PROTOCOL_CI.md — CI and Automation Protocol

**Version:** 1.0.0
**Updated:** 2026-06-13
**Inherits:** [.codex/UNIVERSAL_ENGINEERING_CONTRACT.md](./UNIVERSAL_ENGINEERING_CONTRACT.md) v3.0.0+
**Scope:** CI workflows, pipeline configuration, and project automation, in any language or build system.

This file owns the cross-language CI rules. Language- and build-specific CI concerns — toolchain installation, lockfile flags, build caching, test distribution — live in the language protocols. Examples use GitHub Actions; the rules apply to any CI system.

## 1. CI mirrors local verification

The canonical verification command must pass locally and in CI with identical strictness. Do not create CI-only checks that cannot be reproduced locally. Do not soften local checks based on `CI=true`.

## 2. Pin third-party actions

Third-party CI actions and reusable workflows should be pinned to full-length commit SHAs, not mutable tags. Keep the human-readable version in a trailing comment so updates remain reviewable.

```yaml
# Prefer
uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2

# Avoid
uses: actions/checkout@v4
```

## 3. Timeouts and stale runs

Every CI job should declare `timeout-minutes` appropriate to observed runtime.

Use concurrency groups with `cancel-in-progress: true` to abort obsolete runs on the same branch.

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

## 4. Dependency freshness

Use either asynchronous dependency automation or a sync gate paired with automation. A blocking dependency-freshness gate without automated PR creation turns unrelated work into manual dependency maintenance.
