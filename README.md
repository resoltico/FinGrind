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
- every book is protected at rest with SQLite3 Multiple Ciphers 2.3.3 using the default
  `chacha20` cipher
- every book-bound command requires exactly one explicit passphrase source:
  `--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`
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
./gradlew prepareManagedSqlite
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
```

Controlled FinGrind surfaces pin a managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3
runtime:
- `./gradlew test`, `./gradlew check`, and `./gradlew :cli:run`
- `./gradlew -p jazzer check` and local `jazzer/bin/*` fuzzing commands
- GitHub Actions verification and release workflows
- the published container image

For local work from a supported local-filesystem checkout, the simplest path is:

```bash
./gradlew :cli:run --args="help"
```

That route automatically compiles and injects the managed SQLite 3.53.0 / SQLite3 Multiple
Ciphers 2.3.3 runtime.

For standalone `java -jar`, FinGrind does not bundle a native SQLite library.
Set `FINGRIND_SQLITE_LIBRARY` to the managed library produced by
`./gradlew prepareManagedSqlite`.
FinGrind does not support arbitrary host `libsqlite3` fallback for standalone execution.

The `find .../build/managed-sqlite` export above is the supported local source-checkout path
because it keeps standalone JAR verification on the same managed native library contract as
Gradle, Jazzer, CI, and Docker.

For full contributor verification, keep the checkout on the Mac's local filesystem.
Mounted external volumes are outside the supported setup because Gradle project-cache and JaCoCo
file locking can fail there on macOS.
Docker Desktop must also be running before `./check.sh`, and the bundled `docker buildx` plugin
must be available in the current shell, because the contributor gate includes a real Docker smoke
stage built through Buildx rather than Docker's deprecated legacy builder path.

Inspect the standalone command surface:

```bash
java -jar cli/build/libs/fingrind.jar help
```

Choose one book passphrase source before any book-bound command.
For humans, the best non-persistent route is the interactive prompt:

```bash
java -jar cli/build/libs/fingrind.jar \
  open-book \
  --book-file /tmp/acme-book.sqlite \
  --book-passphrase-prompt
```

For automation or repeatable local workflows, a dedicated key file is also supported:

```bash
install -d -m 700 /tmp/fingrind/keys
umask 077
printf '%s\n' 'acme-demo-passphrase' > /tmp/fingrind/keys/acme.book-key
chmod 600 /tmp/fingrind/keys/acme.book-key
```

The key file must contain one non-empty UTF-8 passphrase.
One trailing newline is tolerated and stripped.
The key file must live on a POSIX filesystem and use owner-only permissions (`0400` or `0600`).

For pipeline-style automation without a persistent file, standard input is also supported:

```bash
printf '%s\n' 'acme-demo-passphrase' | \
  java -jar cli/build/libs/fingrind.jar \
    open-book \
    --book-file /tmp/acme-book.sqlite \
    --book-passphrase-stdin
```

Initialize a new book:

```bash
java -jar cli/build/libs/fingrind.jar \
  generate-book-key-file \
  --book-key-file /tmp/fingrind/keys/acme.book-key

java -jar cli/build/libs/fingrind.jar \
  open-book \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key
```

Rotate that book onto a replacement passphrase when needed:

```bash
java -jar cli/build/libs/fingrind.jar \
  rekey-book \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --new-book-passphrase-prompt
```

Declare the accounts that your first entry will use:

```bash
java -jar cli/build/libs/fingrind.jar \
  declare-account \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/declare-account-cash.json

java -jar cli/build/libs/fingrind.jar \
  declare-account \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
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
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file /tmp/fingrind-request.json

java -jar cli/build/libs/fingrind.jar \
  post-entry \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file /tmp/fingrind-request.json
```

Inspect the declared account registry at any time:

```bash
java -jar cli/build/libs/fingrind.jar \
  list-accounts \
  --book-file /tmp/acme-book.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key
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
- `./gradlew :cli:run ...` automatically injects the managed SQLite 3.53.0 / SQLite3 Multiple
  Ciphers 2.3.3 runtime.
- `./gradlew -p jazzer check` uses that same managed SQLite 3.53.0 / SQLite3 Multiple Ciphers
  2.3.3 contract for deterministic nested Jazzer verification.
- `jazzer/bin/*` uses that same managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 contract
  for supported local active fuzzing, regression replay, and cleanup.
- Active fuzzing is local-only. GitHub Actions intentionally never runs `jazzer/bin/*`, and active
  harness execution hard-fails when `GITHUB_ACTIONS=true`.
- `generate-book-key-file` creates one new `0600` book key file and refuses to overwrite an
  existing path.
- Opened book connections keep `foreign_keys=ON`, `journal_mode=DELETE`, `synchronous=EXTRA`,
  `trusted_schema=OFF`, `secure_delete=ON`, and `temp_store=MEMORY`.
- Standalone `java -jar ...` execution requires `FINGRIND_SQLITE_LIBRARY` pointing at the managed
  SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 library built by `prepareManagedSqlite`.
- For a local source checkout, `:cli:shadowJar` packages only the Java surface.
  Run `./gradlew prepareManagedSqlite` as well before validating the standalone JAR against the
  managed native library under `build/managed-sqlite/`.
- `help`, `version`, and `capabilities` return JSON envelopes for discovery.
- `print-request-template` returns raw JSON so it can be piped straight into a file.
- `open-book`, `rekey-book`, `declare-account`, `list-accounts`, `preflight-entry`, and
  `post-entry` require `--book-file` plus exactly one explicit passphrase source.
- `rekey-book` also requires exactly one replacement passphrase source through
  `--new-book-key-file`, `--new-book-passphrase-stdin`, or `--new-book-passphrase-prompt`.
- `--book-key-file` must point to a non-empty single-line UTF-8 passphrase file on a POSIX
  filesystem, and that file must use owner-only permissions (`0400` or `0600`); one trailing LF or
  CRLF is tolerated and stripped, but embedded control characters are rejected.
- `--book-passphrase-stdin` reads one UTF-8 passphrase payload from standard input, so it cannot
  be combined with `--request-file -`.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo.
- Request JSON must be one object document; duplicate keys and unknown fields are rejected at every
  object level.
- `list-accounts` and `preflight-entry` now reopen books through an explicit read-only SQLite
  session that also enforces `pragma query_only = on`.
- FinGrind does not assume a default database location.
- FinGrind does not accept SQLite URI `key=` or `hexkey=` transport, plaintext CLI passphrase
  arguments, or environment-variable passphrase transport; protected books always use the upstream
  default `chacha20` cipher.
- Protected books are stamped as FinGrind books with a fixed SQLite `application_id` and
  `user_version`, and the runtime rejects external libraries that miss the required SQLite3MC
  compile-option hardening.
- `postingId` in committed responses is generated as a UUID v7 value.
- Duplicate `idempotencyKey` values are rejected within the selected book file.
- Using the wrong key file or wrong non-file passphrase source fails the runtime open with a
  structured `runtime-failure`, typically including `SQLITE_NOTADB`.
- `capabilities` is the best machine-readable contract surface for commands, request fields,
  account-registry rules, rejection descriptors, advisory preflight semantics, the
  single-currency entry model, and the current protected-book runtime metadata.

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
the official SQLite3 Multiple Ciphers 2.3.3 amalgamation, based on SQLite 3.53.0, to build
managed native libraries for Gradle, CI, Jazzer, and container surfaces. SQLite3 Multiple Ciphers
is MIT-licensed via [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS), while the
underlying SQLite sources remain in the public domain. The executable JAR itself does not bundle a
native SQLite library.

[LICENSE](LICENSE) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md)
