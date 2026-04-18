---
afad: "3.5"
version: "0.16.0"
domain: CONTRACT_EXECUTOR
updated: "2026-04-18"
route:
  keywords: [fingrind, contract, executor, open-book, declare-account, inspect-book, list-postings, account-balance, preflight, rejection, uuid-v7, machine-contract, protocol-catalog, ledger-plan]
  questions: ["how does the contract boundary work in fingrind", "what query models exist in fingrind", "how are posting ids generated in fingrind", "where does execute-plan live in fingrind"]
---

# Contract And Executor API Reference

This file documents the public `contract` module plus the `executor` services that consume those
types. `contract` owns public request/result models, protocol metadata, machine contract
descriptors, ledger plans, and journals. `executor` owns the services and narrow seams that act on
those public types.

## `ProtocolCatalog`

`ProtocolCatalog` is the contract-owned registry for public FinGrind operation metadata and hard
book-model facts.

```java
public final class ProtocolCatalog
```

- Purpose: keep operation ids, aliases, display labels, usage lines, execution modes, summaries,
  examples, paging limits, and hard bookkeeping limitations in one typed owner
- Consumers: CLI parsing, `help`, `capabilities`, `MachineContract`, contract lint tests, docs, and
  AI-agent templates

## `ProtocolOperation`

`ProtocolOperation` is one structured public operation descriptor.

```java
public record ProtocolOperation(...)
```

- Purpose: keep command metadata machine-readable before any renderer serializes it

## `OperationId`

`OperationId` is the canonical enum of public FinGrind operation identifiers.

```java
public enum OperationId
```

- Members include discovery (`PRINT_REQUEST_TEMPLATE`, `PRINT_PLAN_TEMPLATE`), administration
  (`OPEN_BOOK`, `REKEY_BOOK`, `DECLARE_ACCOUNT`), query (`INSPECT_BOOK`, `LIST_ACCOUNTS`,
  `GET_POSTING`, `LIST_POSTINGS`, `ACCOUNT_BALANCE`), and write (`EXECUTE_PLAN`,
  `PREFLIGHT_ENTRY`, `POST_ENTRY`) operations

## `ProtocolLimits` And `ProtocolOptions`

`ProtocolLimits` and `ProtocolOptions` keep shared paging bounds and canonical CLI option strings
out of ad hoc renderers.

```java
public final class ProtocolLimits
public final class ProtocolOptions
```

## `ProtocolStatuses`

`ProtocolStatuses` is the canonical owner for public JSON envelope status tokens.

```java
public final class ProtocolStatuses
```

- Purpose: distinguish generic success, posting commit, plan commit, rejection, and error envelopes
- Current split: `committed` is single-posting success; `plan-committed` is ledger-plan success

## `ProtocolLedgerPlanFields`

`ProtocolLedgerPlanFields` is the canonical owner for ledger-plan JSON field-name constants.

```java
public final class ProtocolLedgerPlanFields
```

- Purpose: keep `planId`, `steps`, `kind`, `posting`, `query`, and assertion field names out of
  parser-local string registries
- Consumers: `MachineContract` request-shape descriptors and `CliRequestReader` validation

## `PlanExecutionFacts`

`PlanExecutionFacts` is the core-owned descriptor for fixed ledger-plan execution semantics.

```java
public record PlanExecutionFacts(
    String transactionMode, String failurePolicy, String journal, List<String> hardLimitations)
```

- Purpose: advertise atomic execution, halt-on-first-failure behavior, journal shape, and hard
  plan limitations without accepting no-op request knobs

## `BookAdministrationService`

`BookAdministrationService` owns explicit book initialization and account-registry writes.

```java
public final class BookAdministrationService
```

- Constructor: requires `BookAdministrationSession` and `Clock`
- Surface: `openBook()`, `declareAccount(DeclareAccountCommand)`
- Policy: stamps `initializedAt` and `declaredAt` from the application clock instead of trusting caller input

## `BookQueryService`

`BookQueryService` owns read-only inspection, listing, posting-history, and balance workflows.

```java
public final class BookQueryService
```

- Constructor: requires `BookQuerySession`
- Surface: `inspectBook()`, `listAccounts(...)`, `getPosting(...)`, `listPostings(...)`, `accountBalance(...)`
- Policy: maps unopened books, unknown query accounts, and missing posting ids into explicit query rejections

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance)
```

- Purpose: keep account-registry writes typed at the contract boundary
- Validation: rejects `null` fields

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and query surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns
- Validation: rejects `null` value fields

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`
- Purpose: keep book lifecycle outcomes explicit instead of throwing for ordinary rejections

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Rejected`
- Purpose: return the current declared-account shape or a deterministic refusal

## `RekeyBookResult`

`RekeyBookResult` is the closed result family for explicit book-passphrase rotation.

```java
public sealed interface RekeyBookResult
```

- Variants: `Rekeyed`, `Rejected`
- Purpose: keep passphrase rotation explicit at the contract boundary instead of leaking SQLite-local types

## `BookInspection`

`BookInspection` is the machine-readable lifecycle and compatibility snapshot returned by `inspect-book`.

- Type: sealed interface with explicit variants for missing, blank SQLite, initialized, foreign
  SQLite, unsupported format, and incomplete FinGrind books
- Wire state: `status().wireValue()` is the public lower-case/hyphenated vocabulary used by CLI
  `payload.state`
- Purpose: let callers distinguish missing, blank, initialized, foreign, unsupported-version, and incomplete books before mutating them
- Policy: exposes the current sequential in-place migration policy and the detected versus
  supported book format versions before mutation

## `ListAccountsQuery`

`ListAccountsQuery` is the paginated read model for the declared-account registry.

```java
public record ListAccountsQuery(int limit, int offset)
```

- Purpose: make account-registry paging explicit instead of leaving `list-accounts` unbounded
- Validation: requires `limit` between `1` and `200`, and non-negative `offset`

## `AccountPage`

`AccountPage` is one stable page of declared accounts.

```java
public record AccountPage(List<DeclaredAccount> accounts, int limit, int offset, boolean hasMore)
```

- Purpose: keep paging metadata coupled to the returned account slice
- Validation: defensively copies `accounts`, requires positive `limit`, and requires non-negative `offset`

## `ListAccountsResult`

`ListAccountsResult` is the closed result family for `list-accounts`.

```java
public sealed interface ListAccountsResult
```

- Variants: `Listed`, `Rejected`
- Purpose: keep paged account-registry reads explicit at the contract layer

## `GetPostingResult`

`GetPostingResult` is the closed result family for committed-posting lookup.

```java
public sealed interface GetPostingResult
```

- Variants: `Found`, `Rejected`
- Purpose: distinguish a found committed posting from query-side deterministic rejection

## `ListPostingsQuery`

`ListPostingsQuery` is the filtered paging model for committed posting history.

```java
public record ListPostingsQuery(
    Optional<AccountCode> accountCode,
    EffectiveDateRange effectiveDateRange,
    int limit,
    Optional<PostingPageCursor> cursor)
```

- Purpose: keep history filtering and pagination typed at the contract boundary
- Validation: requires a sensible date range, a positive `limit`, and an explicit `Optional` cursor

## `PostingPage`

`PostingPage` is one stable page of committed postings.

```java
public record PostingPage(
    List<PostingFact> postings,
    int limit,
    Optional<PostingPageCursor> nextCursor)
```

- Purpose: couple one posting-history slice to the opaque cursor needed for the next keyset page
- Validation: defensively copies `postings`, requires a positive `limit`, and rejects `null`
  cursors

## `ListPostingsResult`

`ListPostingsResult` is the closed result family for posting-history queries.

```java
public sealed interface ListPostingsResult
```

- Variants: `Listed`, `Rejected`
- Purpose: keep posting-history reads explicit instead of overloading `null` or exceptions

## `AccountBalanceQuery`

`AccountBalanceQuery` is the grouped-balance request for one declared account.

```java
public record AccountBalanceQuery(
    AccountCode accountCode,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo)
```

- Purpose: request grouped per-currency totals for one account, optionally within an effective-date window
- Validation: rejects `null` fields and invalid date-range order

## `CurrencyBalance`

`CurrencyBalance` is one per-currency grouped balance bucket.

```java
public record CurrencyBalance(
    Money debitTotal,
    Money creditTotal,
    Money netAmount,
    NormalBalance balanceSide)
```

- Purpose: report grouped debit, credit, and net totals while preserving zero-valued totals as `Money`
- Validation: rejects `null` fields

## `AccountBalanceSnapshot`

`AccountBalanceSnapshot` is the read-model payload for `account-balance`.

```java
public record AccountBalanceSnapshot(
    DeclaredAccount account,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    List<CurrencyBalance> balances)
```

- Purpose: keep account identity, optional date filters, and grouped balances together in one response payload
- Validation: rejects `null` fields and defensively copies `balances`

## `AccountBalanceResult`

`AccountBalanceResult` is the closed result family for grouped-balance queries.

```java
public sealed interface AccountBalanceResult
```

- Variants: `Reported`, `Rejected`
- Purpose: keep grouped-balance reads explicit at the contract boundary

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic book-lifecycle refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`, `NormalBalanceConflict`
- Purpose: distinguish lifecycle and registry-state refusals from malformed requests or runtime failure

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query-side refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`
- Purpose: keep query-side misses distinct from malformed input and runtime failure

## `MachineContract`

`MachineContract` now acts as the public discovery assembler only. Typed descriptor payloads live in
focused namespaces and `MachineContract` renders them from contract-owned protocol metadata.

```java
public final class MachineContract
```

- Purpose: render `help`, `version`, `capabilities`, `print-request-template`, and
  `print-plan-template` from typed contract state plus `ProtocolCatalog` operation, model,
  plan-execution, preflight, currency, status, and limit facts
- Descriptor namespaces:
  `ContractDiscovery`, `ContractTemplates`, `ContractRequestShapes`, and `ContractResponse`
- Request-field ownership:
  `ProtocolPostEntryFields`, `ProtocolDeclareAccountFields`, and `ProtocolLedgerPlanFields`
- Live vocabularies: derives enum vocabularies from `JournalLine.EntrySide`, `ActorType`, and `NormalBalance`
- Live rejections: derives rejection descriptors from administration, query, and posting sealed families
- Explicit policy: CLI code renders this payload but does not reauthor operation ids, summaries,
  display labels, execution modes, or hard book-model limitation text

## `LedgerPlan`

`LedgerPlan` is the canonical AI-agent workflow document executed by `execute-plan`.

```java
public record LedgerPlan(String planId, List<LedgerStep> steps)
```

- Purpose: bundle one ordered workflow with stable per-step ids
- Validation: rejects blank `planId`, empty step lists, duplicate `stepId` values, and `open-book`
  outside the first step
- Derived behavior: `beginsWithOpenBook()` is the single source used by CLI storage access and the
  executor initialization guard

## `LedgerStep`

`LedgerStep` is the sealed family of executable plan steps.

```java
public sealed interface LedgerStep
```

- Families: `OpenBook`, `DeclareAccount`, `PreflightEntry`, `PostEntry`, `InspectBook`,
  `ListAccounts`, `GetPosting`, `ListPostings`, `AccountBalance`, and `Assert`
- Purpose: keep plan execution exhaustively typed instead of routing through unstructured maps
- Journal key: `kind()` returns the canonical operation or assertion token emitted in request and
  journal payloads

## `LedgerAssertion`

`LedgerAssertion` is the sealed family of first-class postcondition checks for AI-agent workflows.

```java
public sealed interface LedgerAssertion
```

- Families: `AccountDeclared`, `AccountActive`, `PostingExists`, `AccountBalanceEquals`
- Purpose: let one plan assert intended outcomes without inventing CLI-local test commands
- Journal key: `kind()` returns `assert`, while `detailKind()` exposes
  `assert-account-declared`, `assert-account-active`, `assert-posting-exists`, or
  `assert-account-balance`

## `LedgerJournalEntry`, `LedgerExecutionJournal`, And `LedgerPlanResult`

These contract types carry the durable execution record returned by `execute-plan`.

```java
public sealed interface LedgerJournalEntry
public record LedgerExecutionJournal(...)
public sealed interface LedgerPlanResult
```

- Purpose: return one plan-level result plus one per-step journal that an agent can inspect safely
- Status model: plan and step statuses are sealed through enums instead of free-form strings
- Journal shape: each `LedgerJournalEntry` variant carries typed `LedgerStepKind` plus a nullable
  typed `LedgerAssertionKind` only for assertion steps; the CLI maps them to canonical wire
  strings at the renderer boundary
- Bound: `LedgerPlan` accepts at most 100 steps, which bounds complete plan-journal responses

## `RejectionNarrative`

`RejectionNarrative` owns user-facing rejection prose shared by CLI envelopes and plan journals.

```java
public final class RejectionNarrative
```

- Purpose: prevent plan execution from leaking Java class names as rejection messages
- Facts: exposes compact sealed `LedgerFact.Text`, `LedgerFact.Flag`, and `LedgerFact.Count`
  values for plan step failures without coupling CLI `details` shape to executor internals

## `PostEntryCommand`

`PostEntryCommand` is the application-layer request to preflight or commit one journal entry.

## `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult`

These result families separate posting preflight from posting commit while preserving one shared
CLI response writer surface.

```java
public sealed interface PostEntryResult
public sealed interface PreflightEntryResult extends PostEntryResult
public sealed interface CommitEntryResult extends PostEntryResult
```

- Purpose: make `preflight-entry` unable to return `Committed` and `post-entry` unable to return
  `PreflightAccepted` at compile time
- Variants: `PreflightAccepted` and `PreflightRejected` belong to preflight, while `Committed`
  and `CommitRejected` belong to commit

```java
public record PostEntryCommand(
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
```

- Purpose: carry the balanced journal body, typed direct-versus-reversal lineage, accepted request
  provenance, and ingress channel into the write boundary
- Validation: rejects `null` journal entry, posting lineage, request provenance, and source channel

## `PostingRequest`

`PostingRequest` is the minimal posting shape shared by preflight commands and durable commit drafts.

```java
public interface PostingRequest
```

- Surface: `journalEntry()`, `postingLineage()`, `requestProvenance()`
- Purpose: let application preflight and transactional commit validation share the same rules without duplicating request-shape assumptions

## `PostingDraft`

`PostingDraft` is the commit-ready posting model that defers `postingId` allocation until the store accepts the write.

```java
public record PostingDraft(
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    CommittedProvenance provenance)
```

- Purpose: separate accepted commit metadata from durable id assignment
- Surface: `materialize(PostingId)` creates the final `PostingFact`

## `PostingApplicationService`

`PostingApplicationService` owns preflight and commit behavior for posting entries.

```java
public final class PostingApplicationService
```

- Constructor: requires `PostingBookSession`, `PostingIdGenerator`, and `Clock`
- Surface: exposes `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`
- Policy: reuses shared posting validation for lifecycle, account-state, idempotency, and reversal-lineage checks
- Commit path: stamps `CommittedProvenance`, builds a `PostingDraft`, and lets the store allocate `postingId` only after commit acceptance

## `LedgerPlanService`

`LedgerPlanService` owns atomic multi-step execution for `execute-plan`.

```java
public final class LedgerPlanService
```

- Constructor: requires `LedgerPlanSession`, `PostingIdGenerator`, and `Clock`
- Surface: `execute(LedgerPlan)`
- Policy: runs the whole plan inside one durable transaction, rolls back on the first rejected
  step or failed assertion, and returns the resulting `LedgerPlanResult`
- Step model: reuses the same administration, query, posting, and assertion logic as the
  single-command surface instead of inventing a second rules engine

## `PostingIdGenerator`

`PostingIdGenerator` supplies the next posting identity during commit.

```java
public interface PostingIdGenerator {
  PostingId nextPostingId();
}
```

- Purpose: keep posting-id generation explicit and injectable at the executor boundary

## `UuidV7PostingIdGenerator`

`UuidV7PostingIdGenerator` is FinGrind's project-owned production posting-id generator.

```java
public final class UuidV7PostingIdGenerator implements PostingIdGenerator
```

- Purpose: generate time-ordered UUID v7 posting identities without adding an external dependency
- Production default: the CLI's default SQLite workflow uses this generator for committed `postingId` values

## `PostingRejection`

`PostingRejection` is the closed family of deterministic domain refusals for posting requests.

```java
public sealed interface PostingRejection
```

- Variants: `BookNotInitialized`, `AccountStateViolations`, `DuplicateIdempotencyKey`,
  `ReversalTargetNotFound`, `ReversalAlreadyExists`, `ReversalDoesNotNegateTarget`
- Purpose: keep validly parsed but inadmissible posting requests machine-distinguishable
