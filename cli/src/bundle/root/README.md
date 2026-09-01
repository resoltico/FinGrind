# FinGrind ${version}

This archive is the self-contained FinGrind CLI for `${bundleClassifier}`.
It already contains:
- the runnable launcher at `${bundleLauncherCommand}`
- a private Java 26 runtime image under `./runtime/`
- the FinGrind application JAR under `./lib/app/`
- the pinned managed SQLite `${requiredMinimumSqliteVersion}` / SQLite3 Multiple Ciphers `${requiredSqlite3mcVersion}` library for `${bundleClassifier}`
- the platform-native public archive format `${bundleArchiveFormat}`

No separate Java install is required for this archive.
Any inherited `FINGRIND_SQLITE_LIBRARY` override is ignored for this archive.

Machine-readable bundle metadata:
- `./bundle-manifest.json`
- `bundle-manifest.json` also records the ZIP-portable normalized artifact timestamp applied to every bundled file for reproducible extraction semantics

The archive target facts and managed-SQLite version pins come from the same protocol-owned
contract resources that also drive `capabilities`, release verification, and source-checkout
bundle assembly.

Supported public bundle targets:
${publicBundleTargetsMarkdown}

Public bundle targets not currently shipped as release bundles:
${unsupportedPublicBundleTargetsMarkdown}

Quick start:
1. Run `${bundleLauncherCommand} help`
2. Create owner-only `./.local/fingrind/secrets/` and `./.local/fingrind/books/` directories yourself (for example, `mkdir -p -m 700 ./.local/fingrind/secrets ./.local/fingrind/books` on macOS/Linux); FinGrind never creates or weakens the secret parent directory
3. Run `${bundleLauncherCommand} generate-book-key-file --new-book-key-file ./.local/fingrind/secrets/entity.book-key`
4. Prepare a separate nonempty owner-only UTF-8 founder passphrase file at `./.local/fingrind/secrets/entity-founder.passphrase`; FinGrind creates the absent founder credential at `./.local/fingrind/secrets/entity-founder.fgatk` exactly once
5. Run `${bundleLauncherCommand} open-book --book-file ./.local/fingrind/books/entity.sqlite --book-key-file ./.local/fingrind/secrets/entity.book-key --entity-name "Acme Studio" --book-template-id OWNER_MANAGED_SERVICE --accounting-basis CASH --functional-currency EUR --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-founder-key-file ./.local/fingrind/secrets/entity-founder.fgatk --attestation-founder-passphrase-file ./.local/fingrind/secrets/entity-founder.passphrase`
6. Run `${bundleLauncherCommand} list-accounts --book-file ./.local/fingrind/books/entity.sqlite --book-key-file ./.local/fingrind/secrets/entity.book-key --limit 10` to inspect the seeded accounts
7. Copy the bundled first-sale sample with `cp ./quick-start-request.json ./.local/fingrind/request.json`
8. Edit `./.local/fingrind/request.json` and replace the sample evidence and provenance values before real-world use
9. Run `${bundleLauncherCommand} preflight-entry --book-file ./.local/fingrind/books/entity.sqlite --book-key-file ./.local/fingrind/secrets/entity.book-key --request-file ./.local/fingrind/request.json`
10. Run `${bundleLauncherCommand} record-sale-settled --book-file ./.local/fingrind/books/entity.sqlite --book-key-file ./.local/fingrind/secrets/entity.book-key --request-file ./.local/fingrind/request.json --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-key-file ./.local/fingrind/secrets/entity-founder.fgatk --attestation-passphrase-file ./.local/fingrind/secrets/entity-founder.passphrase`

Use `--accounting-basis ACCRUAL` when you want the accrual owner-managed service chart.
Trading books can stay on the same typed path for stock acquisition through `${bundleLauncherCommand} print-request-template record-purchase-settled --book-template-id OWNER_MANAGED_TRADING` and `${bundleLauncherCommand} print-request-template record-purchase-on-credit --book-template-id OWNER_MANAGED_TRADING`.

The best machine-readable contract after startup is:
- `${bundleLauncherCommand} capabilities --output json`

For the stable or exhaustive discovery layers:
- `${bundleLauncherCommand} capabilities --output json --detail compact`
- `${bundleLauncherCommand} capabilities --output json --detail full`

For the full command surface, examples, and deterministic error guidance:
- `${bundleLauncherCommand} help`

Legal:
See LICENSE, LICENSE-APACHE-2.0, LICENSE-CC0-1.0, LICENSE-SIL-OFL-1.1,
LICENSE-SQLITE3MULTIPLECIPHERS, LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY, NOTICE,
NOTICE-ZULU-26.32.203, PATENTS.md, and SOURCE_OFFER.md in this archive. The linked Java runtime's controlling
license, exception, and component notices are preserved and hash-indexed under runtime/legal/;
runtime/provenance/source-jdk-release identifies the source JDK build.
