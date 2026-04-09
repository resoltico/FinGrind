---
afad: "3.5"
version: "0.1.0"
domain: USER_EXAMPLES
updated: "2026-04-08"
route:
  keywords: [fingrind, examples, preflight, commit, duplicate, stdin, correction, book-file]
  questions: ["show me a working fingrind example", "how do I preflight and commit in fingrind", "how do I send a fingrind request on stdin"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current bootstrap surface.
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
{"status":"rejected","code":"DUPLICATE_IDEMPOTENCY_KEY","message":"A posting with the same idempotency key already exists in this book.","idempotencyKey":"idem-basic-1"}
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

## Preflight A Correction-Shaped Request

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
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' to inspect the minimal valid request shape, or 'fingrind capabilities' to inspect the full CLI contract."}
```
