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
- `jazzer/bin/fuzz-all`
- `jazzer/bin/replay`
- `jazzer/bin/list-findings`

Use `jazzer/bin/*` for all Jazzer workflows.
Raw `./gradlew -p jazzer ...` task names are nested-build internals, not the supported operator
surface.
The wrapper commands are the supported surface because they force `--no-daemon`, own interrupt
cleanup, serialize through the same repo-wide verification lock as `./check.sh`, and write
per-target logs under `jazzer/.local/runs/`. The all-target wrapper keeps pure timeouts moving but
stops on the first actionable harness failure and prints replay-classified findings for that
target. `replay` / `list-findings` expose the project-owned deterministic replay seam for raw
libFuzzer artifacts, including stable lower-case `outcomeKind` / replay-classification values plus
parsed-versus-unparsed detail payloads, and those read-only or maintenance wrappers no longer
erase nested build outputs before they inspect or clean local Jazzer state. The nested build also
gives `src/fuzz/java` its own PMD profile so `@FuzzTest` harnesses keep the shared structural and
correctness checks without being judged as ordinary JUnit suites.

GitHub Actions must never run `jazzer/bin/*`.
Active harness execution hard-fails when `GITHUB_ACTIONS=true`.

More detail lives in:

- [../docs/DEVELOPER_JAZZER.md](../docs/DEVELOPER_JAZZER.md)
- [../docs/DEVELOPER_JAZZER_OPERATIONS.md](../docs/DEVELOPER_JAZZER_OPERATIONS.md)
- [../docs/DEVELOPER_JAZZER_COVERAGE.md](../docs/DEVELOPER_JAZZER_COVERAGE.md)
