<!--
RETRIEVAL_HINTS:
  keywords: [fingrind, bookkeeping, sqlite, book file, cli, journal entry, preflight, post-entry]
  answers: [what is fingrind, how do I post an entry, how does the sqlite book file work]
  related: [docs/DEVELOPER.md, docs/DEVELOPER_SQLITE.md, docs/DOC_00_Index.md]
-->

# FinGrind

FinGrind is a finance-grade bookkeeping kernel with an agent-friendly CLI.

The current model is intentionally strict:
- one SQLite file equals one book
- one book belongs to one entity
- the book file can live anywhere on the OS filesystem
- journal entries must balance before they can cross the write boundary
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

## Quick Start

Build the standalone CLI JAR:

```bash
./scripts/ensure-sqlite.sh
./gradlew :cli:shadowJar
```

The packaged JAR requires Java 26 or newer.

Inspect the command surface:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
  java -jar cli/build/libs/fingrind.jar help
```

Generate a minimal valid request file:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
  java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

Run preflight against a book file at any path:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
  java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/acme-book.sqlite \
  --request-file /tmp/fingrind-request.json
```

Commit the same request:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
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

## Notes

- `java -jar cli/build/libs/fingrind.jar ...` assumes `java` is version 26 or newer.
- `help`, `version`, and `capabilities` return JSON envelopes for discovery.
- `print-request-template` returns raw JSON so it can be piped straight into a file.
- `--book-file` is required for both preflight and commit.
- FinGrind does not assume a default database location.
- For repo-local development, provision the pinned SQLite 3.51.3 toolchain with `./scripts/ensure-sqlite.sh`.
- FinGrind accepts `FINGRIND_SQLITE3_BINARY` to point at the exact SQLite binary to use.
- Duplicate `idempotencyKey` values are rejected within the selected book file.

## More User Docs

- [docs/README.md](/Users/erst/Tools/FinGrind/docs/README.md)
- [docs/USER_CLI.md](/Users/erst/Tools/FinGrind/docs/USER_CLI.md)
- [docs/USER_REQUESTS.md](/Users/erst/Tools/FinGrind/docs/USER_REQUESTS.md)
- [docs/USER_EXAMPLES.md](/Users/erst/Tools/FinGrind/docs/USER_EXAMPLES.md)

## Legal

FinGrind is MIT-licensed. Its executable JAR bundles Jackson (Apache 2.0). FinGrind
provisions SQLite as a native binary at runtime; SQLite is in the public domain. See
[NOTICE](NOTICE) for the complete attribution list and [PATENTS.md](PATENTS.md) for
patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md)
