---
afad: "5.0.1"
version: "0.61.0"
domain: OPERATOR_REQUESTS
updated: "2026-07-17"
route:
  keywords: [fingrind, request-json, provenance, reversal, idempotency, accrual-cutoff, fixed-assets, financing, realized-foreign-exchange, latvian-payroll, prepayment, deferred-revenue, accrued-expense, ledger-plan, execute-plan, tax-setup, account-declaration, account-lifecycle]
  questions: ["what request json does fingrind accept", "how do i record a fixed asset or depreciation", "how do i record financing interest", "how do i settle a foreign-currency receivable", "how do i record Latvian monthly payroll", "how do i record a prepayment or deferred revenue", "how do i settle an accrued expense", "what ledger plan shape does execute-plan accept", "how do i amend or retire an account in fingrind", "what posting request fields does fingrind accept"]
---

# Request Shape Guide

**Purpose**: Show the accepted JSON request shapes owned by the CLI contract.
**Prerequisites**: Familiarity with the packaged CLI in [USER_CLI.md](./USER_CLI.md).

The checked-in `docs/examples/*.json` fixtures mentioned below exist only in a source checkout.
The public release bundle does not ship those repo paths.

Book-bound commands pair these JSON payloads with `--book-file` plus exactly one passphrase
source. `open-book` requires an absent `--book-file` destination and rejects an existing path
before resolving its selected key or accessing its contents. When the selected book parent directory
does not exist, `open-book` creates it with owner-only protection; when it already exists, FinGrind
requires it to remain owner-only:
- `--book-key-file` with a UTF-8 passphrase file protected by POSIX owner-only permissions
  (`0400` or `0600`) on macOS/Linux or a Windows owner-only ACL on Windows; its containing
  directory must also remain owner-only, and the public examples keep this file under a separate
  `./secrets/` tree rather than beside the book. `generate-book-key-file --new-book-key-file`
  creates a missing parent directory with owner-only protection, requires atomic no-replace
  publication support from the target filesystem, and rejects a pre-existing non-private parent
  directory
- `--book-passphrase-stdin` with one UTF-8 passphrase payload up to 4096 bytes from standard
  input
- `--book-passphrase-prompt` with an interactive non-echo terminal prompt whose normalized UTF-8
  payload must also fit within the same 4096-byte limit; this prompt route is accepted only when
  the selected stdout format is `text`, and machine stdout formats reject it as
  `invalid-request`

Every request JSON document must fit within FinGrind's `1048576`-byte UTF-8 payload limit whether
it comes from `--request-file <path>` or `--request-file -`.

`rekey-book` reuses those current-book routes and creates a fresh generated secret at an absent
`--new-book-key-file` target. `backup-book` likewise creates an independent secret at an absent
`--new-backup-key-file` target. `restore-book` reads the backup through `--backup-key-file` and
creates its destination secret through `--new-book-key-file`; it requires
`--replace-existing-book` when the selected `--book-file` already exists, and without that
option it refuses a destination that appears before final publication.

Every generated-secret target also requires a filesystem that can publish an absent staged secret
without replacement. FinGrind rejects a target that lacks that atomic no-replace primitive rather
than risking a partial or clobbering write.

Once a maintenance pair reaches its publication boundary, its declared final book and key artifacts
are successful. If filesystem I/O later prevents removal of FinGrind-owned internal staging evidence,
FinGrind records that cleanup failure without recasting the completed maintenance operation as a
failure.

## Posting Request Shape

Inspect the canonical posting-request scaffold:

```bash
fingrind print-request-template
```

Or, in a source checkout, inspect the checked-in companion example that carries the same scaffold
content:

```bash
cat docs/examples/request-template.json
```

The scaffold is intentionally a placeholder-first sample: `provenance.actorType` defaults to `PERSON`, the
emitted document carries explicit `replace-before-commit-*` evidence and provenance tokens, and
those placeholder values must be replaced before real-world use. On one book, an `idempotencyKey`
becomes single-use per book after the first committed posting.
The default posting scaffold uses the minimal `SALE_SETTLED` path with `cashAccountCode`,
`revenueAccountCode`, and `amount`. On `OWNER_MANAGED_TRADING` books, sale requests additionally
carry `inventoryRelief.inventoryAccountCode`, `inventoryRelief.costOfSalesAccountCode`, and
`inventoryRelief.quantity` so one typed sale records the inventory relief and cost-of-sales side of
the same business event. Trading inventory acquisitions stay on the typed path through
`print-request-template record-purchase-settled` and
`print-request-template record-purchase-on-credit`, which publish `inventoryAccountCode` plus the
matching cash or payable role fields, `quantity`, `unitCost`, and purchase-specific source-document defaults. Landed-cost capitalization, carrying-cost write-downs, quantity shrinkage, and count increases have their own `record-inventory-*` templates, account roles, and source-document policies. The raw
direct-journal boundary stays available through
`print-request-template post-entry`, but it still has to move at least one declared
cash-and-cash-equivalent asset account on cash-basis books and must not contain any line whose
declared account resolves to the inventory role. Inventory quantity and carrying-cost changes use
the corresponding typed inventory command exclusively.

On accrual-basis books, prepayments, deferred revenue, and accrued expenses use their own typed
cut-off commands. A prepayment or deferred-revenue request declares one cut-off id, the temporary
balance account, its recognition account, an amount, and an inclusive recognition interval.
An accrued-expense request declares one cut-off id, the expense account, the accrued-expense
liability, and an amount. Recognition and settlement commands reference that durable id and an
exact amount; FinGrind derives the account pair from the aggregate rather than accepting a
replacement journal. These manual lifecycle events do not compose with tax or foreign-exchange
facts and do not infer periodic allocation.

Fixed assets use `record-fixed-asset-capitalization`, `record-fixed-asset-depreciation`, and
`record-fixed-asset-disposal`. Capitalization supplies the asset id, cost, cash account, asset
accounts, gain/loss accounts, and a straight-line depreciation schedule. Depreciation supplies the
asset id only; FinGrind derives the admissible period amount and retained account facts. Disposal
supplies the asset id, cash account, and proceeds; FinGrind derives carrying value and any gain or
loss. A disposed asset remains in the register with zero current carrying amount, explicit disposal
state, and its exact pre-disposal amount in `carryingAmountAtDisposal`, while the disposal posting removes it from the general ledger. The model is a strict cost-model register, not a lease, impairment, revaluation, tax, or
statutory-reporting engine. Read [ADR_FIXED_ASSETS.md](./ADR_FIXED_ASSETS.md) and its linked
[IAS 16 primary source](https://www.ifrs.org/issued-standards/list-of-standards/ias-16-property-plant-and-equipment/) before choosing accounting policy.

Financing uses `record-financing-borrowing`, `record-financing-principal-repayment`,
`record-financing-interest-accrual`, and `record-financing-interest-payment`. A borrowing supplies
the arrangement id, cash account, principal-liability account, interest-payable account, and
principal amount. Later commands identify the retained arrangement and supply the relevant cash,
expense, and exact principal or interest amount; FinGrind derives the remaining obligation from
durable facts. The model is nominal principal plus exact stated interest only. It does not provide
effective-interest measurement, amortized cost, fees, covenants, maturity schedules, or refinancing.
Read [ADR_FINANCING.md](./ADR_FINANCING.md) and its linked
[IFRS 9 primary source](https://www.ifrs.org/issued-standards/list-of-standards/ifrs-9-financial-instruments/) before applying it to a financial instrument.

Realized foreign exchange uses `record-foreign-currency-obligation` and
`record-realized-foreign-exchange-settlement`. The obligation supplies its durable id, receivable,
revenue, realized-gain, and realized-loss accounts plus the retained foreign-exchange facts. The
settlement supplies that id, cash account, and settlement foreign-exchange facts; FinGrind derives
the realized gain or loss from the retained carrying amount. This model does not remeasure open
balances, translate financial statements, hedge, or source rates. Read
[ADR_REALIZED_FOREIGN_EXCHANGE.md](./ADR_REALIZED_FOREIGN_EXCHANGE.md), its linked
[IAS 21 primary source](https://www.ifrs.org/issued-standards/list-of-standards/ias-21-the-effects-of-changes-in-foreign-exchange-rates/), and the
[European Central Bank reference-rate source](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html) before treating a rate as suitable evidence.

Latvian monthly payroll uses `record-latvian-monthly-payroll`. It requires `payrollRunId`,
`employeeReference`, `payrollMonth`, EUR `grossWages`, six declared account roles, evidence,
provenance, and an explicit withholding profile: `taxBookHeldAtEmployer: true` and
`dependantCount: 0`. Those facts are not defaults. FinGrind rejects any other payroll-tax-book or
dependant profile rather than applying the EUR 550 monthly non-taxable minimum silently. The two
settlement commands identify the retained run and derive the exact open net-wage or state-remittance
obligation. Read [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md) and
[DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md) before use; this context does not determine
employment status, file with EDS, or perform annual reconciliation.

The packaged CLI can surface the same request-shape truth without leaving the terminal:
`help record-sale-settled`, `help post-entry`, `help declare-account`, and `help execute-plan` inline one
canonical template or starter-plan outline plus the accepted fields and enum vocabularies for their
`--request-file` payloads. On trading-template sale commands, that help also publishes `inventoryRelief` as a
conditional field whose description explains when it becomes required. On `execute-plan`, the
posting model remains nested under the ledger-plan request shape rather than surfacing as a second
top-level posting document. When you need the raw scaffold bytes directly, `print-request-template`
accepts `declare-account` plus every posting-shaped topic:
`post-entry`, `preflight-entry`, `record-sale-settled`, `record-sale-on-credit`,
`record-purchase-settled`, `record-purchase-on-credit`, `record-inventory-capitalization-settled`,
`record-inventory-capitalization-on-credit`, `record-inventory-write-down`,
`record-inventory-shrinkage`, `record-inventory-count-increase`, `record-expense-settled`,
`record-prepayment`, `record-deferred-revenue`, `record-accrued-expense`,
`record-accrual-cutoff-recognition`, `record-accrued-expense-settlement`,
`record-fixed-asset-capitalization`, `record-fixed-asset-depreciation`,
`record-fixed-asset-disposal`, `record-financing-borrowing`,
`record-financing-principal-repayment`, `record-financing-interest-accrual`,
`record-financing-interest-payment`, `record-foreign-currency-obligation`,
`record-realized-foreign-exchange-settlement`,
`record-latvian-monthly-payroll`, `record-latvian-payroll-net-wage-settlement`,
`record-latvian-payroll-state-remittance`,
`record-expense-on-credit`, `record-receipt`, `record-payment`, `record-owner-contribution`,
`record-owner-withdrawal`, `record-opening-position`, `record-reversal`, and
`declare-tax-registration`, `amend-account`, and `retire-account`.

Current posting-request rules:
- all top-level date, enum, identifier, and provenance fields are JSON strings
- `entryKind` is required and selects the top-level write path
- `preflight-entry` accepts the full published posting-request family, the typed `record-*`
  commands require their matching business-entry `entryKind`, and raw `post-entry` requires
  `DIRECT_JOURNAL`
- `amount` and `lines[].amount` both use one exact money object with `currencyCode` and `minorUnits`
- every money-object `currencyCode` must be one canonical three-letter uppercase ISO 4217 code supported by FinGrind's pinned currency registry
- every money-object `minorUnits` must contain ASCII digits only, must not contain redundant leading zeroes, must not exceed 19 digits, and must fit inside FinGrind's exact supported minor-unit range
- every money object must decode to one strictly positive posted amount
- `effectiveDate`, `evidence`, and `provenance` are required for every entry kind
- `DIRECT_JOURNAL` requires balanced `lines`
- `DIRECT_JOURNAL` is rejected unless at least one `lines[].accountCode` references a declared
  `CASH_AND_CASH_EQUIVALENT` asset account
- `DIRECT_JOURNAL` is rejected when any `lines[].accountCode` names an inventory account because raw journals do not own exact inventory quantity truth
- `SALE_SETTLED` requires `cashAccountCode`, `revenueAccountCode`, and `amount`; on `OWNER_MANAGED_TRADING` books it also requires `inventoryRelief`
- `SALE_ON_CREDIT` requires `receivableAccountCode`, `revenueAccountCode`, and `amount`; on `OWNER_MANAGED_TRADING` books it also requires `inventoryRelief`
- `PURCHASE_SETTLED` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `cashAccountCode`, `quantity`, and `unitCost`
- `PURCHASE_ON_CREDIT` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `payableAccountCode`, `quantity`, and `unitCost`
- `INVENTORY_CAPITALIZATION_SETTLED` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `cashAccountCode`, and pre-VAT `amount`; it requires existing positive inventory quantity and changes carrying cost without changing quantity
- `INVENTORY_CAPITALIZATION_ON_CREDIT` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `payableAccountCode`, and pre-VAT `amount`; it requires existing positive inventory quantity and changes carrying cost without changing quantity
- `INVENTORY_WRITE_DOWN` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `writeDownLossAccountCode`, and `amount`; the amount cannot exceed the exact carrying-cost pool
- `INVENTORY_SHRINKAGE` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `shrinkageLossAccountCode`, and `quantity`; FinGrind derives the loss from the exact pool and rejects an insufficient quantity
- `INVENTORY_COUNT_INCREASE` is admitted only on `OWNER_MANAGED_TRADING` books and requires `inventoryAccountCode`, `countGainAccountCode`, `quantity`, and `unitCost`
- `PREPAYMENT` is admitted only on accrual-basis books and requires `accrualCutoffId`, `prepaidExpenseAccountCode`, `expenseAccountCode`, `cashAccountCode`, `amount`, and an inclusive `recognitionInterval`
- `DEFERRED_REVENUE` is admitted only on accrual-basis books and requires `accrualCutoffId`, `cashAccountCode`, `deferredRevenueAccountCode`, `revenueAccountCode`, `amount`, and an inclusive `recognitionInterval`
- `ACCRUED_EXPENSE` is admitted only on accrual-basis books and requires `accrualCutoffId`, `expenseAccountCode`, `accruedExpenseAccountCode`, and `amount`
- `ACCRUAL_CUTOFF_RECOGNITION` is admitted only for an existing prepayment or deferred-revenue cut-off, requires `accrualCutoffId` plus `amount`, must fall inside the declared inclusive recognition interval, and cannot exceed the remaining temporary balance
- `ACCRUED_EXPENSE_SETTLEMENT` is admitted only for an existing accrued-expense cut-off, requires `accrualCutoffId`, `cashAccountCode`, and `amount`, and cannot exceed the remaining unpaid liability
- cut-off origin, recognition, and settlement entries reject `taxSelection` and `foreignExchange`; periodic allocation remains operator-authored rather than inferred
- `FIXED_ASSET_CAPITALIZATION` requires `fixedAssetId`, `cashAccountCode`, and the fixed-asset fact block containing the asset, accumulated-depreciation, depreciation-expense, disposal-gain, and disposal-loss accounts, cost, and straight-line depreciation schedule
- `FIXED_ASSET_DEPRECIATION` requires only `fixedAssetId`; FinGrind derives the period depreciation and retained account facts from the capitalized asset
- `FIXED_ASSET_DISPOSAL` requires `fixedAssetId`, `cashAccountCode`, and fixed-asset proceeds; FinGrind derives carrying value and the disposal gain or loss
- `FINANCING_BORROWING` requires `cashAccountCode` plus a financing block containing the arrangement id, principal-liability account, interest-payable account, and principal amount
- `FINANCING_PRINCIPAL_REPAYMENT` requires `cashAccountCode` plus the retained arrangement id and principal amount; `FINANCING_INTEREST_ACCRUAL` requires the arrangement id, interest-expense account, and interest amount; `FINANCING_INTEREST_PAYMENT` requires `cashAccountCode`, the arrangement id, and interest amount
- `FOREIGN_CURRENCY_OBLIGATION` requires receivable and revenue accounts, a realized-foreign-exchange block with obligation id and gain/loss accounts, and `foreignExchange`; `REALIZED_FOREIGN_EXCHANGE_SETTLEMENT` requires `cashAccountCode`, the retained obligation id, and `foreignExchange`, while FinGrind derives the realized gain or loss
- `LATVIAN_MONTHLY_PAYROLL` requires `payrollRunId`, `employeeReference`, `payrollMonth`, EUR `grossWages`, `taxBookHeldAtEmployer: true`, `dependantCount: 0`, and the six published account-role fields; other withholding profiles are outside this bounded context and are rejected rather than approximated
- `EXPENSE_SETTLED` requires `expenseAccountCode`, `cashAccountCode`, and `amount`
- `EXPENSE_ON_CREDIT` requires `expenseAccountCode`, `payableAccountCode`, and `amount`
- `RECEIPT` requires `cashAccountCode`, `receivableAccountCode`, and `amount`
- `PAYMENT` requires `payableAccountCode`, `cashAccountCode`, and `amount`
- `OWNER_CONTRIBUTION` requires `cashAccountCode`, `equityAccountCode`, and `amount`
- `OWNER_WITHDRAWAL` requires `equityAccountCode`, `cashAccountCode`, and `amount`
- `OPENING_POSITION` requires `openingBalances`
- every `openingBalances[].accountCode` must name one asset, liability, or equity account; an inventory balance additionally requires `openingBalances[].quantity`, uses `openingBalances[].amount` as full carrying cost, and must be the first durable movement for that inventory account; non-inventory balances must omit `quantity`
- `REVERSAL` requires `reversal`, derives its journal lines from `reversal.priorPostingId`, and rejects targets that are themselves reversals
- `evidence.sourceDocuments` must contain at least one source-document object
- every `evidence.sourceDocuments[]` entry requires `sourceDocumentId`, `sourceDocumentType`, and `documentDate`
- `inventoryRelief` is admitted only on `SALE_SETTLED` and `SALE_ON_CREDIT`
- `inventoryRelief` requires `inventoryAccountCode`, `costOfSalesAccountCode`, and `quantity`
- `inventoryRelief.inventoryAccountCode` and `inventoryRelief.costOfSalesAccountCode` must name distinct declared accounts
- every inventory-account decrease, including `inventoryRelief.quantity`, is rejected when it would drive exact quantity on hand below zero; record the missing inventory acquisition first or reduce the requested decrease
- purchase and capitalization `amount` or `unitCost` values are pre-VAT functional-currency carrying costs; recoverable input tax is posted to the recoverable-tax account outside the pool, while nonrecoverable input tax is capitalized into the pool
- sale `inventoryRelief.quantity` identifies quantity only: FinGrind derives cost of sales from the exact inventory pool and retains the derived cost, relieved quantity, and informational rounded moving-average unit-cost projection on the committed sale
- `inventory-valuation` exposes the exact per-account quantity and carrying-cost pool from durable movement replay; its rounded moving-average unit-cost projection is display-only and never feeds carrying value or cost of sales
- a foreign-exchange purchase must state a `foreignExchange.functionalAmount` equal to the executor-resolved pre-tax acquisition cost, including quantity multiplication
- on command-scoped `requestShapes.bookkeepingEntry` payloads, the selected `sourceDocumentType`
  policy is published directly on `sourceDocumentFields[]` and on the embedded executable schema;
  the full-family descriptor also keeps `sourceDocumentTypeMode`,
  `acceptedSourceDocumentTypes`, `sourceDocumentTypeSemantics`, and described entry-specific
  `variantFields[]` on `entryKindSemantics[]`
- `evidence.approvals` is required as an array and may be empty
- every `evidence.approvals[]` entry requires `approvalId`, `approvalType`, `approverId`, `approverType`, `decision`, and `approvedAt`
- `lines[].accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- every direct `DIRECT_JOURNAL` entry must contain at least two journal lines
- every direct `DIRECT_JOURNAL` entry must contain at least one `DEBIT` line and at least one `CREDIT` line
- every line inside one direct `DIRECT_JOURNAL` entry must share the same `lines[].amount.currencyCode`
- every direct `DIRECT_JOURNAL` entry is rejected when debit-credit netting reduces every referenced account to zero, because that request would record no durable account movement
- every journal-line amount, every top-level `amount`, and every `openingBalances[].amount` must use the selected book's functional currency
- `foreignExchange` is optional for `DIRECT_JOURNAL`, settled and on-credit sales, purchases,
  capitalizations, expenses, `OWNER_CONTRIBUTION`, `OWNER_WITHDRAWAL`, and `REVERSAL`; it is
  required for `FOREIGN_CURRENCY_OBLIGATION` and `REALIZED_FOREIGN_EXCHANGE_SETTLEMENT`; and it
  must be absent for `RECEIPT`, `PAYMENT`, and `OPENING_POSITION`
- `foreignExchange` requires `transactionAmount`, `functionalAmount`, `quotedRate`, and
  `treatmentKind`
- `foreignExchange.quotedRate` requires `transactionCurrencyAmount`,
  `functionalCurrencyAmount`, `quotedOn`, and `quoteSource`
- `foreignExchange.quotedRate` is retained evidence, not a rate selected or endorsed by FinGrind;
  preserve the source and date that the entity's policy requires. For euro reference-rate evidence,
  use the [European Central Bank reference-rate source](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html), noting that the ECB publishes those rates for information and discourages transaction use. See [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md) before treating any reference rate as applicable to a transaction, tax, or reporting policy.
- `foreignExchange.transactionAmount` and
  `foreignExchange.quotedRate.transactionCurrencyAmount` must share one distinct non-functional
  currency
- `foreignExchange.functionalAmount` and
  `foreignExchange.quotedRate.functionalCurrencyAmount` must use the selected book's functional
  currency
- every typed transaction request that accepts `foreignExchange` requires
  `foreignExchange.treatmentKind: "SPOT_TRANSACTION"`; direct `DIRECT_JOURNAL` and `REVERSAL`
  requests accept the broader published treatment vocabulary
- `foreignExchange` records foreign transaction facts without changing the journal-line currency,
  so mixed-currency journal lines remain rejected
- `reversal` is required only for `REVERSAL` and must be absent for every other `entryKind`
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- `provenance.idempotencyKey` must start with an ASCII letter or digit, may then contain only ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 128 characters
- optional provenance field is `correlationId`
- `reversal.priorPostingId` and `reversal.reason` are both required when `reversal` is present
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal` and `correlationId`
- `reversal.priorPostingId` must already exist in the selected book
- `reversal.priorPostingId` must not identify one posting whose own lineage is already `REVERSAL`
- a reversal requires one exact line-by-line negation of the target posting and only one reversal is allowed per target
- a reversal of a cut-off origin, recognition, or settlement also records a compensating immutable lifecycle fact; it cannot predate the aggregate lifecycle horizon, and an origin can be reversed only after its active applications have been reversed
- `OPENING_POSITION` may touch only `ASSET`, `LIABILITY`, or `EQUITY` accounts
- `OPENING_POSITION` is accepted only before the first committed posting exists in the selected book, so all adoption balances must be seeded inside one opening-only window
- `requestShapes.bookkeepingEntry.reachabilityMatrix[]` is the canonical per-classification truth for which declared-account cells are opening-reachable, operational-journal-reachable, or reversal-reachable; the built-in `RESULT_HOLDING` classification remains opening-reachable but is reserved from caller-authored standard journals and reversals
- legacy `correction` and `reversal.kind` fields are rejected
- unknown fields are rejected at every object level
- duplicate JSON object keys are rejected

## Account-Declaration Request Shape

`declare-account` accepts one book-local account-definition document:

```json
{
  "accountCode": "1000",
  "accountName": "Cash",
  "accountType": "ASSET",
  "accountNodeKind": "POSTABLE",
  "financialPositionLineClassification": "CURRENT_ASSET",
  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
}
```

Inventory accounts add one nested `unitOfMeasure` object:

```json
{
  "accountCode": "inventory",
  "accountName": "Inventory",
  "accountType": "ASSET",
  "accountNodeKind": "POSTABLE",
  "financialPositionLineClassification": "INVENTORY",
  "cashFlowAssetClassification": "NON_CASH",
  "unitOfMeasure": {
    "token": "unit",
    "quantityScale": 0
  }
}
```

Current account-declaration rules:
- `accountCode`, `accountName`, `accountType`, and `accountNodeKind` are required
- `cashFlowAssetClassification` is required when `accountType` is `ASSET` and is forbidden for
  non-asset accounts
- `unitOfMeasure` is required when `financialPositionLineClassification` is `INVENTORY` and is
  forbidden for every non-inventory account
- `unitOfMeasure.token` must start with an ASCII letter or digit, may then contain only ASCII
  letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 64 characters
- `unitOfMeasure.quantityScale` must be an integer between `0` and `9` inclusive
- `parentAccountCode` is optional and declares one explicit chart parent when this account belongs
  under another declared account
- `contraOfAccountCode` is optional and declares the postable account this account reduces; the
  target must exist, be active, use the same `accountType` and compatible statement classification,
  and cannot itself be a contra account
- `accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters,
  digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- `accountCode` is an opaque book-local identifier today; FinGrind does not infer account class or
  hierarchy from numeric ranges or prefixes
- `accountName` must be a non-blank string
- `accountType` must be one of the canonical chart classifications supported by FinGrind
- `accountNodeKind` must be one of `POSTABLE` or `HEADER`
- `ASSET`, `LIABILITY`, and `EQUITY` accounts must declare
  `financialPositionLineClassification` and must not declare
  `profitAndLossLineClassification`
- `CURRENT_PERIOD_RESULT` is reserved for derived statement rows and is not accepted in
  `financialPositionLineClassification` when declaring accounts
- `REVENUE` and `EXPENSE` accounts must declare `profitAndLossLineClassification` and must not
  declare `financialPositionLineClassification`
- `INVENTORY` accounts are ordinary asset accounts on the public request surface, so they still
  declare one `cashFlowAssetClassification`; the built-in inventory doctrine requires `NON_CASH`
- redeclaring an existing account may update the display name and reactivate the account
- redeclaring an existing account with a different `accountType` is rejected
- redeclaring an existing account with a different chart parent or declared taxonomy is
  rejected
- redeclaring an existing account with a different `contraOfAccountCode` is rejected after its
  first declaration

## Account Registry Lifecycle

`amend-account` accepts the same account-definition shape as `declare-account`, but it is only
admitted for an account with no postings, no tax-registration binding, and no child account. It
replaces the definition while preserving the account code and original `declaredAt` timestamp.
`retire-account` accepts `{ "accountCode": "..." }`. Retirement requires a zero current balance
and no live tax-registration or child-account binding; it prevents new ordinary authored postings
without deleting the account or its journal history. A historical `record-reversal` can still use a
retired account because it negates a retained posting rather than creating a new ordinary use.
There is no delete-account request or command.

## Ledger-Plan Request Shape

Inspect the canonical AI-agent scaffold:

```bash
fingrind print-plan-template
```

Or, in a source checkout, inspect the checked-in runnable example:

```bash
cat docs/examples/ledger-plan-request.json
```

The default ledger-plan scaffold is a general workflow: it initializes a book and contains one
placeholder-first settled sale. `print-plan-template tax-setup`, `print-plan-template
fixed-asset-setup`, and `print-plan-template financing-setup` emit atomic setup plans for those
respective contexts. The tax setup declares the required payable and recoverable accounts before it
declares the tax registration; a direct `declare-tax-registration` command remains pure and never
creates prerequisite accounts implicitly. Raw direct-journal and posting steps remain available in
custom plans when the caller needs them.

Current ledger-plan rules:
- top-level fields are `planId` and `steps`
- `planId` must be a non-blank string
- `steps` must contain at least one object and every `stepId` must be unique
- `ensure-book` is allowed only as the first step when a plan initializes a book
- every step requires `stepId` and `kind`
- `ensure-book` uses nested `ensureBook`, which requires `entityName`, `bookTemplateId`,
  `accountingBasis`, `functionalCurrency`, `fiscalYearStart`, and `bookStartEffectiveDate`;
  `bookStartEffectiveDate` is an ISO-8601 calendar date and becomes the immutable earliest
  posting effective date for the book. `bookTemplateId` currently
  accepts `OWNER_MANAGED_SERVICE` or `OWNER_MANAGED_TRADING`, `accountingBasis` accepts `CASH`
  or `ACCRUAL`, and the runtime persists the built-in doctrine facts and echoes them back in
  response payloads
- `declare-account` uses nested `declareAccount`, which has the same shape and inventory-account
  `unitOfMeasure` rule as the standalone `declare-account` request
- `declare-tax-registration` uses nested `declareTaxRegistration`; its payable and recoverable
  account codes must refer to compatible accounts already present in the book or declared by an
  earlier plan step
- `preflight-entry`, every committed `record-*` step, and raw `post-entry` use nested `posting`,
  which has the same shape as the normal posting request, including required
  `evidence.sourceDocuments[]` and `evidence.approvals[]`
- `list-accounts`, `list-postings`, and `account-balance` use nested `query`
- `list-accounts.query` is optional; when present it accepts optional `limit` plus optional opaque
  `cursor`, and omitted `limit` defaults to the standard page size
- `list-postings.query` is optional; when present it accepts optional `accountCode`, optional
  effective-date bounds, optional `limit`, and optional opaque `cursor`, and omitted `limit`
  defaults to the standard page size
- `account-balance.query` accepts `accountCode` plus optional effective-date bounds
- `get-posting` uses `postingId`
- assertion steps use `kind: "assert"` plus a nested `assertion` object
- supported assertion kinds are `assert-account-declared`, `assert-account-active`,
  `assert-posting-exists`, and `assert-account-balance`
- `assert-account-balance` assertions accept `accountCode`, optional `effectiveDateFrom`,
  optional `effectiveDateTo`, typed `netAmount`, and `balanceSide`
- unknown fields are rejected at every object level
- `print-plan-template` emits the canonical general `execute-plan` scaffold; supply one of the
  named setup topics when a tax, fixed-asset, or financing context needs its atomic prerequisite
  account declarations, and replace every placeholder before real-world use
- execution semantics are not request knobs: plans are atomic, halt on first failed step, return
  one bounded aggregate summary by default, and return one complete journal when
  `--result-detail full` is selected; ordinary business steps keep their canonical `kind`,
  assertion entries optionally add `detailKind`, and unexpected begin, initialization-check,
  commit, or rollback failures end the journal with `kind: "plan-boundary"` plus
  `boundaryCheckpoint`
- unexpected transaction-boundary failures such as begin, commit, or rollback problems are mapped
  into the terminal rejected journal step instead of escaping as an untyped plan exception
- plan-journal steps now carry typed `data` records instead of generic fact bags
- successful `declare-tax-registration` journal steps emit the declared tax-registration snapshot,
  including its account bindings and ordered tax-code catalog
- money-bearing plan-journal `data` fields use objects carrying `currencyCode` and `minorUnits`
- successful `list-accounts` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed `accounts[]`
- successful `list-postings` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed summary `postings[]` with `postingId`, `postingKind`,
  `postingOriginKind`, `reversalState`, optional `reversesPostingId`, optional
  `reversedByPostingId`, `effectiveDate`, `recordedAt`, `debitTotal`, `creditTotal`,
  `accountCodes[]`, `sourceDocumentIds[]`, and `approvalIds[]`

For every non-plan single-command invocation, deterministic business rejections and deterministic
failures now use one JSON diagnostics envelope on stderr regardless of `--output`. Successful
stdout may be text, JSON, or CSV where advertised, but failing single-command invocations keep the
same parseable diagnostics shape with the same top-level `message`, optional `hint`, and any typed
detail payload that identifies the failing posting id, blocked close-reserved account code and
classification, account-state violation set, or related deterministic repair data. `execute-plan`
is the
exception: its `REJECTED` and `ASSERTION_FAILED` outcomes are primary result envelopes on stdout.

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `foreignExchange.treatmentKind` | `SPOT_TRANSACTION`, `UNREALIZED_REMEASUREMENT` |
| `provenance.actorType` | `PERSON`, `SYSTEM`, `AGENT` |
| `accountType` | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| `accountNodeKind` | `POSTABLE`, `HEADER` |
| `financialPositionLineClassification` | `CURRENT_ASSET`, `INVENTORY`, `PREPAID_EXPENSE`, `NONCURRENT_ASSET`, `TRADE_RECEIVABLE`, `CURRENT_LIABILITY`, `NONCURRENT_LIABILITY`, `TRADE_PAYABLE`, `DEFERRED_REVENUE`, `ACCRUED_EXPENSE`, `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`, `RESULT_HOLDING`, `RETAINED_ACCUMULATED`, `RESERVE`, `OTHER_EQUITY` |
| `profitAndLossLineClassification` | `OPERATING_REVENUE`, `SALES_DISCOUNT_ALLOWANCE`, `OTHER_REVENUE`, `FINANCE_INCOME`, `COST_OF_SALES`, `OPERATING_EXPENSE`, `DEPRECIATION_AND_AMORTIZATION`, `SETTLEMENT_FEE`, `BAD_DEBT_WRITE_OFF`, `FINANCE_EXPENSE`, `OTHER_EXPENSE` |

`lines[].side` is input polarity for one journal line. Response-side `balanceSide` is a derived net orientation for grouped balances, running balances, and report totals; it is not a second writable posting-line field.

## Response And Output Guide

Request shapes stay in this guide. Response envelopes, read and report payloads, capabilities
output, execute-plan results, and deterministic rejection or error payloads now live in
[USER_RESPONSES.md](./USER_RESPONSES.md).

That companion guide owns the full response contract, including:
- the shared `status`, `payload`, and optional `artifacts[]` envelope families
- the `capabilities` discovery payload and its typed descriptor inventories
- read and report payloads such as `inspect-book`, `list-postings`, `trial-balance`,
  `account-ledger`, and `cash-flow-statement`
- `execute-plan` summaries and optional journals
- deterministic rejection and error payloads, including
  [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt) and
  [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt)

For comparative report outputs, [USER_RESPONSES.md](./USER_RESPONSES.md) is the canonical owner of
the statement payload families and records that `cash-flow-statement` carries
`comparativeOpeningCashTotals[]`, `comparativeMovementTotals[]`, and
`comparativeClosingCashTotals[]` when one comparative period selection is requested and resolved.
