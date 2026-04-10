<!--
RETRIEVAL_HINTS:
  keywords: [fingrind, bookkeeping, sqlite, book file, cli, journal entry, preflight, post-entry, correction, provenance]
  answers: [what is fingrind, how do I post an entry, how does the sqlite book file work, what request shape does fingrind accept]
  related: [docs/DEVELOPER.md, docs/DEVELOPER_SQLITE.md, docs/DOC_00_Index.md]
-->

# FinGrind

FinGrind is a finance-grade bookkeeping kernel with an agent-friendly CLI.

The current model is intentionally strict:
- one SQLite file equals one book
- one book belongs to one entity
- the book file can live anywhere on the OS filesystem
- one canonical current schema defines new books
- there is no migration or backward-compatibility layer
- journal entries must balance before they can cross the write boundary
- caller-supplied request provenance is separate from committed audit metadata
- corrections are additive links to earlier postings, not in-place mutation

## What You Can Do Today

FinGrind currently exposes six CLI commands:
- `help`
- `version`
- `capabilities`
- `print-request-template`
- `preflight-entry`
- `post-entry`

`preflight-entry` validates a request without committing it.
`post-entry` commits one posting fact into the selected SQLite book.
Preflight against a missing book is side-effect free; the first commit creates parent directories and
initializes the canonical schema.

## Quick Start

Build the standalone CLI JAR:

```bash
./gradlew :cli:shadowJar
```

Controlled FinGrind surfaces now pin a managed SQLite 3.53.0 runtime:
- `./gradlew test`, `./gradlew check`, and `./gradlew :cli:run`
- `./gradlew -p jazzer check` and local Jazzer fuzzing commands
- GitHub Actions verification and release workflows
- the published container image

The standalone JAR still requires Java 26 or newer, but it does not embed a native SQLite library.
Use it with either:
- `FINGRIND_SQLITE_LIBRARY` pointing at a SQLite 3.53.0 shared library
- a host `libsqlite3` that is already 3.53.0 or newer

For local work from the repository, the easiest path is:

```bash
./gradlew :cli:run --args="help"
```

Inspect the standalone command surface:

```bash
java -jar cli/build/libs/fingrind.jar help
```

Generate a minimal valid request file:

```bash
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

Run preflight against a book file at any path:

```bash
java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/acme-book.sqlite \
  --request-file /tmp/fingrind-request.json
```

Commit the same request:

```bash
java -jar cli/build/libs/fingrind.jar \
  post-entry \
  --book-file /tmp/acme-book.sqlite \
  --request-file /tmp/fingrind-request.json
```

## Request Shape

A posting request has three main parts:
- `effectiveDate`
- `lines`
- `provenance`

Optional correction links go in:

```json
"correction": {
  "kind": "AMENDMENT",
  "priorPostingId": "posting-previous"
}
```

`provenance` accepts:
- required: `actorId`, `actorType`, `commandId`, `idempotencyKey`, `causationId`
- optional: `correlationId`, `reason`

`provenance.recordedAt` and `provenance.sourceChannel` are not accepted. FinGrind stamps committed
audit metadata itself when `post-entry` succeeds.

`lines[].amount` must be a plain decimal string such as `10.00`. Exponent notation such as
`1e6` is rejected.

If `correction` is present:
- `provenance.reason` is required
- `priorPostingId` must already exist in the selected book
- `REVERSAL` must negate the target posting exactly
- only one reversal is allowed per target posting

Current deterministic rejection codes are:
- `duplicate-idempotency-key`
- `correction-reason-required`
- `correction-reason-forbidden`
- `correction-target-not-found`
- `reversal-already-exists`
- `reversal-does-not-negate-target`

## Notes

- `java -jar cli/build/libs/fingrind.jar ...` assumes `java` is version 26 or newer.
- The packaged JAR does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- `./gradlew :cli:run ...` automatically injects the managed SQLite 3.53.0 runtime.
- `./gradlew -p jazzer ...` uses the same managed SQLite 3.53.0 contract for local regression and fuzzing.
- Standalone `java -jar ...` execution requires either `FINGRIND_SQLITE_LIBRARY` or a host
  `libsqlite3` at version 3.53.0 or newer.
- `help`, `version`, and `capabilities` return JSON envelopes for discovery.
- `print-request-template` returns raw JSON so it can be piped straight into a file.
- `--book-file` is required for both preflight and commit.
- FinGrind does not assume a default database location.
- Duplicate `idempotencyKey` values are rejected within the selected book file.
- `capabilities` is the best machine-readable contract surface for request fields, correction rules,
  and rejection codes.

## More User Docs

- [docs/README.md](/Users/erst/Tools/FinGrind/docs/README.md)
- [docs/USER_CLI.md](/Users/erst/Tools/FinGrind/docs/USER_CLI.md)
- [docs/USER_REQUESTS.md](/Users/erst/Tools/FinGrind/docs/USER_REQUESTS.md)
- [docs/USER_EXAMPLES.md](/Users/erst/Tools/FinGrind/docs/USER_EXAMPLES.md)

## More Developer Docs

- [docs/DEVELOPER.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER.md)
- [docs/DEVELOPER_GRADLE.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_GRADLE.md)
- [docs/DEVELOPER_SQLITE.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_SQLITE.md)
- [docs/DEVELOPER_JAZZER.md](/Users/erst/Tools/FinGrind/docs/DEVELOPER_JAZZER.md)

## Legal

FinGrind is MIT-licensed. Its executable JAR bundles Jackson; see [NOTICE](NOTICE) for the
complete attribution list and [PATENTS.md](PATENTS.md) for patent considerations. FinGrind vendors
the official SQLite 3.53.0 amalgamation to build managed native libraries for Gradle, CI, and
container surfaces. The executable JAR itself does not bundle a native SQLite library.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md)
