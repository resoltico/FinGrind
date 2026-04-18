---
afad: "3.5"
version: "0.16.0"
domain: USER_EXAMPLES
updated: "2026-04-18"
route:
  keywords: [fingrind, examples, open-book, rekey-book, inspect-book, declare-account, list-accounts, get-posting, list-postings, account-balance, preflight, commit, stdin, reversal, print-plan-template, execute-plan]
  questions: ["show me a working fingrind example", "how do I inspect a book and query postings in fingrind", "how do I initialize a book and post in fingrind", "how do I send a fingrind request on stdin", "how do I run an atomic ledger plan in fingrind"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current public surface.
**Prerequisites**: Use the extracted self-contained FinGrind bundle launcher. In the examples
below, `fingrind` means that launcher, for example
`./fingrind-0.16.0-macos-aarch64/bin/fingrind` on macOS/Linux or
`.\fingrind-0.16.0-windows-x86_64\bin\fingrind.cmd` on Windows. For source-driven local work,
the equivalent developer route is `./gradlew :cli:run --args="..."` on macOS/Linux or
`.\gradlew.bat :cli:run --args="..."` on Windows.

## Choose A Book Passphrase Source

For humans, the best non-persistent route is the interactive prompt:

```bash
fingrind \
  open-book \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-passphrase-prompt
```

For automation, generate a dedicated key file:

```bash
fingrind \
  generate-book-key-file \
  --book-key-file /tmp/fingrind/keys/acme.book-key
```

The generated key file contains one non-empty single-line UTF-8 passphrase.
One trailing newline is tolerated and stripped when loading an existing file.
Embedded control characters are rejected.
The key file must be protected with POSIX owner-only permissions (`0400` or `0600`) on
macOS/Linux, or a Windows owner-only ACL on Windows.

For pipeline automation without a persistent file:

```bash
printf '%s\n' 'acme-demo-passphrase' | \
  fingrind \
    open-book \
    --book-file /tmp/fingrind/books/acme/acme.sqlite \
    --book-passphrase-stdin
```

## Initialize One Book

```bash
fingrind \
  open-book \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/tmp/fingrind/books/acme/acme.sqlite","initializedAt":"2026-04-13T11:58:35.532739Z"}}
```

## Inspect Compatibility Before Mutating

```bash
fingrind \
  inspect-book \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key
```

One successful response is checked in at
[examples/inspect-book-response.json](./examples/inspect-book-response.json).
Use this command when an agent needs to know whether the selected book is initialized, compatible
with the current binary, and safe for `open-book`, `declare-account`, or `post-entry`.

## Rotate One Book Passphrase

```bash
fingrind \
  rekey-book \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --new-book-passphrase-prompt
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/tmp/fingrind/books/acme/acme.sqlite"}}
```

## Declare Accounts And Page The Registry

```bash
fingrind \
  declare-account \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/declare-account-cash.json

fingrind \
  declare-account \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/declare-account-revenue.json

fingrind \
  list-accounts \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --limit 50 \
  --offset 0
```

One successful paged response is checked in at
[examples/list-accounts-response.json](./examples/list-accounts-response.json).

## Preflight And Commit One Entry

You can generate a new template at any time:

```bash
fingrind \
  print-request-template > /tmp/fingrind-request.json
```

For the concrete walkthrough below, reuse the checked-in example request:

```bash
fingrind \
  preflight-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/basic-posting-request.json

fingrind \
  post-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/basic-posting-request.json
```

One successful preflight response:

```json
{"status":"preflight-accepted","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08"}
```

That response is advisory, not a durable commit guarantee. `post-entry` still re-runs its
authoritative commit-time checks inside the write transaction.

One successful commit response:

```json
{"status":"committed","postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:00:00Z"}
```

`postingId` is generated by FinGrind as a UUID v7 value.
The request shape is checked in at [examples/basic-posting-request.json](./examples/basic-posting-request.json).
One example committed response is checked in at
[examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json).
Every line in that request uses the same `currencyCode`; mixed-currency entries are rejected, and
every journal line amount must be greater than zero.

## Run One Atomic Ledger Plan

Generate the canonical plan scaffold:

```bash
fingrind \
  print-plan-template > /tmp/fingrind-plan.json
```

Or execute the checked-in runnable example plan directly against a fresh book:

```bash
fingrind \
  execute-plan \
  --book-file /tmp/fingrind/books/acme/acme-plan.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/ledger-plan-request.json
```

That plan:
- opens a new book
- declares cash and revenue
- posts one balanced entry
- asserts the resulting cash balance

Checked-in plan examples:
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json)
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json)
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json)

## Query The Committed History

```bash
fingrind \
  get-posting \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --posting-id 01963c70-8d65-7b56-8a64-3c92745d8f72

fingrind \
  list-postings \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --account-code 1000 \
  --limit 25

fingrind \
  account-balance \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --account-code 1000
```

Checked-in example responses:
- [examples/get-posting-response.json](./examples/get-posting-response.json)
- [examples/list-postings-response.json](./examples/list-postings-response.json)
- [examples/account-balance-response.json](./examples/account-balance-response.json)

If the posting-history response includes `payload.nextCursor`, pass that opaque value back through
`--cursor` to continue from the prior page without using offset scans:

```bash
fingrind \
  list-postings \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --account-code 1000 \
  --limit 25 \
  --cursor "<nextCursor-from-the-prior-page>"
```

## Book Must Exist And Be Opened

```bash
fingrind \
  preflight-entry \
  --book-file /tmp/fingrind/books/missing/missing.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/basic-posting-request.json
```

One deterministic rejection:

```json
{"status":"rejected","code":"posting-book-not-initialized","message":"The selected book does not exist or has not been initialized with open-book.","idempotencyKey":"idem-basic-1"}
```

## Accounts Must Be Declared First

```bash
fingrind \
  preflight-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/unknown-account-request.json
```

One deterministic rejection is checked in at
[examples/account-state-violations-response.json](./examples/account-state-violations-response.json).
Posting-side account failures are now aggregated under `account-state-violations` so callers can
repair every reported account issue before retrying.

## Duplicate Rejection

```bash
fingrind \
  post-entry \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/basic-posting-request.json
```

One repeat commit response:

```json
{"status":"rejected","code":"duplicate-idempotency-key","message":"A posting with the same idempotency key already exists in this book.","idempotencyKey":"idem-basic-1"}
```

## Read The Request From Standard Input

```bash
cat docs/examples/basic-posting-request.json | \
  fingrind \
    preflight-entry \
    --book-file /tmp/fingrind/books/stdin/stdin.sqlite \
    --book-key-file /tmp/fingrind/keys/acme.book-key \
    --request-file -
```

Remember that the selected book must already be initialized and the referenced accounts must
already be declared before that stdin-driven preflight can succeed.
`--request-file -` uses standard input for JSON, so it cannot be combined with
`--book-passphrase-stdin` in the same invocation.

## Reversal Request Template

```bash
cat docs/examples/reversal-request.json
```

That file is a template. Replace `reversal.priorPostingId` with a real `postingId` returned by an
earlier commit in the same book, then preflight or commit it:

```bash
fingrind \
  preflight-entry \
  --book-file /tmp/fingrind/books/reversals/reversals.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/reversal-request.json
```

## Trigger A Deterministic Invalid Request

```bash
fingrind \
  preflight-entry \
  --book-file /tmp/fingrind/books/errors/errors.sqlite \
  --book-key-file /tmp/fingrind/keys/acme.book-key \
  --request-file docs/examples/invalid-empty-lines-request.json
```

One invalid-request response:

```json
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' for a minimal valid request document, or 'fingrind capabilities' for accepted enums and fields."}
```

## Wrong Key Fails The Open

```bash
printf '%s\n' 'wrong-passphrase' > /tmp/fingrind/keys/wrong.book-key
chmod 600 /tmp/fingrind/keys/wrong.book-key

fingrind \
  list-accounts \
  --book-file /tmp/fingrind/books/acme/acme.sqlite \
  --book-key-file /tmp/fingrind/keys/wrong.book-key
```

One runtime-failure response includes `SQLITE_NOTADB`, because FinGrind validates the configured
book passphrase before any schema or account read proceeds.
