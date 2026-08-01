---
afad: "5.0.1"
version: "0.62.0"
domain: USER_EXAMPLES
updated: "2026-07-30"
route:
  keywords: [fingrind, examples, windows-x86_64, bundle, open-book, rekey-book, inspect-book, declare-account, list-accounts, get-posting, list-postings, account-balance, trial-balance, account-ledger, period-summary, financial-position, inventory-valuation, income-statement, cash-flow-statement, changes-in-equity, preflight, commit, stdin, reversal, print-plan-template, execute-plan, source-artifact-identity-duplicated, source-artifact-identity-changed]
  questions: ["show me a working fingrind example", "how do I inspect a book and query postings in fingrind", "how do I initialize a book and post in fingrind", "how do I export a trial balance in fingrind", "how do I send a fingrind request on stdin", "how do I run an atomic ledger plan in fingrind", "how do I choose backup and restore pair target names", "what does source-artifact-identity-changed mean"]
---

# Example Workflows

**Purpose**: Provide copy-paste FinGrind CLI flows that work against the current public surface.
**Prerequisites**: Use the extracted published Linux bundle launcher or one equivalent local
launcher surface. In the examples below, `fingrind` means a session-local shell function backed by
that launcher, for example the script under `./<bundle-root>/bin/fingrind` on a published Linux
bundle. On Windows x86_64, use the same command order with the `bin\fingrind.ps1` launcher from
an extracted published Windows bundle, the published container wrapper from
[USER_CONTAINER.md](./USER_CONTAINER.md), or a source-checkout launcher such as
`.\scripts\source-checkout-cli.ps1`. For source-driven local work, the equivalent developer route
is `./gradlew :cli:run --args="..."` on macOS/Linux or `.\gradlew.bat :cli:run --args="..."` from
PowerShell 7 (`pwsh`) on Windows. The Windows Gradle wrapper requires PowerShell 7 or later.

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

For copy-paste use from one extracted Windows x86_64 bundle session, define `fingrind` once first.

```powershell
function fingrind { & ".\<bundle-root>\bin\fingrind.ps1" @Args }
```

For the published container image on Windows PowerShell, use:

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
mkdir -p -m 700 ./secrets ./books
fingrind \
  generate-book-key-file \
  --new-book-key-file ./secrets/acme.book-key
```

Keep that key outside the book directory. The examples below use `./secrets/` for passphrase
material and `./books/` for encrypted books so ordinary book copies do not also copy the key.
`generate-book-key-file` requires the `./secrets/` parent to exist and remain owner-only; it
never creates or weakens that secret directory. This setup creates both directories explicitly.
On Windows PowerShell, use the owner-only directory preparation in
[USER_QUICK_START.md](./USER_QUICK_START.md#3-create-a-key-file) before generating the key.

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
standard input or an interactive prompt. FinGrind may use private workflow-owned pre-final
material while it verifies a staged rotation, but that material remains immutable owner-record
evidence and is not user-managed. If an interruption leaves recovery evidence or final-pair
completion uncertain, never rename, overwrite, delete, recreate, reuse, or treat it as an
ordinary book; rerun the exact original `rekey-book` operation with its complete original inputs
so FinGrind can classify or recover it safely.

One successful response:

```json
{"status":"ok","payload":{"bookFile":"/workspace/books/acme.sqlite","newBookKeyFile":"/workspace/secrets/acme.rotated.book-key","pairPublicationCompletion":"published","pairPublicationRetention":{"bookPublication":{"path":"/workspace/books/acme.sqlite","retainedStage":"/workspace/books/.acme-rekey-stage"},"generatedSecretPublication":{"path":"/workspace/secrets/acme.rotated.book-key","retainedStage":"/workspace/secrets/.acme-key-stage"}},"attestationCommit":{"operationOrder":"1","operationHead":"<attestation-operation-head>"}},"artifacts":[{"format":"book-key-file","path":"/workspace/secrets/acme.rotated.book-key","retainedStage":"/workspace/secrets/.acme-key-stage"}]}
```

`pairPublicationCompletion` is `published` for this new durable pair and `recovered` only when
the same rekey tuple reconciles an earlier completion-uncertain pair without another rotation
mutation. `pairPublicationRetention` is mandatory for both outcomes: its four paths are immutable
facts, not cleanup targets. Pair errors also always publish nullable
`details.pairPublication.pairPublicationRetention`; when non-null, its two member paths bind
exactly to the reported final paths, and `null` never permits cleanup. If FinGrind returns
`protected-book-pair-publication-uncertain`, retain FinGrind pair evidence and both reported final paths. When FinGrind has verified the
operation-bound pair, rerun the exact same operation with complete original source, target, and
secret inputs. FinGrind resumes only stages registered by that owner record. Never rename,
overwrite, delete, recreate, reuse, or manually alter pair evidence or either final member; do not
start a fresh pair. When `recoveryRecordState` is non-null, retain FinGrind's recovery material
too. Rekey recovery verifies the generated-key pair before it tries the prior key.
If FinGrind instead returns `maintenance-recovery-pending`, no new stage or pair mutation began.
Use its non-null `details.{recoveryOperation,bookTarget,generatedSecretTarget}` only to identify
the required recovery operation and exact canonical targets. Rerun that operation with complete
original source, target, and secret inputs; the details do not recreate a source, backup ID,
credential, or secret. Never rename, overwrite, delete, recreate, or manually clean the evidence.

If retained evidence cannot establish a safe final-member state, FinGrind instead returns
`protected-book-pair-publication-evidence-blocked`. Both member states are `unestablished`; its
always-present nullable pair retention is `null` when no authoritative stage fact is safe to
report. Do not rerun or reconstruct a workflow from that diagnostic. Preserve it for independent
investigation.

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

That command refuses to run when the live book has blocking SQLite sidecars or unreconciled
external pair evidence beside it. For `backup-book`, `restore-book`, and `rekey-book`, each
existing selected protected-book or book-key artifact parent is validation-only: it and its
resolved ancestry must already be real, private owner-only, and non-mutable. FinGrind never
permission- or ACL-repairs it. Only an absent final-target parent may be created: it preflights
the creation ancestry, atomically creates the parent with POSIX `0700`, and postvalidates the
canonical parent and full ancestry. A lifecycle source parent must already exist. ACL-only
final-target creation fails closed as
`artifact-path-invalid` with
`details.pathFailure: "atomic-owner-only-protocol-file-creation-unsupported"`. An intermediate
alias is never admitted. Before canonicalization, FinGrind scans every lexical component from the
root through the selected parent without following links and refuses any symbolic-link or
non-directory component, including a direct-parent alias; a leaf symlink is also refused.
A lifecycle source leaf must already be a regular non-symlink file before final-target preparation.
The complete selected source set, including a selected key-file source, must name distinct physical
files. A later source role that aliases an earlier source is refused as exit-`6`
`artifact-path-invalid` with `details.pathFailure: "source-artifact-identity-duplicated"`.
After FinGrind holds all source exclusions, it revalidates each locked physical identity before
target admission. A replacement or substitution is exit-`6` `artifact-path-invalid` with
`details.pathFailure: "source-artifact-identity-changed"`; restore the trustworthy intended
source, keep every source stable, and rerun the complete maintenance command.

Existing final targets are compared with `Files.isSameFile`; one physical object is
`pair-targets-conflict`. For two absent leaves in the same physical parent, exact raw leaf equality
or a collision after canonical Unicode decomposition plus root-locale case mapping is the same
rejection. Other distinct leaves, including Unicode, spaces, punctuation, and leading dashes,
remain valid targets when the filesystem admits them.
This initial admission occurs after maintenance has admitted every selected parent, including any
permitted missing-parent creation, and before any final target, retained lease-control file, stage,
capability witness, reservation, claim, or pair-recovery-evidence artifact is created. An eligible
missing parent created during admission remains rather than being removed after a refusal.

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
The selected `--book-file` must remain absent through final publication. If another process
creates it while restore is staging, FinGrind leaves that book unchanged, retains every
materialized stage as immutable evidence, and rejects the restore; it does not remove stages from
either path.
If either backup, restore, or rekey instead returns
`protected-book-pair-publication-uncertain`, this is not a normal collision or a retry task:
preserve FinGrind pair evidence and both reported final paths. When FinGrind has verified the
retained operation-bound pair, rerun the exact same operation with its complete original inputs,
including exactly those paths. Malformed, legacy, or internally inconsistent current evidence
instead fails closed as `protected-book-pair-publication-evidence-blocked` without establishing a
verified original operation. When `recoveryRecordState` is non-null, preserve FinGrind's recovery
material too.
The checked-in [pair-publication uncertainty example](./examples/protected-book-pair-publication-uncertain-error.json)
shows the two-member diagnostic shape. Never rename, overwrite, delete, recreate, or manually
clean pair evidence or either final member; do not start a fresh
pair.

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
verification, exact backup acknowledgement retry, and protected-book pair-recovery rules.

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
{"status":"ok","payload":{"postingId":"01963c70-8d65-7b56-8a64-3c92745d8f72","idempotencyKey":"idem-basic-1","effectiveDate":"2026-04-07","recordedAt":"2026-04-07T12:00:00Z","idempotentReplay":false,"resolvedJournal":{"expandedLines":{"effectiveDate":"2026-04-07","lines":[{"accountCode":"cash","side":"DEBIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}},{"accountCode":"service-revenue","side":"CREDIT","amount":{"currencyCode":"EUR","minorUnits":"1000"}}]},"classification":{"eventClass":"SETTLED_SALE","anchorSignature":[{"accountRole":"CASH","side":"DEBIT"},{"accountRole":"REVENUE","side":"CREDIT"}],"containedTypedEvents":["SETTLED_SALE"],"hasCashLine":true,"evidenceClass":"CASH_SETTLEMENT","structural":{"adoptionOpeningEntry":false}}},"attestationCommit":{"operationOrder":"3","operationHead":"<attestation-operation-head>"}}}
```

`payload.postingId` is generated by FinGrind as a UUID v7 value.
The request shape is checked in at [examples/basic-posting-request.json](./examples/basic-posting-request.json).
One example committed response is checked in at
[examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json).
This example uses the sale-first request language with `cashAccountCode`, `revenueAccountCode`,
and one exact positive `amount`. Its `attestationCommit` records the appended operation order and
head; the published sample uses `<attestation-operation-head>` because every new book has a fresh
cryptographic authority chain.

## Generate Or Run A Ledger Plan

Generate the general plan scaffold:

```bash
fingrind \
  print-plan-template > ./plan.json
```

Like `print-request-template`, this scaffold uses the same canonical content as the checked-in
[examples/ledger-plan-template.json](./examples/ledger-plan-template.json) companion example.
It targets an already initialized book and contains one placeholder-first sale. Replace every
placeholder before real-world use.

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
- [examples/execute-plan-no-durable-child-mutation-response.json](./examples/execute-plan-no-durable-child-mutation-response.json)

After the tax-setup plan succeeds, use the checked-in query plan to inspect paginated registry state:

- `./ledger-plan-query-request.json`: copy [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json)

```bash
fingrind \
  execute-plan \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --output json \
  --result-detail full \
  --request-file ./ledger-plan-query-request.json
```

This query-only plan deliberately supplies no attestation credential: it runs through the dedicated
read-only boundary, reports `attestationDisposition: "read-only"`, and has
`attestationCommit: null`. Supplying a complete credential tuple would be refused as
`attestation-credentials-not-allowed` with exit `1`, before any credential is opened. Its successful
journal keeps `count`, `pageLimit`, optional `nextCursor`, `hasMore`, and grouped `account` /
`posting` facts. One checked-in response is at
[examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json).

Running the same signed tax-setup plan again demonstrates the distinct successful
`attestationDisposition: "no-durable-child-mutation"`: its mutation-capable transaction completes,
but every declaration is already durable, so it publishes `attestationCommit: null` and leaves the
verified head unchanged. The checked-in response is at
[examples/execute-plan-no-durable-child-mutation-response.json](./examples/execute-plan-no-durable-child-mutation-response.json).

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

# Prepare this caller-owned parent before selecting --pdf-out on POSIX hosts.
mkdir -p ./private-reports
chmod 700 ./private-reports

fingrind \
  trial-balance \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --effective-date-as-of 2026-04-07 \
  --comparative prior-period \
  --output text \
  --pdf-out ./private-reports/acme-trial-balance.pdf
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
`--pdf-out` writes a parallel PDF artifact to an absent path beneath an existing real owner-only
parent. The POSIX preparation above creates a fresh private parent; on Windows prepare the
equivalent owner-only ACL before running the command. FinGrind neither creates nor weakens the
caller-selected output parent. If the report succeeds and JSON is selected on stdout, the success
envelope publishes the canonical physical PDF path under `artifacts[]`. Before canonicalization,
FinGrind refuses every symbolic-link or non-directory lexical component from the root through the
selected parent. `--output text --pdf-out <path>` writes one artifact
confirmation block to stdout instead of the full report body, and `--output csv` cannot be paired
with `--pdf-out`. A successful PDF artifact is
`artifacts[].{format:"pdf",path,retainedStage}`; its stage is immutable evidence and is never
deleted, replaced, reused, or treated as a retry input. If the final-link parent-directory force
cannot confirm publication, no report success is emitted:
`artifact-publication-durability-uncertain` publishes top-level `retainedStage` and
`details.publishedArtifact.{path,retainedStage}`. Preserve and inspect both paths and do not retry
that no-clobber target. If a no-replace final-link attempt does not establish whether it created
the canonical candidate, `artifact-publication-outcome-uncertain` carries
`details.{candidateArtifact,retainedStage}` and the top-level stage when applicable. Preserve the
candidate and evidence, then use a fresh destination for a new attempt. A pre-final PDF export
failure remains `pdf-export-failure` and reports top-level `retainedStage` whenever applicable.
FinGrind does not check PDF binaries into `docs/examples`; the checked-in text and CSV examples
remain the canonical review fixtures.

For safe retries, request input, reversal, and deterministic failure-recovery flows, continue with
[USER_ENTRY_WORKFLOWS.md](./USER_ENTRY_WORKFLOWS.md).
