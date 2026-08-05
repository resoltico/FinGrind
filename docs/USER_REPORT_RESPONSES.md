---
afad: "5.0.1"
version: "0.62.1"
domain: OPERATOR_REPORT_RESPONSES
updated: "2026-08-05"
route:
  keywords: [fingrind, report-response, resolved-query, account-ledger, trial-balance, financial-position, csv, pdf-out, posting-coverage]
  questions: ["what JSON payload does a FinGrind report return", "how does FinGrind report pagination work", "which FinGrind report fields appear in CSV and PDF output"]
---

# Report Response Guide

This guide is the canonical response-contract owner for FinGrind report commands.
[USER_RESPONSES.md](./USER_RESPONSES.md) owns the shared success, error, discovery, posting, and
plan envelopes; [USER_REJECTIONS.md](./USER_REJECTIONS.md) owns deterministic rejection and
repair diagnostics.

## Report Responses

Every JSON report payload has the following semantic spine:

- `family`, the command's stable report token
- `bookIdentity`, using the shared initialized-book identity payload
- `resolvedQuery`, a family-specific record of the accepted and resolved query inputs
- `generatedAt`, the time this result was produced
- family-specific result facts such as rows, sections, totals, tax obligations, or inventory movements

`resolvedQuery` records the request that FinGrind accepted and how defaults resolved. It is not a
replay guarantee: a later back-dated posting in an open period can change the same report. Exact
replay would require a separate durable book-revision capability.

All report money values use exact objects with `currencyCode` and integer-string `minorUnits`.
Enums are machine tokens. Report JSON deliberately contains no presentation `context`, columns,
cells, labels, alignment, or formatted money strings.

The family-specific query records contain only inputs that the corresponding command accepts:

- `account-balance`: `accountCode`, effective-date bounds, and `postingCoverage`
- `trial-balance` and `financial-position`: optional `asOf`, `postingCoverage`, and optional comparative range
- `account-ledger`: `accountCode`, effective-date bounds, `postingCoverage`, and
  `pagination { limit, cursor }`
- `period-summary`, `income-statement`, `cash-flow-statement`, and `changes-in-equity`: period bounds, `postingCoverage`, and optional comparative range
- `inventory-valuation`: optional `asOf` and whether movements were requested
- `tax-obligation`: `taxRegistrationId` and reporting-period bounds

`account-balance` returns the declared account and per-currency exact balances. `trial-balance`
returns flattened account rows, per-currency totals, balance state, and optional comparative rows.
`account-ledger` returns the account, opening and closing balances, one ascending keyset page of
running-balance entries, and an optional top-level opaque `nextCursor`. The accepted cursor and
next cursor are only navigation tokens; they do not provide a durable read snapshot or replay
guarantee. `period-summary` returns counts, currency totals, and account activity.
`financial-position`, `income-statement`, and `cash-flow-statement` return typed sections with
rows and totals; `income-statement` also returns net-income totals. `changes-in-equity` returns
opening, movement, and closing facts per equity line. `tax-obligation` returns registration facts,
due date, tax-code rows, and obligation totals.

`inventory-valuation` returns one row per inventory account with its owned unit of measure, exact
quantity on hand, exact carrying value, and informational
`roundedMovingAverageUnitCostProjection`; movement rows appear only when `--movements` is
selected. Carrying value is the exact inventory cost pool, never quantity multiplied by the rounded
projection. `get-posting` likewise includes a costed sale's executor-derived cost of sales, relieved
quantity, and informational rounded moving-average unit-cost projection when those facts exist.

`--output csv` emits a single typed table for each report family. Every monetary column is paired
as `<name>CurrencyCode` and `<name>MinorUnits`; CSV does not mix in report context or query metadata
rows. Use JSON when the complete semantic result and resolved query are needed. `--output text` and
`--pdf-out` remain human projections of the shared report model. `--pdf-out` additionally
publishes a no-clobber private artifact with immutable `retainedStage` evidence;
[USER_RESPONSES.md](./USER_RESPONSES.md) owns that shared artifact and error envelope. If final
publication cannot be established, `artifact-publication-outcome-uncertain` reports the candidate
final PDF path without asserting that it exists and, when a stage was created, its retained stage.
If the final link exists but its directory durability is unconfirmed,
`artifact-publication-durability-uncertain` reports both the published PDF and its retained stage.
Neither result authorizes deletion, reuse, or an in-place retry.
