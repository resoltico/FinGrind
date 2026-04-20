---
afad: "3.5"
version: "0.19.0"
domain: CONTRACT_EXECUTOR
updated: "2026-04-19"
route:
  keywords: [fingrind, contract, executor, protocol-catalog, machine-contract, discovery, request-shapes, reports, ledger-plan, preflight, posting, rejection, uuid-v7]
  questions: ["how does the contract boundary work in fingrind", "which public request and result models exist in fingrind", "where do ledger plans and reports live in fingrind", "which executor services consume the contract types"]
---

# Contract And Executor API Reference

This file documents the exported `contract` module plus the `executor` services that act on those
types. `contract` owns public request/result models, protocol metadata, discovery descriptors,
ledger plans, reports, and deterministic error vocabularies. `executor` owns services and narrow
storage seams that consume those public types.

## `ProtocolCatalog`

`ProtocolCatalog` is the contract-owned registry for public FinGrind operation metadata and hard
book-model facts.

```java
public final class ProtocolCatalog
```

- Purpose: own operation ids, aliases, display labels, output modes, usage lines, summaries,
  examples, query limits, public bundle targets, and fixed bookkeeping limitations
- Consumers: CLI parsing, `help`, `capabilities`, `MachineContract`, docs lint, and examples

## `ProtocolOperation`

`ProtocolOperation` is one structured command descriptor in the protocol catalog.

```java
public record ProtocolOperation(...)
```

- Purpose: keep command metadata machine-readable before any renderer serializes it

## `OperationId`

`OperationId` is the canonical enum of public FinGrind operation identifiers.

```java
public enum OperationId
```

- Scope: discovery, administration, query/report, and write operations
- Wire contract: `wireName()` is the stable command id used by CLI, examples, and capabilities

## `OperationCategory`

`OperationCategory` groups operations for discovery payloads.

```java
public enum OperationCategory {
  DISCOVERY,
  ADMINISTRATION,
  QUERY,
  WRITE
}
```

- Purpose: drive grouped capabilities output from one enum-backed owner

## `ExecutionMode`

`ExecutionMode` describes the public envelope shape of one operation.

```java
public enum ExecutionMode
```

- Members: `JSON_ENVELOPE`, `RAW_JSON`
- Purpose: distinguish normal envelopes from raw template JSON

## `OutputMode`

`OutputMode` is the public output-selection vocabulary for commands that advertise `--output`.

```java
public enum OutputMode
```

- Members: `JSON`, `HUMAN`, `CSV`
- Purpose: keep output-mode parsing and rendering enum-owned instead of switch-local
- Surface: `wireValue()`, `wireValues()`, `fromWireValue(...)`, and branch-owning `run(...)`

## `ProtocolStatuses`

`ProtocolStatuses` is the canonical owner of public envelope status tokens.

```java
public final class ProtocolStatuses
```

- Purpose: distinguish generic success, posting commit, plan commit, plan rejection, plan
  assertion failure, business rejection, and runtime failure without magic strings

## `ProtocolLimits`

`ProtocolLimits` owns shared public paging bounds.

```java
public final class ProtocolLimits
```

- Purpose: keep pagination defaults and hard limits out of parser-local literals
- Current contract: `DEFAULT_PAGE_LIMIT = 50`, `PAGE_LIMIT_MAX = 200`

## `ProtocolOptions`

`ProtocolOptions` owns canonical public CLI option spellings.

```java
public final class ProtocolOptions
```

- Purpose: keep option text consistent across parser, help, capabilities, templates, and docs
- Scope: book access, passphrase sources, request files, report output, PDF export, pagination,
  posting lookup, and date filters

## `ProtocolArtifactOutput`

`ProtocolArtifactOutput` is one contract-owned export descriptor for a non-stdout artifact.

```java
public record ProtocolArtifactOutput(String format, String option, String description)
```

- Purpose: advertise supported artifact outputs without ad hoc CLI strings
- Current scope: PDF export through `ProtocolArtifactOutput.pdf()`

## `PublicDistributionContract`

`PublicDistributionContract` is the protocol-owned bundle-target contract loaded from a resource.

```java
public record PublicDistributionContract(
    List<String> supportedPublicCliBundleTargets,
    List<String> unsupportedPublicCliOperatingSystems)
```

- Purpose: keep release-target metadata in one typed owner shared by capabilities, docs, and build
  verification
- Validation: rejects blanks and duplicates

## `BookModelFacts`, `CurrencyFacts`, `PreflightFacts`, And `PlanExecutionFacts`

These typed records publish FinGrind's hard public model facts.

```java
public record BookModelFacts(...)
public record CurrencyFacts(...)
public record PreflightFacts(...)
public record PlanExecutionFacts(...)
```

- Purpose: keep book-model scope, currency support, advisory preflight behavior, and ledger-plan
  execution semantics structured before help or capabilities render them
- Policy: these are not CLI-local prose constants

## `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields`

These protocol-owned constant classes keep public JSON field names canonical.

```java
public final class ProtocolDeclareAccountFields
public final class ProtocolPostEntryFields
public final class ProtocolLedgerPlanFields
```

- Purpose: prevent request parsing, templates, capabilities, and docs from carrying divergent
  field-name registries
- `ProtocolPostEntryFields.TopLevel`, `.JournalLine`, `.Provenance`, and `.Reversal` group the
  canonical posting-request field families by JSON object scope
- `ProtocolLedgerPlanFields.Plan`, `.Step`, `.Query`, and `.Assertion` group the canonical
  ledger-plan field families by JSON object scope

## `MachineContract`

`MachineContract` is the public discovery assembler for `help`, `version`, `capabilities`,
`print-request-template`, and `print-plan-template`.

```java
public final class MachineContract
```

- Purpose: render discovery payloads from typed contract state instead of CLI-owned literals
- Inputs: `ProtocolCatalog`, `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, and
  `ContractTemplates`

## `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates`

These public namespace classes own the typed descriptor families used by `MachineContract`.

```java
public final class ContractDiscovery
public final class ContractRequestShapes
public final class ContractResponse
public final class ContractTemplates
```

- `ContractDiscovery`: help, capabilities, version, environment, exit-code, and command
  descriptors
- `ContractDiscovery.ApplicationIdentity`, `.HelpDescriptor`, `.CapabilitiesDescriptor`,
  `.VersionDescriptor`, `.ArtifactOutputDescriptor`, `.CommandDescriptor`,
  `.ExitCodeDescriptor`, and `.EnvironmentDescriptor` are the nested typed discovery payloads
- `ContractRequestShapes`: request-input plumbing plus posting, account-declaration, and ledger-plan
  request-shape descriptors
- `ContractRequestShapes.RequestInputDescriptor`, `.RequestShapesDescriptor`,
  `.PostEntryRequestShapeDescriptor`, `.DeclareAccountRequestShapeDescriptor`,
  `.LedgerPlanRequestShapeDescriptor`, `.RequestFieldDescriptor`, and
  `.EnumVocabularyDescriptor` are the nested typed request-shape descriptors
- `ContractResponse`: response-model, rejection, audit, preflight, currency, and plan-execution
  descriptors
- `ContractResponse.BookModelDescriptor`, `.FieldDescriptor`, `.ErrorDescriptor`,
  `.ResponseModelDescriptor`, `.PlanExecutionDescriptor`, `.RejectionDescriptor`,
  `.AuditDescriptor`, `.AccountRegistryDescriptor`, `.ReversalDescriptor`,
  `.PreflightDescriptor`, and `.CurrencyDescriptor` are the nested typed response descriptors
- `ContractTemplates`: canonical request and ledger-plan template descriptors
- `ContractTemplates.PostingRequestTemplateDescriptor`, `.JournalLineTemplateDescriptor`,
  `.ProvenanceTemplateDescriptor`, `.ReversalTemplateDescriptor`,
  `.LedgerPlanTemplateDescriptor`, `.LedgerPlanStepTemplateDescriptor`,
  `.DeclareAccountTemplateDescriptor`, and `.LedgerAssertionTemplateDescriptor` are the nested
  typed template descriptors

## `ContractErrors` And `ContractErrorException`

These types own deterministic CLI-visible error vocabulary outside ordinary business rejections.

```java
public final class ContractErrors
public final class ContractErrorException extends IllegalStateException
```

- Purpose: distinguish malformed input and deterministic invocation failures from runtime failure
- Contract: `ContractErrors.Descriptor` owns stable error codes such as `invalid-request`,
  `invalid-page-cursor`, `book-authentication-failed`, and `interactive-prompt-unavailable`

## `BookMigrationPolicy`

`BookMigrationPolicy` is the public vocabulary of supported on-disk migration strategies.

```java
public enum BookMigrationPolicy {
  SEQUENTIAL_IN_PLACE
}
```

- Purpose: make book migration policy explicit in inspection and discovery payloads
- Wire contract: `wireValue()` returns `sequential-in-place`

## `BookAdministrationService`

`BookAdministrationService` owns explicit book initialization and account-registry writes.

```java
public final class BookAdministrationService
```

- Constructor: requires `BookAdministrationSession` and `Clock`
- Surface: `openBook()` and `declareAccount(DeclareAccountCommand)`
- Policy: stamps lifecycle timestamps from the application clock

## `BookReadService`

`BookReadService` owns lifecycle inspection, read-side queries, and office-worker reports.

```java
public final class BookReadService
```

- Constructor: requires `BookReadSession`
- Surface: `inspectBook()`, `listAccounts(...)`, `getPosting(...)`, `listPostings(...)`,
  `accountBalance(...)`, `trialBalance(...)`, `accountLedger(...)`, `periodSummary(...)`

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance)
```

- Purpose: keep account-registry writes typed at the contract boundary

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and
read/report surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Rejected`

## `RekeyBookResult`

`RekeyBookResult` is the closed result family for explicit passphrase rotation.

```java
public sealed interface RekeyBookResult
```

- Variants: `Rekeyed`, `Rejected`

## `BookInspection`

`BookInspection` is the machine-readable lifecycle and compatibility snapshot returned by
`inspect-book`.

```java
public sealed interface BookInspection
```

- Variants: `Missing`, `BlankSqlite`, `Initialized`, `ForeignSqlite`,
  `UnsupportedFormatVersion`, `IncompleteFinGrind`
- Purpose: distinguish missing, blank, initialized, foreign, unsupported, and incomplete books
- Wire state: `status().wireValue()` is the stable lower-case/hyphenated vocabulary

## `ListAccountsQuery`

`ListAccountsQuery` is the paginated read model for the declared-account registry.

```java
public record ListAccountsQuery(int limit, int offset)
```

- Purpose: keep account-registry paging explicit

## `AccountPage`

`AccountPage` is one stable page of declared accounts.

```java
public record AccountPage(List<DeclaredAccount> accounts, int limit, int offset, boolean hasMore)
```

- Purpose: couple one account slice to its paging metadata

## `ListAccountsResult`

`ListAccountsResult` is the closed result family for `list-accounts`.

```java
public sealed interface ListAccountsResult
```

- Variants: `Listed`, `Rejected`

## `GetPostingResult`

`GetPostingResult` is the closed result family for committed-posting lookup.

```java
public sealed interface GetPostingResult
```

- Variants: `Found`, `Rejected`

## `EffectiveDateRange`

`EffectiveDateRange` is the sealed effective-date filter shared by reads, reports, and assertions.

```java
public sealed interface EffectiveDateRange
```

- Variants: `Unbounded`, `From`, `To`, `Bounded`
- Purpose: make date-bound combinations structural instead of using loosely related optionals
- Surface: `contains(...)` plus factory helpers like `of(...)` and `unbounded()`

## `PostingPageCursor`

`PostingPageCursor` is the stable opaque keyset cursor for reverse-chronological posting history.

```java
public record PostingPageCursor(LocalDate effectiveDate, Instant recordedAt, PostingId postingId)
```

- Purpose: continue posting-history pagination without offset scans
- Wire contract: `wireValue()` and `fromWireValue(...)` own the base64url binary encoding

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

## `PostingPage`

`PostingPage` is one stable page of committed postings.

```java
public record PostingPage(
    List<PostingFact> postings,
    int limit,
    Optional<PostingPageCursor> nextCursor)
```

- Purpose: couple one posting-history slice to the next keyset cursor

## `ListPostingsResult`

`ListPostingsResult` is the closed result family for posting-history queries.

```java
public sealed interface ListPostingsResult
```

- Variants: `Listed`, `Rejected`

## `AccountBalanceQuery`

`AccountBalanceQuery` is the grouped-balance request for one declared account.

```java
public record AccountBalanceQuery(
    AccountCode accountCode,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo)
```

- Purpose: request grouped per-currency totals for one account, optionally within an effective-date
  window

## `CurrencyBalance`

`CurrencyBalance` is one per-currency grouped balance bucket.

```java
public record CurrencyBalance(
    Money debitTotal,
    Money creditTotal,
    Money netAmount,
    NormalBalance balanceSide)
```

- Purpose: report grouped debit, credit, and net totals while preserving exact decimal semantics

## `AccountBalanceSnapshot`

`AccountBalanceSnapshot` is the payload returned by `account-balance`.

```java
public record AccountBalanceSnapshot(
    DeclaredAccount account,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    List<CurrencyBalance> balances)
```

- Purpose: keep account identity, optional date filters, and grouped balances together

## `AccountBalanceResult`

`AccountBalanceResult` is the closed result family for grouped-balance queries.

```java
public sealed interface AccountBalanceResult
```

- Variants: `Reported`, `Rejected`

## `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult`

These types own the book-wide trial-balance report surface.

```java
public record TrialBalanceQuery(Optional<LocalDate> effectiveDateTo)
public record TrialBalanceRow(DeclaredAccount account, CurrencyBalance balance)
public record TrialBalanceReport(Optional<LocalDate> effectiveDateTo, List<TrialBalanceRow> rows)
public sealed interface TrialBalanceResult
```

- Purpose: request, carry, and result-wrap one as-of trial balance for the selected book
- Result variants: `Reported`, `Rejected`

## `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult`

These types own the running ledger surface for one declared account.

```java
public record AccountLedgerQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange)
public record AccountLedgerEntry(
    PostingFact postingFact,
    CurrencyBalance movement,
    Money runningNetAmount,
    BalanceSide runningBalanceSide)
public record AccountLedgerReport(...)
public sealed interface AccountLedgerResult
```

- Purpose: request and carry one running ledger with opening balances, activity rows, and closing
  balances
- Result variants: `Reported`, `Rejected`

## `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult`

These types own the bounded book-wide period summary surface.

```java
public record PeriodSummaryQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record PeriodCurrencySummary(CurrencyBalance totals)
public record PeriodAccountActivityRow(DeclaredAccount account, CurrencyBalance movement)
public record PeriodSummaryReport(...)
public sealed interface PeriodSummaryResult
```

- Purpose: request and carry bounded posting counts, currency totals, and flattened account activity
- Result variants: `Reported`, `Rejected`

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic lifecycle and account-registry
refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`,
  `NormalBalanceConflict`

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query/report refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`

## `RejectionNarrative`

`RejectionNarrative` owns user-facing rejection prose and plan-journal failure facts.

```java
public final class RejectionNarrative
```

- Purpose: prevent plan execution and CLI rendering from leaking Java class names as rejection text

## `PostingLineage`

`PostingLineage` is the structural direct-versus-reversal lineage carried by commands, drafts, and
committed facts.

```java
public sealed interface PostingLineage
```

- Variants: `Direct`, `Reversal`
- Purpose: keep direct postings and reversal postings structurally distinct

## `PostEntryCommand`

`PostEntryCommand` is the application-layer request to preflight or commit one journal entry.

```java
public record PostEntryCommand(
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
```

- Purpose: carry the write-boundary payload after CLI parsing and request validation

## `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult`

These result families separate posting preflight from posting commit while preserving one shared
contract surface.

```java
public sealed interface PostEntryResult
public sealed interface PreflightEntryResult extends PostEntryResult
public sealed interface CommitEntryResult extends PostEntryResult
```

- Purpose: make `preflight-entry` unable to return `Committed` and `post-entry` unable to return
  `PreflightAccepted` at compile time

## `PostingRequest`

`PostingRequest` is the minimal posting shape shared by preflight and commit validation.

```java
public interface PostingRequest
```

- Surface: `journalEntry()`, `postingLineage()`, `requestProvenance()`
- Purpose: keep shared write validation independent of transport adapters

## `PostingDraft`

`PostingDraft` is the commit-ready posting model that defers `postingId` allocation until the store
accepts the write.

```java
public record PostingDraft(
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    CommittedProvenance provenance)
```

- Purpose: separate accepted commit metadata from durable id assignment
- Surface: `materialize(PostingId)` creates the final `PostingFact`

## `LedgerPlanId` And `LedgerStepId`

These value types are the caller-supplied stable identifiers for one plan and one plan step.

```java
public record LedgerPlanId(String value)
public record LedgerStepId(String value)
```

- Purpose: keep plan and step identity typed instead of carrying raw strings through the contract

## `LedgerPlan`

`LedgerPlan` is the canonical AI-authored workflow document executed by `execute-plan`.

```java
public record LedgerPlan(LedgerPlanId planId, List<LedgerStep> steps)
```

- Purpose: bundle one ordered workflow with stable per-step ids
- Validation: rejects blank plan ids, empty step lists, duplicate step ids, and `open-book` outside
  the first step

## `LedgerStep`

`LedgerStep` is the sealed family of executable plan steps.

```java
public sealed interface LedgerStep
```

- Families: `OpenBook`, `DeclareAccount`, `PreflightEntry`, `PostEntry`, `InspectBook`,
  `ListAccounts`, `GetPosting`, `ListPostings`, `AccountBalance`, `TrialBalance`,
  `AccountLedger`, `PeriodSummary`, `Assert`
- Purpose: keep plan execution exhaustively typed instead of routing through maps

## `LedgerAssertion`

`LedgerAssertion` is the sealed family of first-class postcondition checks for AI-agent workflows.

```java
public sealed interface LedgerAssertion
```

- Families: `AccountDeclared`, `AccountActive`, `PostingExists`, `AccountBalanceEquals`
- Purpose: let one plan assert intended outcomes without inventing CLI-local test commands

## `LedgerFact`

`LedgerFact` is the sealed family of typed per-step journal observations.

```java
public sealed interface LedgerFact
```

- Families: `Text`, `Flag`, `Count`, `Group`
- Purpose: keep step observations machine-readable without collapsing everything to strings

## `LedgerStepKind`, `LedgerAssertionKind`, `LedgerStepStatus`, And `LedgerPlanStatus`

These enums own the stable ledger-plan wire vocabulary.

```java
public enum LedgerStepKind
public enum LedgerAssertionKind
public enum LedgerStepStatus
public enum LedgerPlanStatus
```

- Purpose: keep plan/journal tokens compiler-owned and renderer-independent

## `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult`

These types carry the durable execution record returned by `execute-plan`.

```java
public sealed interface LedgerJournalEntry
public record LedgerExecutionJournal(...)
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts)
public sealed interface LedgerPlanResult
```

- Purpose: return one plan-level result plus one per-step journal that agents can inspect safely
- Bound: `LedgerPlan` accepts at most 100 steps, which bounds full journal responses

## `PostingApplicationService`

`PostingApplicationService` owns preflight and commit behavior for posting entries.

```java
public final class PostingApplicationService
```

- Constructor: requires `PostingBookSession`, `PostingIdGenerator`, and `Clock`
- Surface: `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`

## `LedgerPlanService`

`LedgerPlanService` owns atomic multi-step execution for `execute-plan`.

```java
public final class LedgerPlanService
```

- Constructor: requires `LedgerPlanSession`, `PostingIdGenerator`, and `Clock`
- Policy: runs the whole plan inside one durable transaction and rolls back on the first rejected
  step or failed assertion

## `PostingIdGenerator`

`PostingIdGenerator` supplies the next posting identity during commit.

```java
public interface PostingIdGenerator {
  PostingId nextPostingId();
}
```

- Purpose: keep posting-id generation explicit and injectable

## `UuidV7PostingIdGenerator`

`UuidV7PostingIdGenerator` is FinGrind's project-owned production posting-id generator.

```java
public final class UuidV7PostingIdGenerator implements PostingIdGenerator
```

- Purpose: generate time-ordered UUID v7 posting ids without an external dependency

## `PostingRejection`

`PostingRejection` is the closed family of deterministic write-side refusals.

```java
public sealed interface PostingRejection
```

- Variants: `BookNotInitialized`, `AccountStateViolations`, `DuplicateIdempotencyKey`,
  `ReversalTargetNotFound`, `ReversalAlreadyExists`, `ReversalDoesNotNegateTarget`
- Purpose: keep validly parsed but inadmissible postings machine-distinguishable
