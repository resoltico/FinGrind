---
afad: "5.0.1"
version: "0.61.0"
domain: USER_ENTRY_WORKFLOWS
updated: "2026-07-22"
route:
  keywords: [fingrind, idempotency, stdin, reversal, preflight, commit, rejection, invalid-request, cursor, protected-book, interactive-prompt]
  questions: ["how do I retry a fingrind posting safely", "how do I send a fingrind request on stdin", "how do I reverse a fingrind posting", "how do I diagnose a fingrind rejection"]
---

# Entry And Recovery Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows for posting retries, request input, reversals, and deterministic failure recovery.
**Prerequisites**: Start with [USER_EXAMPLES.md](./USER_EXAMPLES.md) to open a protected book and establish the local `fingrind` launcher used below.

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

Checked-in machine and operator examples live at [examples/account-state-violations-response.json](./examples/account-state-violations-response.json) and [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt). Posting-side account failures are aggregated under `account-state-violations` so callers can repair every reported account issue before retrying; the machine envelope keeps a stable summary while the ordered `details.violations[]` items and the text-mode `Issue N | <code>` sections carry the actionable per-issue repair data. This example uses the same sale-first surface as the runnable posting flow so the rejection stays anchored to the primary write language.

## Entry-Semantics Rejections Explain Every Issue

Create this local file first:
- `./entry-semantics-multi-violation-request.json`: copy [examples/entry-semantics-multi-violation-request.json](./examples/entry-semantics-multi-violation-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./entry-semantics-multi-violation-request.json \
  --output text
```

Checked-in machine and operator examples live at [examples/entry-semantics-violations-response.json](./examples/entry-semantics-violations-response.json) and [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt). The machine envelope keeps a stable family summary plus ordered `details.violations[]` items, while the text surface renders the same family as one `Summary` header plus one `Issue N | <code>` section per violation so an operator can repair every problem without scraping one concatenated paragraph.

## Idempotent Replay

```bash
fingrind \
  record-sale-settled \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./basic-posting-request.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

One repeat commit response for the exact same normalized request:

```json
{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-07","recordedAt":"2026-04-07T12:00:00Z","idempotentReplay":true,"resolvedJournal":{"expandedLines":{"effectiveDate":"2026-04-07","lines":[{"accountCode":"cash","side":"DEBIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}},{"accountCode":"service-revenue","side":"CREDIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}}]},"classification":{"eventClass":"SETTLED_SALE","anchorSignature":[{"accountRole":"CASH","side":"DEBIT"},{"accountRole":"REVENUE","side":"CREDIT"}],"containedTypedEvents":["SETTLED_SALE"],"hasCashLine":true,"evidenceClass":"CASH_SETTLEMENT","structural":{"adoptionOpeningEntry":false}}}}}
```

If the same `idempotencyKey` is reused with a different normalized request, the book still rejects it with `idempotency-key-conflict`.

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

Remember that the selected book must already be initialized and the referenced accounts must already be declared before that stdin-driven preflight can succeed. `--request-file -` uses standard input for JSON, so it cannot be combined with `--book-passphrase-stdin` in the same invocation.

## Reversal Request Template

Create this local file first:
- `./reversal-request.json`: copy [examples/reversal-request.json](./examples/reversal-request.json)

```bash
cat ./reversal-request.json
```

That file is a template. Replace `reversal.priorPostingId` with a real `postingId` returned by an earlier commit in the same book, keep `evidence.sourceDocuments[]` pointed at the reversal's own supporting document, then preflight or commit it:

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
{"status":"error","code":"invalid-request","message":"Journal entry must contain at least one line.","hint":"Run 'fingrind print-request-template' for the canonical request scaffold, then replace its placeholder evidence and provenance values before real-world use, or run 'fingrind capabilities' for accepted enums and fields.","details":{"violations":["Journal entry must contain at least one line."]}}
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

One deterministic error example is checked in at [examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json).

## Protected-Book Verification Fails Deterministically

```bash
fingrind generate-book-key-file --new-book-key-file ./secrets/wrong.book-key
fingrind \
  list-accounts \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/wrong.book-key \
  --output json
```

One deterministic error example is checked in at [examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json). Wrong passphrases, damaged or truncated protected books, and unsupported protected SQLite variants now return `protected-book-verification-failed` with exit `6`; SQLite storage symptoms such as `SQLITE_NOTADB` do not leak to callers.

## Prompt Mode Requires A Supported Interactive Terminal

Before this command, prepare a separate nonempty owner-only UTF-8 founder passphrase file at
`./secrets/founder.passphrase`. FinGrind creates the absent founder credential at
`./secrets/founder.fgatk` exactly once; do not reuse the book passphrase for that credential.

```bash
fingrind \
  open-book \
  --book-file ./prompt.sqlite \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 \
  --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/founder.passphrase \
  --book-passphrase-prompt
```

When no supported controlling terminal is available, FinGrind returns the deterministic `interactive-prompt-unavailable` error with a repair hint pointing to `--book-key-file` or `--book-passphrase-stdin` and exits with code `5`. One example is checked in at [examples/interactive-prompt-unavailable-error.txt](./examples/interactive-prompt-unavailable-error.txt).
