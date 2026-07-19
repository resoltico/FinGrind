---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_PROFILES
updated: "2026-07-19"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationSemanticProfile", "SystemWorkflowPolicy"]
route:
  keywords: [verifiable-operation-attestation, semantic-profile, posting-role, autonomous-workflow, system-channel, journal-balance, request-effect-closure]
  questions: ["which request facts are admitted by an attested operation", "how are attested posting facts tied to journal effects", "what can start a system-channel attested operation", "how does FinGrind derive a system close"]
stage: "Slice 0 feature-branch specification; not released behavior"
---

# Verifiable Operation Attestation Semantic Profiles

This document is the canonical field-level admission owner for the next protected-book attestation
format. [DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md)
owns the wire grammar, record tags, envelope, and failure vocabulary. This document closes the
semantic relation between a typed request, prior accepted book state, and its immutable effect.

## Closed Posting Relation

Tag admission is necessary but not sufficient. A Version-1 typed posting is valid only when its
complete request and effect satisfy exactly one row below. No request fact may be ignored; no
effect fact may be discretionary; and a row never inherits a role, amount, quantity, or lifecycle
fact from another row merely because it shares a record tag.

For one posting step, `A{...}`, `M{...}`, and `Q{...}` mean the exact set of
`request.account-role`, `request.money`, and `request.quantity` roles. Every listed role occurs
exactly once; every unlisted role is forbidden. `E1` means exactly one evidence-document. `-`
means the corresponding record type is forbidden. `T` and `X` are the paired tax and quoted-FX
groups from the core contract. `I`, `A1`, `A2`, `L1`, `L2`, `F1`, `F2`, `F3`, `N1`, `N2`, `N3`,
`O1`, `O2`, and `R` have the tag meaning assigned in the core posting matrix.

An A, M, or Q set contributes exactly one 0121, 0122, or 0123 record for each listed role. Thus a
profile is a field-level grammar, not merely a tag allowlist. `request.inventory-movement` carries
the same inventory quantity as its Q fact, names the row's inventory account and counter account,
and has movementKind exactly inventory-capitalization, inventory-write-down, inventory-shrinkage,
or inventory-count-increase for I0/I1, I2, I3, or I4 respectively.

Every typed step has exactly one request.posting. Its operationKind equals the enclosing command,
except that execute-plan uses the child kind. It has exactly the listed account, money, quantity,
and lifecycle records and no other request record apart from request.command, its one E1, and an
explicit paired T or X group where the row permits it. A typed step has exactly one posting.fact,
exactly one posting.source-document, and journal.line records numbered contiguously from zero.
Every posting.fact recordedAt equals its operation payload recordedAt; its sourceChannel equals the
request.command sourceChannel; and its operationStepOrder equals the request stepOrder.

Each effect journal line must use either an account named by the profile's A set, an account
deterministically retained by the referenced lifecycle aggregate, or the account resolved by the
selected tax registration. Every named account must be used by at least one effect journal line or
the explicitly named account field of its matching lifecycle effect. Journal lines balance debit
and credit by currency exactly. Each named money or quantity fact is consumed exactly once by the
row's journal or lifecycle equation; an executor may not introduce an additional amount, quantity,
account, or posting relationship. Generated identifiers, recordedAt, calculated tax, weighted
inventory cost, payroll deductions, remaining lifecycle amount, and realized FX are derivations,
not caller substitutions.

| Profile | Operation kinds | Exact A roles | Exact M roles | Exact Q roles | Required group and effect equation |
|:--|:--|:--|:--|:--|:--|
| J | post-entry, record-opening-position | - | - | - | D; request.journal-line is two or more contiguous, balanced input lines and effect journal.line is its exact postingId-bearing projection. Opening-position may use opening-quantity only on an explicitly inventory-classified input line. |
| S0 | record-sale-settled | cash-account, revenue-account | gross-amount | - | B; debit cash and credit revenue for gross amount. T is permitted; X is forbidden. |
| S1 | record-sale-on-credit | receivable-account, revenue-account | gross-amount | - | B; debit receivable and credit revenue for gross amount. T is permitted; X is forbidden. |
| P0 | record-purchase-settled, record-expense-settled | expense-account, cash-account | gross-amount | - | B; debit expense and credit cash for gross amount. T is permitted; X is forbidden. |
| P1 | record-purchase-on-credit, record-expense-on-credit | expense-account, payable-account | gross-amount | - | B; debit expense and credit payable for gross amount. T is permitted; X is forbidden. |
| R0 | record-receipt | cash-account, receivable-account; settlement-adjunct-account only when 0129 is present | settlement-amount | - | B; debit cash and credit the retained receivable, with the optional adjunct consumed once. |
| R1 | record-payment | payable-account, cash-account; settlement-adjunct-account only when 0129 is present | settlement-amount | - | B; debit the retained payable and credit cash, with the optional adjunct consumed once. |
| O0 | record-owner-contribution | cash-account, equity-account | gross-amount | - | B; debit cash and credit equity. T and X are forbidden. |
| O1 | record-owner-withdrawal | equity-account, cash-account | gross-amount | - | B; debit equity and credit cash. T and X are forbidden. |
| I0 | record-inventory-capitalization-settled | inventory-account, cash-account | unit-cost | inventory-quantity | B, I; debit inventory and credit cash for quantity times unit cost. |
| I1 | record-inventory-capitalization-on-credit | inventory-account, payable-account | unit-cost | inventory-quantity | B, I; debit inventory and credit payable for quantity times unit cost. |
| I2 | record-inventory-write-down | inventory-account, write-down-loss-account | carrying-amount | inventory-quantity | B, I; debit write-down loss and credit inventory. |
| I3 | record-inventory-shrinkage | inventory-account, shrinkage-loss-account | carrying-amount | inventory-quantity | B, I; debit shrinkage loss and credit inventory. |
| I4 | record-inventory-count-increase | inventory-account, count-gain-account | carrying-amount | inventory-quantity | B, I; debit inventory and credit count gain. |
| A0 | record-prepayment | prepayment-asset-account, expense-account, cash-account | gross-amount | - | B, A1; debit prepayment asset and credit cash. 0050 stores prepayment asset as balanceAccountCode and expense as recognitionAccountCode, with the inclusive recognition interval. |
| A1 | record-deferred-revenue | cash-account, deferred-revenue-account, revenue-account | gross-amount | - | B, A1; debit cash and credit deferred revenue. 0050 stores deferred revenue as balanceAccountCode and revenue as recognitionAccountCode, with the inclusive recognition interval. |
| A2 | record-accrued-expense | expense-account, accrued-expense-liability-account | gross-amount | - | B, A1; debit expense and credit accrued-expense liability. 0050 stores accrued-expense liability as balanceAccountCode and expense as recognitionAccountCode. |
| A3 | record-accrual-cutoff-recognition | - | recognition-amount | - | B, A2; the referenced cut-off fixes its accounts, currency, permitted interval, and remaining amount. |
| A4 | record-accrued-expense-settlement | cash-account | settlement-amount | - | B, A2; the referenced cut-off fixes its accrued-liability account and remaining amount. |
| L0 | record-latvian-monthly-payroll | wage-expense-account, employer-social-contribution-expense-account, net-wages-payable-account, employee-social-contribution-payable-account, employer-social-contribution-payable-account, personal-income-tax-payable-account | gross-amount | - | B, L1; the closed Latvian 2026 formula derives net wage, employee and employer contribution, and personal-income-tax lines. 0090 preserves all six accounts by their matching field names. |
| L1 | record-latvian-payroll-net-wage-settlement, record-latvian-payroll-state-remittance | cash-account | - | - | B, L2; the retained payroll run fixes the one still-open obligation and exact settlement amount. |
| F0 | record-fixed-asset-capitalization | fixed-asset-account, accumulated-depreciation-account, depreciation-expense-account, gain-on-disposal-account, loss-on-disposal-account, cash-account | carrying-amount | - | B, F1; debit fixed asset and credit cash. 0060 preserves the five non-cash accounts by their matching field names and the complete straight-line schedule. |
| F1 | record-fixed-asset-depreciation | - | - | - | B, F2; the retained asset fixes period, amount, fixed-asset, accumulated-depreciation, and depreciation-expense accounts. |
| F2 | record-fixed-asset-disposal | cash-account | proceeds-amount | - | B, F3; the retained asset fixes carrying amount and gain/loss accounts. |
| N0 | record-financing-borrowing | cash-account, financing-principal-account, interest-payable-account | principal-amount | - | B, N1; debit cash and credit financing principal. 0070 preserves financing principal and interest payable by their matching field names. |
| N1 | record-financing-principal-repayment | cash-account | principal-amount | - | B, N2; the retained arrangement fixes the principal account and remaining bound. |
| N2 | record-financing-interest-accrual | interest-expense-account | interest-amount | - | B, N2; the retained arrangement fixes interest payable and its accrued-interest bound. |
| N3 | record-financing-interest-payment | cash-account | interest-amount | - | B, N2; the retained arrangement fixes interest payable and its accrued-interest bound. |
| X0 | record-foreign-currency-obligation | receivable-account, revenue-account, foreign-exchange-gain-account, foreign-exchange-loss-account | - | - | B, O1; 0127 fixes the foreign and functional amounts and quote; origin debits receivable and credits revenue. 0080 preserves all four accounts by their matching field names. |
| X1 | record-realized-foreign-exchange-settlement | cash-account | - | - | B, O2; 0127 fixes settlement amount and quote; the retained obligation fixes carrying amount and derived gain or loss. |
| V | record-reversal | - | - | - | B, R; request.posting.priorPostingId is required, reversalReason is required, and the effect is the unique compensating projection of the referenced active posting and its lifecycle facts. |

`request.inventory-movement` is present exactly for I0 through I4. Its inventoryAccountCode and
counter account must equal the row's named A roles; its quantity equals the one Q fact. Its amount
is `quantity × unit-cost` for I0/I1 and the named carrying amount for I2/I4. A zero or a
non-integral minor-unit result is invalid.

T is admitted only by S0, S1, P0, P1, and the inventory rows. It produces exactly one
posting.applied-tax fact and any tax journal lines resolved from the named registration. X is
admitted only by direct journal J and the rows whose equation explicitly names 0127; it produces
exactly one posting.foreign-exchange fact. A row that does not admit T or X rejects the whole
request rather than silently ignoring the extra group.

`execute-plan` contains one command and one or more child steps. Each child independently satisfies
one profile after removing the shared request.command record. It may not combine profiles, reuse a
stepOrder, or let a request fact in one child justify an effect fact in another.

## Autonomous System Initiation And Workflow Policy

The sourceChannel field records the origin of a signed request; it is not authority. cli means the
request was initiated through an operator-facing surface. system means FinGrind derived the request
from already committed book facts while executing an autonomous accounting workflow. A
human-requested command, including one submitted by automation on that human's behalf, is cli.

A system-initiated operation has only system-purpose enrolled credentials in its envelope. Those
credentials have no ambient or implicit authority: each needs the same active key, capability
grant, exact quorum, distinct-principal, observed-head, and historical-policy checks as every
other signer. Their private keys remain outside the book and are subject to the same custody rule.
system is valid only for an operation whose request and effect are mechanically derived from the
stated autonomous workflow and already committed facts; it is invalid for a caller-supplied
request, a caller-authored journal entry, or a discretionary policy decision. A cli request has
only operator-purpose envelope credentials. The signed request therefore proves the credential
purpose that authorized its asserted provenance, but cannot prove an external human did or did not
press a button.

Version 1 permits sourceChannel=system only for interim-result-sweep and fiscal-year-close. Every
other operation kind requires sourceChannel=cli. A system channel outside this closed set is
attestation-request-profile-invalid; a system request that does not reproduce its one derivation is
attestation-system-derivation-invalid.

A system workflow policy is an append-only, attested policy fact. Its workflowKind field is one
closed systemWorkflowKind token. An active policy has one workflowId, one such kind, and the
account bindings required by that kind. At most one active policy of each kind may exist at a
resolving position. Genesis may declare policies; an alter-policy operation may create or retire
one, subject to the same capability-quorum and post-operation eligibility checks as every other
policy fact. A policy configuration never changes in place: replacement retires its prior
workflowId and creates a new one. A retirement repeats its historical kind and every account
binding exactly, with active false.

sourceChannel=system is valid only for an active policy and exactly one request.system-workflow-run.
The system-run record is a proof selector, never caller input: its workflowId must resolve to the
only active policy of the corresponding operation kind. The verifier derives request.period-close
and every close effect from the prior accepted state, that policy, and the payload recordedAt. Any
different request field, extra request field, omitted required close fact, amount, account, period,
or effect is attestation-system-derivation-invalid.

| Workflow kind | Required active-policy bindings | Unique derived request and effect |
|:--|:--|:--|
| interim-result-sweep | resultHoldingAccountCode; capitalAccountCode and retainedResultAccountCode absent | effectiveFrom is the day after the latest accepted sweep or fiscal-close effectiveTo, or bookStartDate when none exists. effectiveTo is the calendar day before recordedAt. resultHoldingAccountCode equals policy. fiscalYear, capital, and retained-result fields are absent. The effect has the next sweepOrder, exactly one total per affected currency, and journal lines that transfer every income and expense balance in that interval to result holding. |
| fiscal-year-close | resultHoldingAccountCode, capitalAccountCode, retainedResultAccountCode | effectiveFrom and effectiveTo are the oldest unclosed fiscal year derived from book identity whose end is no later than calendar date(recordedAt). fiscalYear is that year's ending calendar year. The three account fields equal policy. The effect has the next closeOrder and, for every nonzero currency balance held in result holding immediately before the close, transfers that balance to retained result with the direction required to clear result holding. 0043 records all three policy accounts; capitalAccountCode is that close's immutable classification binding and creates no additional Version-1 journal line. |

Every close operation has exactly one request.period-close. An interim close requires
effectiveFrom, effectiveTo, and resultHoldingAccountCode, and forbids fiscalYear,
capitalAccountCode, and retainedResultAccountCode. A fiscal close requires every period-close
field. A system close additionally has exactly one request.system-workflow-run. It has exactly one
posting.fact; journal.line records are contiguous; an interim close has exactly one 0040, one 0042
that links its posting, and one 0041 for each affected currency; a fiscal close has exactly one
0043 and one 0044 that links its posting. No other close effect is admitted.

An interim sweep is invalid when its derived interval is empty. A fiscal close is invalid when no
fiscal year is due or when an earlier year remains unclosed. A CLI-originated close remains a
normal CLOSE_PERIOD operation, but it cannot claim system provenance and has no system-run record.
No other Version-1 operation has an autonomous workflow; periodic accrual recognition,
depreciation, and financing interest remain CLI-originated until a later hard format defines their
independent scheduling policy and complete derivation.

## Profile Failure Outcome

The exact semantic checks in this document run as the core contract's operation check 5. A tag,
role, multiplicity, balance, request-to-effect linkage, lifecycle relation, or amount equation
failure is attestation-request-profile-invalid. A system workflow mismatch runs at check 16 after
the authentic, historically valid system-purpose signer is established, yielding
attestation-system-derivation-invalid.
