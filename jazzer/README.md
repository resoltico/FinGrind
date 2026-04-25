# FinGrind Jazzer

This directory is FinGrind's nested local-only Jazzer build.

Use these surfaces intentionally:

- `./gradlew -p jazzer test`
- `./gradlew -p jazzer jazzerRegression`
- `./gradlew -p jazzer check`

Those are the deterministic nested-build commands.

For active fuzzing, use only:

- `jazzer/bin/fuzz-cli-request`
- `jazzer/bin/fuzz-ledger-plan-request`
- `jazzer/bin/fuzz-posting-workflow`
- `jazzer/bin/fuzz-sqlite-book-roundtrip`
- `jazzer/bin/fuzz-all`
- `jazzer/bin/replay`
- `jazzer/bin/list-findings`

Do not run active fuzzing through raw `./gradlew -p jazzer fuzz...` task names.
The wrapper scripts are the supported operator surface because they force `--no-daemon`, own
interrupt cleanup, serialize runs through `jazzer/.local/run-lock`, and write per-target logs
under `jazzer/.local/runs/`. The all-target wrapper now keeps pure timeouts moving but stops on
the first actionable harness failure, prints replay-classified findings for that target, and no
longer hides multi-target orchestration inside one shell library call. `replay` / `list-findings`
expose the project-owned deterministic replay seam for raw libFuzzer artifacts, including stable
lower-case `outcomeKind` / replay-classification values plus parsed-versus-unparsed detail payloads.
The nested build also gives `src/fuzz/java` its own PMD profile so `@FuzzTest` harnesses keep the
shared structural and correctness checks without being judged as ordinary JUnit suites.

GitHub Actions must never run `jazzer/bin/*`.
Active harness execution hard-fails when `GITHUB_ACTIONS=true`.

More detail lives in:

- [../docs/DEVELOPER_JAZZER.md](../docs/DEVELOPER_JAZZER.md)
- [../docs/DEVELOPER_JAZZER_OPERATIONS.md](../docs/DEVELOPER_JAZZER_OPERATIONS.md)
- [../docs/DEVELOPER_JAZZER_COVERAGE.md](../docs/DEVELOPER_JAZZER_COVERAGE.md)
