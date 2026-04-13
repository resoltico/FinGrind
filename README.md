<!--
RETRIEVAL_HINTS:
  keywords: [fingrind, bookkeeping, sqlite, book file, cli, open-book, declare-account, preflight, post-entry, reversal, provenance]
  answers: [what is fingrind, how do I initialize a book, how do I declare accounts in fingrind, how do I post an entry, what request shape does fingrind accept]
  related: [docs/README.md, docs/USER_CLI.md, docs/DEVELOPER.md, docs/DOC_00_Index.md]
-->

# FinGrind

FinGrind is a finance-grade bookkeeping kernel with an agent-friendly CLI.

The current model is intentionally strict:
- one SQLite file equals one book
- one book belongs to one entity
- books are initialized explicitly with `open-book`
- accounts must be declared before posting
- posting lines must reference declared active accounts
- one canonical current schema defines new books
- new books use SQLite `STRICT` tables
- there is no migration or backward-compatibility layer
- every journal entry is single-currency
- journal entries must balance before they can cross the write boundary
- caller-supplied request provenance is separate from committed audit metadata
- reversals are additive links to earlier postings, not in-place mutation

## What You Can Do Today

FinGrind currently exposes nine CLI commands:
- `help`
- `version`
- `capabilities`
- `print-request-template`
- `open-book`
- `declare-account`
- `list-accounts`
- `preflight-entry`
- `post-entry`

The book lifecycle is explicit:
- `open-book` creates one new initialized book
- `declare-account` inserts or reactivates one account in that book
- `list-accounts` returns the current account registry
- `preflight-entry` and `post-entry` reject a missing or unopened book with `book-not-initialized`
- `preflight-entry` and `post-entry` reject undeclared or inactive accounts deterministically
- `preflight-entry` is advisory and not a durable commit guarantee

## Quick Start

Build the standalone CLI JAR:

```bash
./gradlew :cli:shadowJar
```

Controlled FinGrind surfaces pin a managed SQLite 3.53.0 runtime:
- `./gradlew test`, `./gradlew check`, and `./gradlew :cli:run`
- `./gradlew -p jazzer check` and local `jazzer/bin/*` fuzzing commands
- GitHub Actions verification and release workflows
- the published container image

For local work from a supported local-filesystem checkout, the simplest path is:

```bash
./gradlew :cli:run --args="help"
```

That route automatically compiles and injects the managed SQLite 3.53.0 runtime.

For standalone `java -jar`, FinGrind does not bundle a native SQLite library.
Use either:
- `FINGRIND_SQLITE_LIBRARY` pointing at a SQLite 3.53.0 shared library
- a host `libsqlite3` that is already 3.53.0 or newer

If the host library is older, write commands such as `open-book` fail immediately by design
because FinGrind hard-requires SQLite 3.53.0 or newer.

For full contributor verification, keep the checkout on the Mac's local filesystem.
Mounted external volumes are outside the supported setup because Gradle project-cache and JaCoCo
file locking can fail there on macOS.
Docker Desktop must also be running before `./check.sh`, because the contributor gate includes a
real Docker smoke stage.

Inspect the standalone command surface:

```bash
java -jar cli/build/libs/fingrind.jar help
```

Initialize a new book:

```bash
java -jar cli/build/libs/fingrind.jar \
  open-book \
  --book-file /tmp/acme-book.sqlite
```

Declare the accounts that your first entry will use:

```bash
java -jar cli/build/libs/fingrind.jar \
  declare-account \
  --book-file /tmp/acme-book.sqlite \
  --request-file docs/examples/declare-account-cash.json

java -jar cli/build/libs/fingrind.jar \
  declare-account \
  --book-file /tmp/acme-book.sqlite \
  --request-file docs/examples/declare-account-revenue.json
```

Generate a minimal posting request:

```bash
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

Preflight and then commit that request:

```bash
java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/acme-book.sqlite \
  --request-file /tmp/fingrind-request.json

java -jar cli/build/libs/fingrind.jar \
  post-entry \
  --book-file /tmp/acme-book.sqlite \
  --request-file /tmp/fingrind-request.json
```

Inspect the declared account registry at any time:

```bash
java -jar cli/build/libs/fingrind.jar \
  list-accounts \
  --book-file /tmp/acme-book.sqlite
```

## Request Shape

A posting request has three main parts:
- `effectiveDate`
- `lines`
- `provenance`

Optional reversal links go in:

```json
"reversal": {
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
Every line inside one entry must share the same `currencyCode`. Mixed-currency entries are rejected.

Successful commits return a FinGrind-generated `postingId`. The default production generator emits
UUID v7 values.

If `reversal` is present:
- `provenance.reason` is required
- `priorPostingId` must already exist in the selected book
- the reversal must negate the target posting exactly
- only one reversal is allowed per target posting
- legacy `correction` and `reversal.kind` fields are rejected

Current deterministic rejection codes include:
- `book-already-initialized`
- `book-contains-schema`
- `book-not-initialized`
- `account-normal-balance-conflict`
- `unknown-account`
- `inactive-account`
- `duplicate-idempotency-key`
- `reversal-reason-required`
- `reversal-reason-forbidden`
- `reversal-target-not-found`
- `reversal-already-exists`
- `reversal-does-not-negate-target`

## Notes

- `java -jar cli/build/libs/fingrind.jar ...` assumes `java` is version 26 or newer.
- The packaged JAR does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- `./gradlew :cli:run ...` automatically injects the managed SQLite 3.53.0 runtime.
- `./gradlew -p jazzer check` uses that same managed SQLite 3.53.0 contract for deterministic
  nested Jazzer verification.
- `jazzer/bin/*` uses that same managed SQLite 3.53.0 contract for supported local active fuzzing,
  regression replay, and cleanup.
- Active fuzzing is local-only. GitHub Actions intentionally never runs `jazzer/bin/*`, and active
  harness execution hard-fails when `GITHUB_ACTIONS=true`.
- Opened book connections keep `foreign_keys` enabled and `trusted_schema` disabled.
- Standalone `java -jar ...` execution requires either `FINGRIND_SQLITE_LIBRARY` or a host
  `libsqlite3` at version 3.53.0 or newer.
- `help`, `version`, and `capabilities` return JSON envelopes for discovery.
- `print-request-template` returns raw JSON so it can be piped straight into a file.
- `--book-file` is required for `open-book`, `declare-account`, `list-accounts`,
  `preflight-entry`, and `post-entry`.
- FinGrind does not assume a default database location.
- `postingId` in committed responses is generated as a UUID v7 value.
- Duplicate `idempotencyKey` values are rejected within the selected book file.
- `capabilities` is the best machine-readable contract surface for commands, request fields,
  account-registry rules, rejection descriptors, advisory preflight semantics, and the
  single-currency entry model.

## More User Docs

- [docs/README.md](docs/README.md)
- [docs/USER_CLI.md](docs/USER_CLI.md)
- [docs/USER_REQUESTS.md](docs/USER_REQUESTS.md)
- [docs/USER_EXAMPLES.md](docs/USER_EXAMPLES.md)

## More Developer Docs

- [docs/DEVELOPER.md](docs/DEVELOPER.md)
- [docs/DEVELOPER_DOCKER.md](docs/DEVELOPER_DOCKER.md)
- [docs/DEVELOPER_GRADLE.md](docs/DEVELOPER_GRADLE.md)
- [docs/DEVELOPER_SQLITE.md](docs/DEVELOPER_SQLITE.md)
- [docs/DEVELOPER_JAZZER.md](docs/DEVELOPER_JAZZER.md)
- [docs/DEVELOPER_JAZZER_OPERATIONS.md](docs/DEVELOPER_JAZZER_OPERATIONS.md)

## Legal

FinGrind is MIT-licensed. Its executable JAR bundles Jackson; see [NOTICE](NOTICE) for the
complete attribution list and [PATENTS.md](PATENTS.md) for patent considerations. FinGrind vendors
the official SQLite 3.53.0 amalgamation to build managed native libraries for Gradle, CI, and
container surfaces. The executable JAR itself does not bundle a native SQLite library.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md)
