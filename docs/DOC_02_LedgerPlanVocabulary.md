---
afad: "5.0.1"
version: "0.62.0"
domain: CONTRACT_EXECUTOR_WRITE_LEDGER_PLAN_VOCABULARY
updated: "2026-07-30"
scope:
  paths: ["contract", "executor", "cli"]
  symbols: ["LedgerStepKind", "LedgerJournalKind", "LedgerAssertionKind", "LedgerBoundaryCheckpoint", "LedgerStepStatus", "LedgerPlanStatus", "LedgerPlanAttestationDisposition", "LedgerPlanAttestationCommitMode", "LedgerPlanAttestationCredentialMode"]
route:
  keywords: [ledger-plan, workflow-journal, plan-status, plan-step, attestation-disposition, appended, read-only, no-durable-child-mutation]
  questions: ["which wire values describe FinGrind ledger-plan steps and journals", "when does an execute-plan result carry an attestation commitment", "what does no-durable-child-mutation mean for a ledger plan"]
---

# Ledger Plan Vocabulary And Attestation Outcomes

This document is the canonical public wire-vocabulary and attestation-outcome owner for
`execute-plan`. [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md) owns the
posting and plan models that use this vocabulary.

## `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus`

These types own the stable ledger-plan wire vocabulary.

```java
public enum LedgerStepKind
public sealed interface LedgerJournalKind
public enum LedgerAssertionKind
public enum LedgerBoundaryCheckpoint
public enum LedgerStepStatus
public enum LedgerPlanStatus
```

- Purpose: keep plan/journal tokens compiler-owned and renderer-independent
- Ownership: every standard `LedgerJournalKind` is its canonical `LedgerStepKind`; only the
  journal-only `plan-boundary` marker is represented by `LedgerJournalKind.BoundaryKind`
- Surface: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `LedgerPlanAttestationDisposition`, `LedgerPlanAttestationCommitMode`, And `LedgerPlanAttestationCredentialMode`

These enums own the closed attestation outcome contract for a successful ledger plan.

```java
public enum LedgerPlanAttestationDisposition
public enum LedgerPlanAttestationCommitMode
public enum LedgerPlanAttestationCredentialMode
```

- `LedgerPlanAttestationDisposition`: a successful plan's closed aggregate-attestation outcome:
  it owns both exact response modes, never nullable/optional interpretation. `appended` is
  `attestationCommit: required` and `attestationCredentials: required`; `read-only` is
  `attestationCommit: must-be-null` and `attestationCredentials: prohibited`; and
  `no-durable-child-mutation` is `attestationCommit: must-be-null` and
  `attestationCredentials: required`. `must-be-null` requires the field to be rendered explicitly
  as JSON `null`, rather than omitted.
