---
afad: "4.0"
version: "0.39.0"
domain: USER_EXAMPLES
updated: "2026-05-17"
route:
  keywords: [fingrind, examples, open-book, rekey-book, inspect-book, declare-account, list-accounts, get-posting, list-postings, account-balance, trial-balance, account-ledger, period-summary, preflight, commit, stdin, reversal, print-plan-template, execute-plan]
  questions: ["show me a working fingrind example", "how do I inspect a book and query postings in fingrind", "how do I initialize a book and post in fingrind", "how do I export a trial balance in fingrind", "how do I send a fingrind request on stdin", "how do I run an atomic ledger plan in fingrind"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current public surface.
**Prerequisites**: Use the extracted self-contained FinGrind bundle launcher. In the examples
below, `fingrind` means a session-local shell function backed by that launcher, for example the
script under `./<bundle-root>/bin/fingrind` on macOS/Linux or
`.\<bundle-root>\bin\fingrind.ps1` on Windows. For source-driven local work, the equivalent
developer route is `./gradlew :cli:run --args="..."` on macOS/Linux or
`.\gradlew.bat :cli:run --args="..."` on Windows.

The public release bundle does not include `docs/examples/`. The runnable commands below therefore
use local working files such as `./declare-account-cash.json` and `./basic-posting-request.json`.
If you are in a source checkout, you can populate those files by copying the matching checked-in
fixtures under [examples/](./examples/). The command blocks below use POSIX shell line continuation
for readability; on Windows PowerShell, keep the same launcher, local file names, and command
order, but use PowerShell line continuation or one-line invocations.

For copy-paste use from one extracted bundle session, define `fingrind` once first.

```bash
fingrind() { "./<bundle-root>/bin/fingrind" "$@"; }
```

```powershell
function fingrind { & .\<bundle-root>\bin\fingrind.ps1 @args }
```

## Choose A Book Passphrase Source

For humans, the best non-persistent route is the interactive prompt:

```bash
fingrind \
  open-book \
  --book-file ./books/acme.sqlite \
  --entity-name "Acme Studio" \
  --entity-form COMPANY \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --accounting-basis ACCRUAL \
  --book-passphrase-prompt
```

For automation, generate a dedicated key file:

```bash
fingrind \
  generate-book-key-file \
  --book-key-file ./secrets/acme.book-key
```

Keep that key outside the book directory. The examples below use `./secrets/` for passphrase
material and `./books/` for encrypted books so ordinary book copies do not also copy the key.
If `./secrets/` or `./books/` does not exist yet, FinGrind creates it with owner-only
permissions. If either directory already exists, keep it owner-only before you reuse that path.

The generated key file contains one non-empty single-line UTF-8 passphrase.
One trailing newline is tolerated and stripped when loading an existing file.
Embedded control characters are rejected.
The key file must be protected with POSIX owner-only permissions (`0400` or `0600`) on
macOS/Linux, or a Windows owner-only ACL on Windows, and its containing directory must also
remain owner-only.

The interactive prompt route and the stdin route both enforce the same 4096-byte UTF-8 limit as
the key-file route.

For pipeline automation when a passphrase must flow over stdin, feed it from an existing
protected file or another non-history-bearing secret source instead of embedding the passphrase
literal on the shell command line. FinGrind accepts up to 4096 bytes on that stdin route:

```bash
cat ./secrets/acme.book-key | \
  fingrind \
    open-book \
    --book-file ./books/acme.sqlite \
    --entity-name "Acme Studio" \
    --entity-form COMPANY \
    --functional-currency EUR \
    --fiscal-year-start 01-01 \
    --accounting-basis ACCRUAL \
    --book-passphrase-stdin
```

On Windows PowerShell, the same stdin route is:

```powershell
Get-Content -Raw .\secrets\acme.book-key | fingrind open-book --book-file .\books\acme.sqlite --entity-name "Acme Studio" --entity-form COMPANY --functional-currency EUR --fiscal-year-start 01-01 --accounting-basis ACCRUAL --book-passphrase-stdin
```

## Initialize One Book

```bash
fingrind \
  open-book \
  --book-file ./books/acme.sqlite \
  --entity-name "Acme Studio" \
  --entity-form COMPANY \
  --owner-model MULTI_OWNER \
  --reporting-obligation-status INTERNAL_MANAGEMENT_ONLY \
  --tax-registration-status NOT_REGISTERED \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --accounting-basis ACCRUAL \
  --book-key-file ./secrets/acme.book-key
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/absolute/path/books/acme.sqlite","initializedAt":"2026-05-17T02:03:45.725027Z","bookIdentity":{"entityName":"Acme Studio","entityForm":"COMPANY","ownerModel":"MULTI_OWNER","reportingObligationStatus":"INTERNAL_MANAGEMENT_ONLY","taxRegistrationStatus":"NOT_REGISTERED","taxProfile":{"registrations":[],"taxCodeDefinitions":[]},"businessActivityTags":[],"functionalCurrency":"EUR","fiscalYearStart":"01-01","accountingBasis":"ACCRUAL"}}}
```

If the book is tax registered, copy
[examples/registered-tax-profile.json](./examples/registered-tax-profile.json) into your working
directory and add both `--tax-registration-status REGISTERED` and
`--tax-profile-file ./registered-tax-profile.json` to the same `open-book` command.

## Inspect Compatibility Before Mutating

```bash
fingrind \
  inspect-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key
```

One successful response is checked in at
[examples/inspect-book-response.json](./examples/inspect-book-response.json).
Use this command when an agent needs to know whether the selected book is initialized, compatible
with the current binary, and safe for `open-book`, `declare-account`, or `post-entry`.

## Rotate One Book Passphrase

Generate the replacement key file before you ask `rekey-book` to use it:

```bash
fingrind \
  generate-book-key-file \
  --book-key-file ./secrets/acme.rotated.book-key
```

```bash
fingrind \
  rekey-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --replacement-book-key-file ./secrets/acme.rotated.book-key
```

`--replacement-book-key-file` must point to an existing generated or operator-supplied secret
file. `rekey-book` also accepts `--replacement-book-passphrase-stdin` and
`--replacement-book-passphrase-prompt` for the replacement secret. The interactive replacement
prompt asks for the new passphrase twice and rejects mismatched entries. FinGrind creates one
same-directory rollback copy before rotating the book and restores the pre-rekey file
automatically if replacement-passphrase verification fails. If a crash or forced stop interrupts
that cleanup, the rollback artifact remains in the book directory under the old ciphertext until
you inspect or delete it; later opens warn when they detect that stale copy.

If you prefer the interactive replacement prompt instead of an existing file:

```bash
fingrind \
  rekey-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --replacement-book-passphrase-prompt
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/absolute/path/books/acme.sqlite","replacementPassphraseSource":"key-file","replacementBookKeyFile":"/absolute/path/secrets/acme.rotated.book-key"}}
```

## Back Up And Restore One Closed Protected Book

The supported backup path is a closed-book encrypted file copy. Stop using the book first, then
copy the `.sqlite` file to protected storage and keep the key file protected separately through
your normal secret-storage path.

```bash
mkdir -p ./backup/books
cp ./books/acme.sqlite ./backup/books/acme.sqlite
```

To restore, replace the closed live book with the encrypted copy before reopening it:

```bash
cp ./backup/books/acme.sqlite ./books/acme.sqlite
fingrind \
  inspect-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key
```

On Windows PowerShell, the same closed-book copy/restore flow is:

```powershell
New-Item -ItemType Directory -Force -Path .\backup\books | Out-Null
Copy-Item .\books\acme.sqlite .\backup\books\acme.sqlite
Copy-Item .\backup\books\acme.sqlite .\books\acme.sqlite -Force
fingrind inspect-book --book-file .\books\acme.sqlite --book-key-file .\secrets\acme.book-key
```

## Declare Accounts And Page The Registry

Create these local files first:
- `./declare-account-cash.json`: copy [examples/declare-account-cash.json](./examples/declare-account-cash.json)
- `./declare-account-revenue.json`: copy [examples/declare-account-revenue.json](./examples/declare-account-revenue.json)

```bash
fingrind \
  declare-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./declare-account-cash.json

fingrind \
  declare-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./declare-account-revenue.json

fingrind \
  list-accounts \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --limit 1
```

One successful paged response is checked in at
[examples/list-accounts-response.json](./examples/list-accounts-response.json).
If that response includes `payload.nextCursor`, pass the opaque value back through `--cursor` to
continue from the prior page without offset scans:

```bash
fingrind \
  list-accounts \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
the scaffold placeholder `replace-before-commit-effective-date` and default to
`"postingKind": "STANDARD"`.
The scaffold is agent-first: `actorType` is `AGENT`, and `effectiveDate`, `actorId`,
`commandId`, `idempotencyKey`, and `causationId` must all be replaced before submission.
A committed `idempotencyKey` is single-use per book.

For the concrete walkthrough below, reuse the checked-in example request:

- `./basic-posting-request.json`: copy [examples/basic-posting-request.json](./examples/basic-posting-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./basic-posting-request.json

fingrind \
  post-entry \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./basic-posting-request.json
```

One successful preflight response:

```json
{"status":"ok","payload":{"idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08"}}
```

That response is advisory, not a durable commit guarantee. `post-entry` still re-runs its
authoritative commit-time checks inside the write transaction.

One successful commit response:

```json
{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-08","recordedAt":"2026-04-08T12:00:00Z"}}
```

`payload.postingId` is generated by FinGrind as a UUID v7 value.
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
same `replace-before-commit-effective-date` placeholder inside its nested posting scaffold.
Its nested posting scaffold also defaults to `"postingKind": "STANDARD"`, and its provenance uses
the same `replace-before-commit-*` placeholders, which must be replaced before the plan is
submitted.

Or execute the checked-in runnable example plan directly against a fresh book:

- `./ledger-plan-request.json`: copy [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./acme-plan.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --result-detail full \
  --request-file ./ledger-plan-request.json
```

That plan:
- opens a new book
- declares cash and revenue
- posts one balanced entry
- asserts the resulting cash balance

`execute-plan` defaults to bounded summary output. The examples above pass `--result-detail full`
because the checked-in response fixtures below include the full execution journal.

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
  --book-key-file ./secrets/acme.book-key \
  --result-detail full \
  --request-file ./ledger-plan-query-request.json
```

That committed journal keeps `count`, `pageLimit`, optional `nextCursor`, `hasMore`, and grouped
`account` / `posting` facts for the successful query steps. One checked-in response is at
[examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json).

## Query The Committed History

```bash
fingrind \
  get-posting \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --posting-id "<postingId-from-the-commit-response>"

fingrind \
  list-postings \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code 1000 \
  --limit 25

fingrind \
  account-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code 1000 \
  --limit 25 \
  --cursor "<nextCursor-from-the-prior-page>"
```

## Run Office-Worker Reports

```bash
fingrind \
  trial-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --effective-date-to 2026-04-08 \
  --output human

fingrind \
  account-ledger \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code 1000 \
  --effective-date-from 2026-04-07 \
  --effective-date-to 2026-04-08 \
  --output csv

fingrind \
  period-summary \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --effective-date-from 2026-04-07 \
  --effective-date-to 2026-04-08 \
  --output human

fingrind \
  trial-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
`--pdf-out` writes a parallel PDF artifact to the requested path. If the report succeeds and JSON
is selected on stdout, the success envelope also publishes the normalized PDF under
`artifacts[]`. Diagnostics emit an info message with the same normalized written path. If the
artifact write fails, FinGrind still returns the report on stdout and emits a warning on the
diagnostics stream for the PDF path. FinGrind does not check PDF binaries into `docs/examples`;
the checked-in text and CSV examples remain the canonical review fixtures.

## Book Must Exist And Be Opened

```bash
fingrind \
  preflight-entry \
  --book-file ./missing.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
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
    --book-file ./books/stdin.sqlite \
    --book-key-file ./secrets/acme.book-key \
    --request-file -
```

On Windows PowerShell, the same stdin flow is:

```powershell
Get-Content .\basic-posting-request.json -Raw | fingrind preflight-entry --book-file .\books\stdin.sqlite --book-key-file .\secrets\acme.book-key --request-file -
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
  --book-key-file ./secrets/acme.book-key \
  --request-file ./reversal-request.json
```

## Trigger A Deterministic Invalid Request

Create this local file first:
- `./invalid-empty-lines-request.json`: copy [examples/invalid-empty-lines-request.json](./examples/invalid-empty-lines-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./errors.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./invalid-empty-lines-request.json \
  --output json
```

One invalid-request response:

```json
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' for the canonical request scaffold, then replace its scaffold placeholders before submission, or run 'fingrind capabilities' for accepted enums and fields.","details":{"violations":["Journal entry must contain at least one line."]}}
```

## Invalid Cursor Is Rejected Deterministically

```bash
fingrind \
  list-postings \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --cursor definitely-not-a-valid-cursor \
  --output json
```

One deterministic error example is checked in at
[examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json).

## Protected-Book Verification Fails Deterministically

```bash
fingrind generate-book-key-file --book-key-file ./secrets/wrong.book-key
fingrind \
  list-accounts \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/wrong.book-key \
  --output json
```

One deterministic error example is checked in at
[examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json).
Wrong passphrases, damaged or truncated protected books, and unsupported protected SQLite variants
now return `protected-book-verification-failed` with exit `2`; SQLite storage symptoms such as
`SQLITE_NOTADB` do not leak to callers.

## Prompt Mode Requires A Supported Interactive Terminal

```bash
fingrind \
  open-book \
  --book-file ./prompt.sqlite \
  --entity-name "Acme Studio" \
  --entity-form COMPANY \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --accounting-basis ACCRUAL \
  --book-passphrase-prompt
```

When no supported controlling terminal is available, FinGrind returns the deterministic
`interactive-prompt-unavailable` error with a repair hint pointing to `--book-key-file` or
`--book-passphrase-stdin`. One example is checked in at
[examples/interactive-prompt-unavailable-error.json](./examples/interactive-prompt-unavailable-error.json).
