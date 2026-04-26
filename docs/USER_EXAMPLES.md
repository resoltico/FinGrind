---
afad: "4.0"
version: "0.27.0"
domain: USER_EXAMPLES
updated: "2026-04-26"
route:
  keywords: [fingrind, examples, open-book, rekey-book, inspect-book, declare-account, list-accounts, get-posting, list-postings, account-balance, trial-balance, account-ledger, period-summary, preflight, commit, stdin, reversal, print-plan-template, execute-plan]
  questions: ["show me a working fingrind example", "how do I inspect a book and query postings in fingrind", "how do I initialize a book and post in fingrind", "how do I export a trial balance in fingrind", "how do I send a fingrind request on stdin", "how do I run an atomic ledger plan in fingrind"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current public surface.
**Prerequisites**: Use the extracted self-contained FinGrind bundle launcher. In the examples
below, `fingrind` means that launcher, for example
`./fingrind-0.27.0-macos-aarch64/bin/fingrind` on macOS/Linux or
`.\fingrind-0.27.0-windows-x86_64\bin\fingrind.ps1` on Windows. For source-driven local work,
the equivalent developer route is `./gradlew :cli:run --args="..."` on macOS/Linux or
`.\gradlew.bat :cli:run --args="..."` on Windows.

The public release bundle does not include `docs/examples/`. The runnable commands below therefore
use local working files such as `./declare-account-cash.json` and `./basic-posting-request.json`.
If you are in a source checkout, you can populate those files by copying the matching checked-in
fixtures under [examples/](./examples/). The command blocks below use POSIX shell line continuation
for readability; on Windows PowerShell, keep the same launcher, local file names, and command
order, but use PowerShell line continuation or one-line invocations.

## Choose A Book Passphrase Source

For humans, the best non-persistent route is the interactive prompt:

```bash
fingrind \
  open-book \
  --book-file ./acme.sqlite \
  --book-passphrase-prompt
```

For automation, generate a dedicated key file:

```bash
fingrind \
  generate-book-key-file \
  --book-key-file ./acme.book-key
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
    --book-file ./acme.sqlite \
    --book-passphrase-stdin
```

On Windows PowerShell, the same stdin route is:

```powershell
"acme-demo-passphrase" | fingrind open-book --book-file .\acme.sqlite --book-passphrase-stdin
```

## Initialize One Book

```bash
fingrind \
  open-book \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/tmp/fingrind/books/acme/acme.sqlite","initializedAt":"2026-04-13T11:58:35.532739Z"}}
```

## Inspect Compatibility Before Mutating

```bash
fingrind \
  inspect-book \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key
```

One successful response is checked in at
[examples/inspect-book-response.json](./examples/inspect-book-response.json).
Use this command when an agent needs to know whether the selected book is initialized, compatible
with the current binary, and safe for `open-book`, `declare-account`, or `post-entry`.

## Rotate One Book Passphrase

```bash
fingrind \
  rekey-book \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --new-book-passphrase-prompt
```

`rekey-book` also accepts `--new-book-key-file` and `--new-book-passphrase-stdin` for the
replacement secret. The interactive replacement prompt asks for the new passphrase twice and
rejects mismatched entries.

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/tmp/fingrind/books/acme/acme.sqlite"}}
```

## Declare Accounts And Page The Registry

Create these local files first:
- `./declare-account-cash.json`: copy [examples/declare-account-cash.json](./examples/declare-account-cash.json)
- `./declare-account-revenue.json`: copy [examples/declare-account-revenue.json](./examples/declare-account-revenue.json)

```bash
fingrind \
  declare-account \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./declare-account-cash.json

fingrind \
  declare-account \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./declare-account-revenue.json

fingrind \
  list-accounts \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --limit 1
```

One successful paged response is checked in at
[examples/list-accounts-response.json](./examples/list-accounts-response.json).
If that response includes `payload.nextCursor`, pass the opaque value back through `--cursor` to
continue from the prior page without offset scans:

```bash
fingrind \
  list-accounts \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --limit 1 \
  --cursor "<nextCursor-from-the-prior-page>"
```

## Preflight And Commit One Entry

You can generate a new template at any time:

```bash
fingrind \
  print-request-template > ./request.json
```

That generated scaffold is byte-identical to the checked-in
[examples/request-template.json](./examples/request-template.json) fixture. Both intentionally use
the canonical scaffold `effectiveDate` value `2026-04-17`.

For the concrete walkthrough below, reuse the checked-in example request:

- `./basic-posting-request.json`: copy [examples/basic-posting-request.json](./examples/basic-posting-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./basic-posting-request.json

fingrind \
  post-entry \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./basic-posting-request.json
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
  print-plan-template > ./plan.json
```

Like `print-request-template`, this scaffold is byte-identical to the checked-in
[examples/ledger-plan-template.json](./examples/ledger-plan-template.json) fixture and uses the
canonical scaffold `effectiveDate` value `2026-04-17`.

Or execute the checked-in runnable example plan directly against a fresh book:

- `./ledger-plan-request.json`: copy [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./acme-plan.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./ledger-plan-request.json
```

That plan:
- opens a new book
- declares cash and revenue
- posts one balanced entry
- asserts the resulting cash balance

Checked-in plan examples:
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json)
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)
- [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json)
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json)
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json)
- [examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json)

If you want the plan itself to inspect paginated state before it finishes, use the checked-in
query example:

- `./ledger-plan-query-request.json`: copy [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./acme-plan.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./ledger-plan-query-request.json
```

That committed journal keeps `count`, `pageLimit`, optional `nextCursor`, `hasMore`, and grouped
`account` / `posting` facts for the successful query steps. One checked-in response is at
[examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json).

## Query The Committed History

```bash
fingrind \
  get-posting \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --posting-id 01963c70-8d65-7b56-8a64-3c92745d8f72

fingrind \
  list-postings \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --account-code 1000 \
  --limit 25

fingrind \
  account-balance \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
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
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --account-code 1000 \
  --limit 25 \
  --cursor "<nextCursor-from-the-prior-page>"
```

## Run Office-Worker Reports

```bash
fingrind \
  trial-balance \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --effective-date-to 2026-04-08 \
  --output human

fingrind \
  account-ledger \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --account-code 1000 \
  --effective-date-from 2026-04-07 \
  --effective-date-to 2026-04-08 \
  --output csv

fingrind \
  period-summary \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --effective-date-from 2026-04-07 \
  --effective-date-to 2026-04-08 \
  --output human

fingrind \
  trial-balance \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --effective-date-to 2026-04-08 \
  --output human \
  --pdf-out ./acme-trial-balance.pdf
```

Checked-in report examples:
- [examples/trial-balance-response.json](./examples/trial-balance-response.json)
- [examples/account-ledger-response.json](./examples/account-ledger-response.json)
- [examples/period-summary-response.json](./examples/period-summary-response.json)
- [examples/trial-balance-human.txt](./examples/trial-balance-human.txt)
- [examples/account-ledger.csv](./examples/account-ledger.csv)
- [examples/period-summary-human.txt](./examples/period-summary-human.txt)

These report commands keep JSON as the default machine surface, while `--output human` and
`--output csv` render accounting-grade display scale for operators and spreadsheet tools.
`--pdf-out` writes a parallel PDF artifact to the requested path. If the report succeeds but that
artifact write fails, FinGrind still returns the report on stdout and emits a warning on the
diagnostics stream for the PDF path. FinGrind does not check PDF binaries into `docs/examples`;
the checked-in text and CSV examples remain the canonical review fixtures.

## Book Must Exist And Be Opened

```bash
fingrind \
  preflight-entry \
  --book-file ./missing.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./basic-posting-request.json
```

One deterministic rejection:

```json
{"status":"rejected","code":"posting-book-not-initialized","message":"The selected book does not exist or has not been initialized with open-book.","idempotencyKey":"idem-basic-1"}
```

## Accounts Must Be Declared First

Create this local file first:
- `./unknown-account-request.json`: copy [examples/unknown-account-request.json](./examples/unknown-account-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./unknown-account-request.json
```

One deterministic rejection is checked in at
[examples/account-state-violations-response.json](./examples/account-state-violations-response.json).
Posting-side account failures are now aggregated under `account-state-violations` so callers can
repair every reported account issue before retrying.

## Duplicate Rejection

```bash
fingrind \
  post-entry \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./basic-posting-request.json
```

One repeat commit response:

```json
{"status":"rejected","code":"duplicate-idempotency-key","message":"A posting with the same idempotency key already exists in this book.","idempotencyKey":"idem-basic-1"}
```

## Read The Request From Standard Input

```bash
cat ./basic-posting-request.json | \
  fingrind \
    preflight-entry \
    --book-file ./stdin.sqlite \
    --book-key-file ./acme.book-key \
    --request-file -
```

On Windows PowerShell, the same stdin flow is:

```powershell
Get-Content .\basic-posting-request.json -Raw | fingrind preflight-entry --book-file .\stdin.sqlite --book-key-file .\acme.book-key --request-file -
```

Remember that the selected book must already be initialized and the referenced accounts must
already be declared before that stdin-driven preflight can succeed.
`--request-file -` uses standard input for JSON, so it cannot be combined with
`--book-passphrase-stdin` in the same invocation.

## Reversal Request Template

Create this local file first:
- `./reversal-request.json`: copy [examples/reversal-request.json](./examples/reversal-request.json)

```bash
cat ./reversal-request.json
```

That file is a template. Replace `reversal.priorPostingId` with a real `postingId` returned by an
earlier commit in the same book, then preflight or commit it:

```bash
fingrind \
  preflight-entry \
  --book-file ./reversals.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./reversal-request.json
```

## Trigger A Deterministic Invalid Request

Create this local file first:
- `./invalid-empty-lines-request.json`: copy [examples/invalid-empty-lines-request.json](./examples/invalid-empty-lines-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./errors.sqlite \
  --book-key-file ./acme.book-key \
  --request-file ./invalid-empty-lines-request.json
```

One invalid-request response:

```json
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' for a minimal valid request document, or 'fingrind capabilities' for accepted enums and fields."}
```

## Invalid Cursor Is Rejected Deterministically

```bash
fingrind \
  list-postings \
  --book-file ./acme.sqlite \
  --book-key-file ./acme.book-key \
  --cursor definitely-not-a-valid-cursor
```

One deterministic error example is checked in at
[examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json).

## Wrong Key Fails Deterministically

```bash
fingrind generate-book-key-file --book-key-file ./wrong.book-key
fingrind \
  list-accounts \
  --book-file ./acme.sqlite \
  --book-key-file ./wrong.book-key
```

One deterministic error example is checked in at
[examples/book-authentication-failed-error.json](./examples/book-authentication-failed-error.json).
Wrong passphrases now return `book-authentication-failed` with exit `2`; SQLite storage symptoms
such as `SQLITE_NOTADB` do not leak to callers.

## Prompt Mode Requires A Supported Interactive Terminal

```bash
fingrind \
  open-book \
  --book-file ./prompt.sqlite \
  --book-passphrase-prompt
```

When no supported controlling terminal is available, FinGrind returns the deterministic
`interactive-prompt-unavailable` error with a repair hint pointing to `--book-key-file` or
`--book-passphrase-stdin`. One example is checked in at
[examples/interactive-prompt-unavailable-error.json](./examples/interactive-prompt-unavailable-error.json).
