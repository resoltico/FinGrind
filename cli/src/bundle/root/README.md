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
2. Let FinGrind create `./secrets/` and `./books/` securely, or keep any existing `./secrets/` and `./books/` directories owner-only before you reuse them
3. Run `${bundleLauncherCommand} generate-book-key-file --book-key-file ./secrets/entity.book-key`
4. Run `${bundleLauncherCommand} open-book --book-file ./books/entity.sqlite --book-key-file ./secrets/entity.book-key --entity-name "Acme Studio" --functional-currency EUR --fiscal-year-start 01-01`
5. Run `${bundleLauncherCommand} list-accounts --book-file ./books/entity.sqlite --book-key-file ./secrets/entity.book-key --limit 10` to inspect the seeded starter chart
6. Run `${bundleLauncherCommand} print-request-template > ./request.json`
7. Edit `./request.json`, then run `${bundleLauncherCommand} preflight-entry --book-file ./books/entity.sqlite --book-key-file ./secrets/entity.book-key --request-file ./request.json` and `${bundleLauncherCommand} post-entry --book-file ./books/entity.sqlite --book-key-file ./secrets/entity.book-key --request-file ./request.json`

The best machine-readable contract after startup is:
- `${bundleLauncherCommand} capabilities --output json`

For the stable or exhaustive discovery layers:
- `${bundleLauncherCommand} capabilities --output json --detail compact`
- `${bundleLauncherCommand} capabilities --output json --detail full`

For the full command surface, examples, and deterministic error guidance:
- `${bundleLauncherCommand} help`

Legal:
See LICENSE, LICENSE-APACHE-2.0, LICENSE-SIL-OFL-1.1, LICENSE-SQLITE3MULTIPLECIPHERS,
NOTICE, and PATENTS.md in this archive for license texts, attribution notices, and
patent considerations.
