---
afad: "3.5"
version: "0.2.0"
domain: USER_EXAMPLES
updated: "2026-04-09"
route:
  keywords: [fingrind, examples, preflight, commit, duplicate, stdin, correction, book-file, request-template]
  questions: ["show me a working fingrind example", "how do I preflight and commit in fingrind", "how do I send a fingrind request on stdin"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current hard-break core surface.
**Prerequisites**: Java 26 or newer, `./gradlew :cli:shadowJar`, and the pinned SQLite 3.51.3 toolchain provisioned with `./scripts/ensure-sqlite.sh`.

## Preflight And Commit One Book

Generate a request file first:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

Then run preflight and commit:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --request-file /tmp/fingrind-request.json

FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  post-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --request-file /tmp/fingrind-request.json
```

One successful preflight response:

```json
{"status":"preflight-accepted","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08"}
```

`preflight-entry` does not create `/tmp/fingrind/books/acme/acme.sqlite`.
The first successful `post-entry` does.

## Duplicate Rejection

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  post-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --request-file docs/examples/basic-posting-request.json
```

One repeat commit response:

```json
{"status":"rejected","code":"duplicate-idempotency-key","message":"A posting with the same idempotency key already exists in this book.","idempotencyKey":"idem-basic-1"}
```

## Read The Request From Standard Input

```bash
cat docs/examples/basic-posting-request.json | \
  FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
  java -jar cli/build/libs/fingrind.jar \
    preflight-entry \
    --book-file /tmp/fingrind/books/stdin/stdin.sqlite \
    --request-file -
```

## Correction Request Template

```bash
cat docs/examples/correction-request.json
```

That file is a template. Replace `correction.priorPostingId` with a real `postingId` returned by an earlier commit in the same book, then preflight or commit it:

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/fingrind/books/corrections/corrections.sqlite \
  --request-file docs/examples/correction-request.json
```

## Trigger A Deterministic Invalid Request

```bash
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
java -jar cli/build/libs/fingrind.jar \
  preflight-entry \
  --book-file /tmp/fingrind/books/errors/errors.sqlite \
  --request-file docs/examples/invalid-empty-lines-request.json
```

One invalid-request response:

```json
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' for a minimal valid request document, or 'fingrind capabilities' for accepted enums and fields."}
```
