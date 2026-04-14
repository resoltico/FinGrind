# FinGrind ${version}

This archive is the self-contained FinGrind CLI for `${bundleClassifier}`.
It already contains:
- the runnable launcher at `./bin/fingrind`
- a private Java 26 runtime image under `./runtime/`
- the FinGrind application JAR under `./lib/app/`
- the pinned managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 library for `${bundleClassifier}`

No separate Java install is required for this archive.
No `FINGRIND_SQLITE_LIBRARY` export is required for this archive.

Machine-readable bundle metadata:
- `./bundle-manifest.json`

Supported public bundle targets:
${publicBundleTargetsMarkdown}

Public operating systems not currently shipped as release bundles:
${unsupportedPublicOperatingSystemsMarkdown}

Quick start:
1. Run `./bin/fingrind help`
2. Run `./bin/fingrind generate-book-key-file --book-key-file ./entity.book-key`
3. Run `./bin/fingrind open-book --book-file ./entity.sqlite --book-key-file ./entity.book-key`
4. Run `./bin/fingrind print-request-template > ./request.json`
5. Edit `./request.json`, then run `./bin/fingrind preflight-entry ...` and `./bin/fingrind post-entry ...`

The best machine-readable contract after startup is:
- `./bin/fingrind capabilities`

For the full command surface, examples, and deterministic error guidance:
- `./bin/fingrind help`
