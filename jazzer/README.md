# FinGrind Jazzer

This directory is FinGrind's nested local-only Jazzer build.

Use these surfaces intentionally:

- `./gradlew -p jazzer test`
- `./gradlew -p jazzer jazzerRegression`
- `./gradlew -p jazzer check`

Those are the deterministic nested-build commands.

For active fuzzing, use only:

- `jazzer/bin/fuzz-cli-request`
- `jazzer/bin/fuzz-posting-workflow`
- `jazzer/bin/fuzz-sqlite-book-roundtrip`
- `jazzer/bin/fuzz-all`

Do not run active fuzzing through raw `./gradlew -p jazzer fuzz...` task names.
The wrapper scripts are the supported operator surface because they force `--no-daemon`, own
interrupt cleanup, serialize runs through `jazzer/.local/run-lock`, and write per-target logs
under `jazzer/.local/runs/`.

GitHub Actions must never run `jazzer/bin/*`.
Active harness execution hard-fails when `GITHUB_ACTIONS=true`.

More detail lives in:

- [../docs/DEVELOPER_JAZZER.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_JAZZER.md)
- [../docs/DEVELOPER_JAZZER_OPERATIONS.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_JAZZER_OPERATIONS.md)
- [../docs/DEVELOPER_JAZZER_COVERAGE.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_JAZZER_COVERAGE.md)
