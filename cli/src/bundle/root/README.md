# FinGrind ${version}

This archive is the self-contained FinGrind CLI for `${bundleClassifier}`.
It already contains:
- the runnable launcher at `${bundleLauncherCommand}`
- a private Java 26 runtime image under `./runtime/`
- the FinGrind application JAR under `./lib/app/`
- the pinned managed SQLite `${requiredMinimumSqliteVersion}` / SQLite3 Multiple Ciphers `${requiredSqlite3mcVersion}` library for `${bundleClassifier}`
- the platform-native public archive format `${bundleArchiveFormat}`

No separate Java install is required for this archive.
No `FINGRIND_SQLITE_LIBRARY` export is required for this archive.

Machine-readable bundle metadata:
- `./bundle-manifest.json`

The archive target facts and managed-SQLite version pins come from the same protocol-owned
contract resources that also drive `capabilities`, release verification, and source-checkout
bundle assembly.

Supported public bundle targets:
${publicBundleTargetsMarkdown}

Public bundle targets not currently shipped as release bundles:
${unsupportedPublicBundleTargetsMarkdown}

Quick start:
1. Run `${bundleLauncherCommand} help`
2. Run `${bundleLauncherCommand} generate-book-key-file --book-key-file ./entity.book-key`
3. Run `${bundleLauncherCommand} open-book --book-file ./entity.sqlite --book-key-file ./entity.book-key`
4. Run `${bundleLauncherCommand} print-request-template > ./request.json`
5. Edit `./request.json`, then run `${bundleLauncherCommand} preflight-entry ...` and `${bundleLauncherCommand} post-entry ...`

The best machine-readable contract after startup is:
- `${bundleLauncherCommand} capabilities`

For the full command surface, examples, and deterministic error guidance:
- `${bundleLauncherCommand} help`
