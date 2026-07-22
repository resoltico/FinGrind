---
afad: "5.0.1"
version: "0.61.0"
domain: USER_EXAMPLES
updated: "2026-07-22"
route:
  keywords: [fingrind, examples, open-book, rekey-book, inspect-book, declare-account, list-accounts, get-posting, list-postings, account-balance, trial-balance, account-ledger, period-summary, financial-position, inventory-valuation, income-statement, cash-flow-statement, changes-in-equity, preflight, commit, stdin, reversal, print-plan-template, execute-plan]
  questions: ["show me a working fingrind example", "how do I inspect a book and query postings in fingrind", "how do I initialize a book and post in fingrind", "how do I export a trial balance in fingrind", "how do I send a fingrind request on stdin", "how do I run an atomic ledger plan in fingrind"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current public surface.
**Prerequisites**: Use the extracted published Linux bundle launcher or one equivalent local
launcher surface. In the examples below, `fingrind` means a session-local shell function backed by
that launcher, for example the script under `./<bundle-root>/bin/fingrind` on a published Linux
bundle. On Windows, use the same command order with either the published container wrapper from
[USER_CONTAINER.md](./USER_CONTAINER.md) or one source-checkout launcher such as
`.\scripts\source-checkout-cli.ps1`. For source-driven local work, the equivalent developer route
is `./gradlew :cli:run --args="..."` on macOS/Linux or `.\gradlew.bat :cli:run --args="..."` on
Windows.

The public release bundle does not include `docs/examples/`. The runnable commands below therefore
use local working files such as `./declare-account-supplemental-cash-reserve.json` and
`./basic-posting-request.json`.
If you are in a source checkout, you can populate those files by copying the matching checked-in
fixtures under [examples/](./examples/). The command blocks below use POSIX shell line continuation
for readability; on Windows PowerShell, keep the same launcher, local file names, and command
order, but use PowerShell line continuation or one-line invocations.

For copy-paste use from one extracted Linux bundle session, define `fingrind` once first.

```bash
fingrind() { "./<bundle-root>/bin/fingrind" "$@"; }
```

```powershell
function fingrind { docker run --rm -i -v "${PWD}:/workspace" -w /workspace ghcr.io/resoltico/fingrind:<tag> @args }
```

## Choose A Book Passphrase Source

For operators, the best non-persistent route is the interactive prompt:

```bash
fingrind \
  open-book \
  --book-file ./books/acme.sqlite \
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

This prompt route is for `--output text`. If you request `json` or `csv` together with
`--book-passphrase-prompt`, FinGrind rejects the invocation deterministically as
`invalid-request` and tells you to switch back to `--output text` or to a non-interactive
passphrase source.

For automation, generate a dedicated key file:

```bash
fingrind \
  generate-book-key-file \
  --new-book-key-file ./secrets/acme.book-key
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

Before the first `open-book`, prepare a separate nonempty owner-only UTF-8 founder passphrase
file at `./secrets/founder.passphrase`. The examples bind its newly created no-clobber encrypted
Ed25519 key at `./secrets/founder.fgatk` to the displayed founder UUID. Do not reuse the book key
file or its passphrase for that credential.

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
    --book-template-id OWNER_MANAGED_SERVICE \
    --accounting-basis CASH \
    --functional-currency EUR \
    --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 \
    --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
    --attestation-founder-key-file ./secrets/founder.fgatk \
    --attestation-founder-passphrase-file ./secrets/founder.passphrase \
    --book-passphrase-stdin
```

On Windows PowerShell, the same stdin route is:

```powershell
Get-Content -Raw .\secrets\acme.book-key | fingrind open-book --book-file .\books\acme.sqlite --entity-name "Acme Studio" --book-template-id OWNER_MANAGED_SERVICE --accounting-basis CASH --functional-currency EUR --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-founder-key-file .\secrets\founder.fgatk --attestation-founder-passphrase-file .\secrets\founder.passphrase --book-passphrase-stdin
```

## Initialize One Book

```bash
fingrind \
  open-book \
  --book-file ./books/acme.sqlite \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 \
  --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/founder.passphrase \
  --book-key-file ./secrets/acme.book-key
```

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/workspace/books/acme.sqlite","initializedAt":"2026-05-17T02:03:45.725027Z","bookIdentity":{"entityName":"Acme Studio","accountingKernelProfile":"internal-management-bookkeeping-kernel","accountingBasis":"CASH","accountingFrameworkPosition":"NON_STATUTORY_INTERNAL_MANAGEMENT","entityForm":"OWNER_MANAGED_SINGLE_ENTITY","bookTemplateId":"OWNER_MANAGED_SERVICE","functionalCurrency":"EUR","fiscalYearStart":"01-01","bookStartEffectiveDate":"2026-01-01"}}}
```

That initialized book starts from the explicitly selected owner-managed service seed template with
an explicit cash basis. Use `--accounting-basis ACCRUAL` when you want the accrual
owner-managed service chart instead. To initialize the trading template, select
`OWNER_MANAGED_TRADING` and add `--inventory-costing WEIGHTED_AVERAGE`; the selected trading seed
then owns inventory quantity, cost-of-sales, and inventory-adjustment accounts. Review the seeded
accounts with `list-accounts` before you declare any supplemental accounts.

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
with the current binary, which hard-break migration policy governs the current format line, and
whether the path is safe for `open-book`, `declare-account`, or `post-entry`.

## Rotate One Book Passphrase

```bash
fingrind \
  rekey-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --new-book-key-file ./secrets/acme.rotated.book-key \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

`--new-book-key-file` must name an absent target on a filesystem that supports atomic no-replace
publication. FinGrind generates and publishes the fresh owner-only secret only after it has staged
and verified the re-encrypted book. `rekey-book` does not accept a replacement secret through
standard input or an interactive prompt. FinGrind creates one same-directory rollback copy before
rotating the book and restores the pre-rekey file automatically if staged verification fails. If a
crash or forced stop interrupts cleanup, the rollback artifact remains in the book directory under
the old ciphertext until you inspect or delete it; later opens warn when they detect that stale
copy.

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/workspace/books/acme.sqlite","newBookKeyFile":"/workspace/secrets/acme.rotated.book-key"},"artifacts":[{"format":"book-key-file","path":"/workspace/secrets/acme.rotated.book-key"}]}
```

## Back Up And Restore One Closed Protected Book

Stop using the book first. The canonical encrypted backup flow is one verified backup pair:

```bash
fingrind \
  backup-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --backup-file ./backup/books/acme.sqlite \
  --new-backup-key-file ./backup/secrets/acme.book-key \
  --backup-id 86ba4e4e-e08d-45e5-9c42-631d0121d6ef \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

That command refuses to run when the live book has blocking SQLite sidecars or stale rollback
artifacts beside it.
If `./backup/books/` or `./backup/secrets/` does not exist yet, FinGrind creates those parent
directories with owner-only protection. If either directory already exists, keep it owner-only
before you reuse that backup path.

To restore, verify the backup pair into a new absent live-book path:

```bash
fingrind \
  restore-book \
  --book-file ./books/acme-restored.sqlite \
  --new-book-key-file ./secrets/acme-restored.book-key \
  --backup-file ./backup/books/acme.sqlite \
  --backup-key-file ./backup/secrets/acme.book-key \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

After restore completes, reopen `./books/acme-restored.sqlite` with `./secrets/acme-restored.book-key`
because the restored encrypted book is re-encrypted under that destination secret.
If the selected live-book or live-key parent directory does not exist yet, FinGrind creates it
with owner-only protection before publishing the restored pair. If either directory already
exists, keep it owner-only before you reuse that restore target.
The selected `--book-file` must remain absent through final publication. If another process
creates it while restore is staging, FinGrind leaves that book unchanged, removes its own staged
artifacts, and rejects the restore.

## Verify One Attested Book

Verify the immutable chain before acting on a copied or recovered book. This does not modify the
book. `--require-clean-attestation` additionally refuses review findings.

```bash
fingrind \
  verify-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --require-clean-attestation
```

See [USER_BOOK_ATTESTATION.md](./USER_BOOK_ATTESTATION.md) for receipt export, receipt
verification, and the exact backup acknowledgement retry rule.

## Declare Supplemental Accounts And Page The Registry

Create these local files first:
- `./declare-account-supplemental-cash-reserve.json`: copy
  [examples/declare-account-supplemental-cash-reserve.json](./examples/declare-account-supplemental-cash-reserve.json)
- `./declare-account-supplemental-misc-revenue.json`: copy
  [examples/declare-account-supplemental-misc-revenue.json](./examples/declare-account-supplemental-misc-revenue.json)

The seed template already includes `cash` and `service-revenue`. These checked-in declaration
examples show how to add supplemental accounts on top of that seeded chart, for example
`cash-reserve` and `misc-revenue`.

```bash
fingrind \
  declare-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./declare-account-supplemental-cash-reserve.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase

fingrind \
  declare-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./declare-account-supplemental-misc-revenue.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase

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

## Amend Or Retire An Account

Use an amendment only while the account has no posted history, tax-registration binding, or child
account. Generate the canonical request, replace its placeholders with the new definition, then
submit it against the selected book:

```bash
fingrind \
  print-request-template amend-account > ./cash-reserve-amend.json

fingrind \
  amend-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./cash-reserve-amend.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

To retire an account, it must have a zero current balance and no live tax-registration or
child-account binding. Retirement keeps its identity and journal history; it blocks new ordinary
authored use, while a historical `record-reversal` remains admissible.

```bash
printf '%s\n' '{"accountCode":"cash-reserve"}' > ./retire-account.json

fingrind \
  retire-account \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./retire-account.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

## Preflight And Commit One Entry

You can generate a new template at any time:

```bash
fingrind \
  print-request-template > ./request.json
```

That generated scaffold uses the same canonical content as the checked-in
[examples/request-template.json](./examples/request-template.json) companion example. Both
intentionally publish one placeholder-first sample document and default to one minimal
`"entryKind": "SALE_SETTLED"` posting with `cashAccountCode`, `revenueAccountCode`, and `amount` over the
seeded starter accounts.
When the selected book uses `OWNER_MANAGED_TRADING`, fill the same sale request with
`inventoryRelief.inventoryAccountCode`, `inventoryRelief.costOfSalesAccountCode`, and
`inventoryRelief.quantity` so the typed sale records revenue plus inventory relief together.
Trading books can likewise stay on the typed inventory-acquisition path through
`print-request-template record-purchase-settled` and
`print-request-template record-purchase-on-credit`, which default to one inventory purchase shape
with `quantity` and `unitCost` instead of a raw adjustment journal. Use the matching
`record-inventory-capitalization-*` templates for landed costs, `record-inventory-write-down` for
carrying-cost impairment, `record-inventory-shrinkage` for a quantity loss with executor-derived
cost, and `record-inventory-count-increase` for a count-discovered quantity increase.
The scaffold is request-first rather than demo-runnable: `evidence.approvals` starts as an empty
array that callers may populate when one posting requires
explicit approval references, and every `replace-before-commit-*` evidence or provenance token must be
replaced before real-world use.
A committed `idempotencyKey` is single-use per book.
Typed business-entry commands remain the primary write surface, and the raw direct-journal boundary
remains available through `print-request-template post-entry` when you need it explicitly.

For the concrete walkthrough below, reuse the checked-in example request:

- `./basic-posting-request.json`: copy [examples/basic-posting-request.json](./examples/basic-posting-request.json)

```bash
fingrind \
  preflight-entry \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./basic-posting-request.json

fingrind \
  record-sale-settled \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./basic-posting-request.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

One successful preflight response:

```json
{"status":"ok","payload":{"idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-07","resolvedJournal":{"expandedLines":{"effectiveDate":"2026-04-07","lines":[{"accountCode":"cash","side":"DEBIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}},{"accountCode":"service-revenue","side":"CREDIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}}]},"classification":{"eventClass":"SETTLED_SALE","anchorSignature":[{"accountRole":"CASH","side":"DEBIT"},{"accountRole":"REVENUE","side":"CREDIT"}],"containedTypedEvents":["SETTLED_SALE"],"hasCashLine":true,"evidenceClass":"CASH_SETTLEMENT","structural":{"adoptionOpeningEntry":false}}}}}
```

That response is advisory, not a durable commit guarantee. The matching commit command such as
`record-sale-settled` re-runs its authoritative commit-time checks inside the write transaction. Raw
`post-entry` does the same when the submitted request itself is a direct journal.

One successful commit response:

```json
{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-07","recordedAt":"2026-04-07T12:00:00Z","idempotentReplay":false,"resolvedJournal":{"expandedLines":{"effectiveDate":"2026-04-07","lines":[{"accountCode":"cash","side":"DEBIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}},{"accountCode":"service-revenue","side":"CREDIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}}]},"classification":{"eventClass":"SETTLED_SALE","anchorSignature":[{"accountRole":"CASH","side":"DEBIT"},{"accountRole":"REVENUE","side":"CREDIT"}],"containedTypedEvents":["SETTLED_SALE"],"hasCashLine":true,"evidenceClass":"CASH_SETTLEMENT","structural":{"adoptionOpeningEntry":false}}}}}
```

`payload.postingId` is generated by FinGrind as a UUID v7 value.
The request shape is checked in at [examples/basic-posting-request.json](./examples/basic-posting-request.json).
One example committed response is checked in at
[examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json).
This example uses the sale-first request language with `cashAccountCode`, `revenueAccountCode`,
and one exact positive `amount`.

## Generate Or Run A Ledger Plan

Generate the general plan scaffold:

```bash
fingrind \
  print-plan-template > ./plan.json
```

Like `print-request-template`, this scaffold uses the same canonical content as the checked-in
[examples/ledger-plan-template.json](./examples/ledger-plan-template.json) companion example.
It initializes the book and contains one placeholder-first sale. Replace every placeholder before
real-world use.

Generate a context-specific atomic setup only when its prerequisites are needed:

```bash
fingrind print-plan-template tax-setup > ./tax-setup-plan.json
fingrind print-plan-template fixed-asset-setup > ./fixed-asset-setup-plan.json
fingrind print-plan-template financing-setup > ./financing-setup-plan.json
```

The tax setup declares payable and recoverable accounts before its tax registration. It is
structural, not a Latvian VAT determination: verify registration, rate, deduction,
place-of-supply, invoice, and filing treatment against the primary sources listed in
[DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md).

Or execute the checked-in runnable example plan against the initialized book established above:

- `./ledger-plan-request.json`: copy [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --output json \
  --result-detail full \
  --request-file ./ledger-plan-request.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

That plan:
- declares the required VAT payable account
- declares the required VAT recoverable account
- declares the tax registration only after its prerequisite accounts exist

If any setup step is refused, `execute-plan` rolls the complete setup back and returns the ordered
failure journal. A successful plan exposes each declaration in that same journal; it does not hide
account creation inside `declare-tax-registration`.

`execute-plan` defaults to a bounded text summary. The examples above pass `--output json` and
`--result-detail full` because the checked-in response fixtures below include the machine envelope
and the full execution journal.

Checked-in plan examples:
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json): checked-in source-copy companion for the general plan scaffold
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json): primary runnable plan example that creates the tax setup atomically
- [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json): follow-on plan that pages the initialized account registry
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json)
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json)
- [examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json)

After the tax-setup plan succeeds, use the checked-in query plan to inspect paginated registry state:

- `./ledger-plan-query-request.json`: copy [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --output json \
  --result-detail full \
  --request-file ./ledger-plan-query-request.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
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
  --account-code cash \
  --limit 25

fingrind \
  account-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code cash
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
  --account-code cash \
  --limit 25 \
  --cursor "<nextCursor-from-the-prior-page>"
```

## Run Office-Worker Reports

```bash
fingrind \
  trial-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --effective-date-as-of 2026-04-07 \
  --output text

fingrind \
  account-ledger \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code cash \
  --effective-date-from 2026-04-07 \
  --effective-date-to 2026-04-07 \
  --limit 50 \
  --output csv

fingrind \
  period-summary \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --period-start 2026-04-07 \
  --period-end 2026-04-07 \
  --output text

fingrind \
  inventory-valuation \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --as-of 2026-04-07 \
  --movements \
  --output text

fingrind \
  cash-flow-statement \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --period-start 2026-04-07 \
  --period-end 2026-04-07 \
  --comparative prior-period \
  --output text

fingrind \
  trial-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --effective-date-as-of 2026-04-07 \
  --comparative prior-period \
  --output text \
  --pdf-out ./acme-trial-balance.pdf
```

For JSON pagination, pass the opaque `payload.nextCursor` from a previous account-ledger response back unchanged. A cursor continues the ledger's ascending keyset order; it is not a read snapshot.

```bash
fingrind \
  account-ledger \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --account-code cash \
  --limit 50 \
  --cursor '<nextCursor-from-the-previous-response>' \
  --output json
```

Checked-in report examples:
- [examples/trial-balance-response.json](./examples/trial-balance-response.json)
- [examples/account-ledger-response.json](./examples/account-ledger-response.json)
- [examples/period-summary-response.json](./examples/period-summary-response.json)
- [examples/trial-balance-text.txt](./examples/trial-balance-text.txt)
- [examples/account-ledger.csv](./examples/account-ledger.csv)
- [examples/period-summary-text.txt](./examples/period-summary-text.txt)

These report commands keep JSON as the default machine surface, while `--output text` and
`--output csv` render accounting-grade display scale for operators and spreadsheet tools.
`cash-flow-statement` is the bounded statement of cash receipts and payments and classifies
movement into operating, investing, and financing sections from the declared counterpart accounts.
`inventory-valuation` reports exact on-hand quantity and carrying value per inventory account; its
rounded moving-average unit-cost projection is informational and is never multiplied back into the
carrying value.
`--pdf-out` writes a parallel PDF artifact to the requested path. If the report succeeds and JSON
is selected on stdout, the success envelope also publishes one canonical absolute PDF path under
`artifacts[]`. `--output text --pdf-out <path>` writes one artifact confirmation block to stdout
instead of the full report body, and `--output csv` cannot be paired with `--pdf-out`. If the
artifact write fails, the command returns one deterministic `pdf-export-failure` error instead of
publishing a successful report. FinGrind does not check PDF binaries into `docs/examples`;
the checked-in text and CSV examples remain the canonical review fixtures.

For safe retries, request input, reversal, and deterministic failure-recovery flows, continue with
[USER_ENTRY_WORKFLOWS.md](./USER_ENTRY_WORKFLOWS.md).
