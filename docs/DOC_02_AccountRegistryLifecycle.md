---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_ACCOUNT_REGISTRY
updated: "2026-07-23"
route:
  keywords: [fingrind, contract, account-registry, account-lifecycle, amend-account, retire-account, declared-account]
  questions: ["where are account lifecycle commands documented", "how does amend-account work", "how does retire-account preserve historical reversals"]
---

# Account Registry Lifecycle Reference

This file documents the public Account Registry operations that evolve a declared account without
deleting the durable ledger identity it anchors.

## `AmendAccountCommand`, `AmendAccountResult`, `RetireAccountCommand`, And `RetireAccountResult`

These types own the Account Registry lifecycle operations that evolve an account without deleting
retained accounting history.

```java
public record AmendAccountCommand(...)
public sealed interface AmendAccountResult
public record RetireAccountCommand(AccountCode accountCode)
public sealed interface RetireAccountResult
```

- `AmendAccountCommand`: carries one complete replacement definition for an account that has no
  postings, tax-registration bindings, or child accounts
- `AmendAccountResult`: variants `Amended`, `Unchanged`, `Rejected`; `Amended` carries the exact
  `AttestationCommit`, while `Unchanged` carries no commit and a successful result preserves the
  account code and original declaration timestamp
- `RetireAccountCommand`: identifies one account to remove from new ordinary authored use
- `RetireAccountResult`: variants `Retired`, `Unchanged`, `Rejected`; `Retired` carries the exact
  `AttestationCommit`, while `Unchanged` carries no commit. Retirement requires zero current
  balance and no live tax-registration or child-account binding, but retained posting reversals
  remain admissible
- Boundary: no delete-account command or result exists because retirement preserves the account's
  ledger identity and history

## `ContraAccountInvalid`

`ContraAccountInvalid` is the Account Registry rejection for a declared `contraOfAccountCode`
relationship that violates the chart doctrine.

```java
public record ContraAccountInvalid(
    AccountCode accountCode,
    AccountCode contraOfAccountCode,
    ContraAccountRelationshipViolation violation)
```

The rejection preserves both the candidate and target account identities plus the exact closed
violation reason, so callers can correct the relationship without inferring the cause from a
generic taxonomy failure.

## `AccountAmendmentOutcome`, `AccountRegistryDependency`, `AccountRegistryLifecyclePolicy`, `AccountRegistryLifecycleRejection`, `AccountRegistryPublishedLanguageTranslator`, And `AccountRetirementOutcome`

The executor owns the lifecycle decision before persistence, while the public contract keeps the
same rejection grammar visible to callers.

```java
public sealed interface AccountAmendmentOutcome
public enum AccountRegistryDependency
public final class AccountRegistryLifecyclePolicy
public sealed interface AccountRegistryLifecycleRejection
public final class AccountRegistryPublishedLanguageTranslator
public sealed interface AccountRetirementOutcome
```

- `AccountAmendmentOutcome` and `AccountRetirementOutcome` keep the executor's admitted,
  unchanged, and rejected paths explicit before the result is published
- `AccountRegistryDependency` names the durable or live relationship that prevents a lifecycle
  change: postings, tax registrations, or child accounts
- `AccountRegistryLifecyclePolicy` applies the amendment and retirement preconditions without
  permitting deletion or mutation of posted account taxonomy
- `AccountRegistryLifecycleRejection` supplies the public and local typed failures for a missing
  account, dependent account, or non-zero retirement balance
- `AccountRegistryPublishedLanguageTranslator` is the boundary translator for lifecycle request
  and result types; the general bookkeeping translator remains responsible for shared account
  snapshots and administration rejections
