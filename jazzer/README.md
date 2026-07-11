# FinGrind Jazzer

This directory is FinGrind's nested local-only Jazzer build.

Use these surfaces intentionally:

- `jazzer/bin/test`
- `jazzer/bin/regression`
- `jazzer/bin/check`

Those are the deterministic Jazzer verification commands.

For active fuzzing, use only:

- `jazzer/bin/fuzz-cli-request`
- `jazzer/bin/fuzz-ledger-plan-request`
- `jazzer/bin/fuzz-posting-workflow`
- `jazzer/bin/fuzz-sqlite-book-roundtrip`
- `jazzer/bin/fuzz-inventory-costing-math`
- `jazzer/bin/fuzz-all`
- `jazzer/bin/replay`
- `jazzer/bin/list-findings`
- `jazzer/bin/promote-seed`
- `jazzer/bin/seed-audit`

Use `jazzer/bin/*` for all Jazzer workflows.
Raw `./gradlew -p jazzer ...` task names are nested-build internals, not the supported operator
surface.
The wrapper commands are the supported surface because they force `--no-daemon`, own interrupt
cleanup, serialize through the same repo-wide verification lock as `./check.sh`, and write
per-target logs under `jazzer/.local/runs/`. The all-target wrapper stops on the first actionable
harness failure, keeps later harnesses available when one wrapper has to enforce timeout teardown,
and prints replay-classified findings for the failed target. Ordinary bounded Jazzer completions
return success instead of being mislabeled as wrapper timeouts. `replay`, `list-findings`,
`promote-seed`, and `seed-audit` expose the project-owned deterministic replay and seed-management
surface for raw libFuzzer artifacts, including stable lower-case `outcomeKind` /
replay-classification values, parsed-versus-unparsed detail payloads, required coverage-intent
metadata, and seed auditing that fails on duplicate raw inputs, orphaned committed inputs,
committed `unexpected-failure` expectations, unreadable or escaped metadata references, missing
committed inputs, or malformed committed `.json` seed bodies. `promote-seed` requires
lower_snake_case seed names, `coverageIntent` values are unique across the committed corpus, the
seed-management `--help` surfaces print the supported replayable target keys directly, and
`--json` now returns structured deterministic failures without leaking Gradle task boilerplate.
Those read-only or maintenance wrappers no longer erase nested build outputs before they inspect or
clean local Jazzer state. The nested build also gives `src/fuzz/java` its own PMD profile so
`@FuzzTest` harnesses keep the shared structural and correctness checks without being judged as
ordinary JUnit suites.

GitHub Actions must never run `jazzer/bin/*`.
Active harness execution hard-fails when `GITHUB_ACTIONS=true`.

More detail lives in:

- [../docs/DEVELOPER_JAZZER.md](../docs/DEVELOPER_JAZZER.md)
- [../docs/DEVELOPER_JAZZER_OPERATIONS.md](../docs/DEVELOPER_JAZZER_OPERATIONS.md)
- [../docs/DEVELOPER_JAZZER_COVERAGE.md](../docs/DEVELOPER_JAZZER_COVERAGE.md)
