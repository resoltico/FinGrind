---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_DISCOVERY
updated: "2026-07-21"
route:
  keywords: [fingrind, machine-contract, discovery, request-shapes, response-shapes, templates, workflow, contract-errors]
  questions: ["where is MachineContract documented", "where are request and response descriptor types documented", "where are discovery templates and workflow descriptors documented"]
---

# Machine Contract And Descriptor Reference

This file documents the exported `contract` owners that assemble machine discovery, define typed
request and response descriptors, publish templates and workflow scaffolds, and expose deterministic
contract failures.

## `MachineContract`

`MachineContract` is the public discovery assembler for `help`, `version`, `capabilities`,
`print-request-template`, and `print-plan-template`.

```java
public final class MachineContract
```

- Purpose: render discovery payloads from typed contract state instead of CLI-owned literals
- Versioning: `protocolVersion()` is the single canonical owner for the public discovery and
  machine-envelope contract line, and discovery JSON payloads carry that field directly so
  callers can detect hard public breaks without inferring from the application version
- Inputs: `ProtocolCatalog`, `ContractDiscovery`, the top-level discovery descriptor types,
  `ContractRequestShapes`, `ContractResponse`, and `ContractTemplates`
- Help behavior: `help()` now owns the canonical typed workflow and note inventory, while the CLI
  transport deliberately narrows the root help surface to one concise overview and uses
  `capabilities` as the deep doctrine/runtime contract
- Command-help behavior: `help(OperationId)` and the CLI `<command> --help` alias both scope the
  rendered discovery payload to one selected operation, while the CLI help renderer rewrites
  canonical `fingrind ...` examples and repair hints to the active launcher surface such as
  `./bin/fingrind` or `.\\bin\\fingrind.ps1`; request-file commands additionally inline the
  canonical request template, accepted field tables, and enum vocabularies so a caller can form a
  valid payload from the CLI alone, and typed `record-*` help narrows that payload guidance to the
  selected business-event variant instead of restating the full union write shape
- Template behavior: `requestTemplate()` emits deterministic placeholder-first request documents,
  and `planTemplate()` emits the canonical general workflow. Topic-selected `planTemplate(...)`
  emits atomic tax, fixed-asset, or financing setup plans with their prerequisite account
  declarations. The CLI raw-template commands route both help snippets and
  `print-request-template` / `print-plan-template` through the same canonical serializer so
  machine fixtures remain byte-identical to live command output and the checked-in public examples
  stay semantically aligned without drifting away from the placeholder scaffold contract

## `ScaffoldPlaceholders`, `WorkflowSurface`, `WorkflowDescriptor`, `WorkflowStepKind`, And `WorkflowStepDescriptor`

These public contract owners keep scaffold-reservation and help-workflow guidance typed.

```java
public final class ScaffoldPlaceholders
public enum WorkflowSurface
public record WorkflowDescriptor(...)
public enum WorkflowStepKind
public sealed interface WorkflowStepDescriptor
```

- `ScaffoldPlaceholders`: owns the canonical reserved sentinel vocabulary that the CLI request
  validators reject when callers try to smuggle internal scaffolding tokens into committed
  bookkeeping payloads
- `WorkflowSurface`: publishes the stable machine-readable quick-start surface keys such as the
  self-contained POSIX-shell and Windows-PowerShell bundle flows
- `WorkflowDescriptor`: groups one platform-specific quick-start sequence under its published
  workflow surface
- `WorkflowStepKind`: distinguishes command, edit, and note steps in the public help workflow
- `WorkflowStepDescriptor`: keeps each workflow step typed through explicit `Command`, `Edit`, and
  `Note` variants so machine consumers can distinguish runnable commands from canonical file-write
  payloads and explanatory notes without nullable payload slots

## `ContractDiscovery`, `ContractRequestShapes`, `ContractResponse`, And `ContractTemplates`

These public contract owners define the typed descriptor families used by `MachineContract`.

```java
public final class ContractDiscovery
public final class ContractRequestShapes
public final class ContractResponse
public final class ContractTemplates
```

- `ContractDiscovery`: discovery-descriptor registry used for coverage and contract audits
- `ContractDiscoveryDescriptor` is the sealed public owner that makes discovery-descriptor
  registration exhaustive instead of list-maintained
- `ApplicationIdentity`, `HelpDescriptor`, `CapabilitiesDescriptor`,
  `StorageSurfaceDescriptor`, `CommandCatalogDescriptor`, `VersionDescriptor`,
  `ArtifactOutputDescriptor`, `CommandDescriptor`, `ExitCodeDescriptor`,
  `EnvironmentRuntimeDescriptor`, `EnvironmentPublicationDescriptor`,
  `EnvironmentStorageDescriptor`, `EnvironmentSqliteDescriptor`, `EnvironmentDescriptor`, and
  `SqliteCompileOptionsVerificationStatus` are the top-level typed discovery payloads
- `MachineContract.protocolVersion()` is the canonical owner for the current public discovery and
  machine-envelope contract line, and `HelpDescriptor`, `CapabilitiesDescriptor`, and
  `VersionDescriptor` all publish that value directly
- `EnvironmentSqliteDescriptor.runtime` is an explicit state family with `ReadyRuntime`,
  `UnavailableRuntime`, `FailedRuntime`, and `IncompatibleRuntime`, so discovery payloads publish
  compile-option proof, trust basis, loaded-library facts, and failure detail only in the runtime
  states where those facts actually exist
- `EnvironmentStorageDescriptor.defaultProtectedBookFormat` publishes the canonical protected-book
  cipher, page-size, reserve-byte, and KDF facts as one typed contract record instead of leaking
  those defaults through loosely related scalar fields
- `HelpDescriptor.quickStart` is a typed `WorkflowDescriptor` list keyed by `WorkflowSurface`
  rather than a flat string array, so canonical quick starts can publish platform-aware command,
  file-write, and note steps without implying that one shell transcript fits every bundle target
  or forcing agents to parse prose to reconstruct JSON seed files
- `CommandCatalogDescriptor` groups full `CommandDescriptor` records by operation category, so the
  machine-readable `capabilities` payload publishes per-command identity, aliases, options,
  execution mode, output modes, artifact outputs, and summaries without falling back to one lossy
  global stdout-mode list
- `CommandDescriptor` keeps command identity, execution mode, output modes, and artifact outputs
  typed through `OperationId`, `ExecutionMode`, `OutputMode`, and `ArtifactOutputDescriptor`
  instead of flattening those closed vocabularies into strings
- `ContractRequestShapes`: transport request-input descriptors plus bookkeeping-entry,
  account-declaration, tax-registration declaration, and ledger-plan request-shape descriptors
- `ContractRequestShapes.RequestShapeDescriptorType` is the sealed nested owner that keeps the
  published request-shape inventory exhaustive
- `ContractRequestShapes.RequestInputDescriptor`, `.RequestShapesDescriptor`,
  `.BookkeepingEntryRequestShapeDescriptor`, `.DeclareAccountRequestShapeDescriptor`,
  `.DeclareTaxRegistrationRequestShapeDescriptor`, `.LedgerPlanRequestShapeDescriptor`,
  `.RequestFieldDescriptor`, `.EntryKindSemanticsDescriptor`, `.EvidenceRequirementDescriptor`,
  and `.EnumVocabularyDescriptor` are the nested typed request-shape descriptors
- `ContractRequestShapes.RequestShapesDescriptor` publishes `declareTaxRegistration` as one
  first-class sibling beside the bookkeeping-entry, account, and ledger-plan request shapes
- `ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor` publishes the nested request
  `foreignExchangeFields`, `quotedRateFields`, and `taxFields` inventories beside the
  journal-line, opening-balance, evidence, provenance, and reversal families
- `ContractRequestShapes.EntryKindSemanticsDescriptor` publishes one entry kind's required,
  optional, and forbidden top-level fields, described entry-specific `variantFields`, and the
  canonical `sourceDocumentType` policy for that entry kind; the selected command's human help
  projects those same field descriptors rather than maintaining a separate field list
- `ContractRequestShapes.EvidenceRequirementDescriptor` publishes the non-negotiable source-document
  minimum for every bookkeeping-entry request
- `RequestFieldPresence` is the stable request-field presence vocabulary shared by request-shape
  descriptors and executable schema authorship, with `required`, `conditional`, `optional`, and
  `forbidden` wire values
- `ContractResponse`: response-model, rejection, audit, preflight, currency, and plan-execution
  descriptors
- `ContractResponseCatalog` owns the complete published error and rejection descriptor union and
  requires every failure code to declare exactly one `ContractResponse.FailureCategory`; lookup has
  no fallback category
- `ContractResponse.FailureCategory` distinguishes `structural-invalid`, `domain-semantic`,
  `precondition`, `unsupported-selection`, and `internal` failures for machine consumers
- `ContractResponse.ResponseDescriptorType` is the sealed nested owner for the published response
  descriptor inventory
- `ContractResponse.BookModelDescriptor`, `.FieldDescriptor`, `.ErrorDescriptor`,
  `.ResponseModelDescriptor`, `.PlanExecutionDescriptor`, `.RejectionDescriptor`,
  `.AuditDescriptor`, `.AccountRegistryDescriptor`, `.InitializationRequirement`,
  `.ReversalDescriptor`, `.PreflightDescriptor`, `.CommitGuarantee`, `.CurrencyDescriptor`, and
  `.BookkeepingKernelDescriptor` are the nested typed response descriptors
- `ContractTemplates`: canonical request template descriptors
- `ContractPlanTemplates`: canonical ledger-plan template descriptors
- `TemplateDescriptorType` is the sealed owner for the published template-descriptor inventory
- `ContractPlanTemplates.LedgerPlanTemplateDescriptor`, `.LedgerPlanStepTemplateDescriptor`,
  `.LedgerPlanQueryTemplateDescriptor`, and
  `.LedgerAssertionTemplateDescriptor` are the nested typed ledger-plan template descriptors
- `ContractTemplates.PostingRequestTemplateDescriptor`, `.JournalLineTemplateDescriptor`,
  `.ProvenanceTemplateDescriptor`, `.ReversalTemplateDescriptor`,
  `.DeclareAccountTemplateDescriptor`, `.DeclareTaxRegistrationTemplateDescriptor`,
  `.DeclareTaxCodeTemplateDescriptor`, and related evidence descriptors are the nested typed
  request template descriptors
- `InventoryReliefTemplateDescriptor` is the top-level typed trading-sale relief descriptor
  reused by posting request templates instead of remaining buried as an anonymous object shape
- `ForeignExchangeTemplateDescriptor` and `QuotedExchangeRateTemplateDescriptor` are the
  top-level typed FX request template descriptors reused by posting request templates
- Template descriptors keep actor type, account type, entry side, normal balance, step kind,
  assertion kind, balance side, and exact money typed at the public boundary, and they reject
  structurally impossible ledger plan step or assertion combinations before any renderer publishes
  them

## `ContractSettlementTemplates`

`ContractSettlementTemplates` owns the request-template descriptors shared by settlement and
tax-aware posting scaffolds.

```java
public interface ContractSettlementTemplates
```

- `SettlementAdjunctTemplateDescriptor` names the account and positive monetary amount of one
  settlement adjunct
- `TaxSelectionTemplateDescriptor` names one tax registration and tax code, validating live
  identifiers while preserving documented scaffold placeholders
- Boundary: these reusable nested template facts are separate from `ContractTemplates`, which
  remains the owner of the complete posting-template shape

## `ContractPostingRequestTemplates`, `ContractRequestShapes.RetireAccountRequestShapeDescriptor`, And `ContractTemplates.RetireAccountTemplateDescriptor`

`ContractPostingRequestTemplates` owns the complete typed caller-authored posting scaffold. Its
`PostingRequestTemplateDescriptor` replaces the former nested template owner so that reusable
posting blocks compose through a single public contract root. `RetireAccountRequestShapeDescriptor`
and `RetireAccountTemplateDescriptor` publish the minimal account-retirement request shape and
scaffold as first-class discovery facts.

```java
public interface ContractPostingRequestTemplates
public record ContractRequestShapes.RetireAccountRequestShapeDescriptor(...)
public record ContractTemplates.RetireAccountTemplateDescriptor(String accountCode)
```

The retirement descriptors contain only the declared account code. Whether retirement is
admissible remains an Account Registry lifecycle decision based on the current balance and durable
references; the descriptor never implies that account history can be deleted.

## `PlanTemplateTopic`

`PlanTemplateTopic` is the closed vocabulary for the `--topic` value accepted by
`print-plan-template`. Its values such as `tax-setup`, `fixed-asset-setup`, and `financing-setup`
are template selectors, not independently invocable commands.

```java
public enum PlanTemplateTopic
```

The enum owns stable topic wire names and parsing. `MachineContract` uses the selected topic to
publish one executable atomic ledger-plan scaffold.

## `ContractFixedAssetTemplates`, `ContractFinancingTemplates`, And `ContractRealizedForeignExchangeTemplates`

```java
public interface ContractFixedAssetTemplates
public interface ContractFinancingTemplates
public interface ContractRealizedForeignExchangeTemplates
```

These context-owned descriptor families add typed fixed-asset, financing, and realized-FX blocks to the shared posting request template. They describe caller-authored source facts only; executor-owned carrying value, depreciation, financing balances, and realized gain or loss are not template inputs. Their field presence and allowed evidence are derived from the same request-contract metadata used for validation.

## `ContractErrors`, `ContractFailure`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException`

These contract-owned types publish deterministic non-rejection failures and the accepted-or-rejected
decision seam used by low-level contract boundaries.

```java
public final class ContractErrors
public record ContractFailure(...)
public record ContractFailurePaths(Path path, List<Path> relatedPaths)
public sealed interface ContractDecision<T>
public final class ContractFailureException extends IllegalStateException
```

- Purpose: distinguish malformed input and deterministic invocation failures from runtime failure
- Contract: `ContractErrors.Descriptor` owns stable error codes such as `invalid-request`,
  `invalid-page-cursor`, `protected-book-verification-failed`, `internal-defect`,
  `internal-error`, `managed-runtime-failure`, `storage-runtime-failure`,
  `pdf-export-failure`, and `interactive-prompt-unavailable`, plus the published process
  `exitCode` for each deterministic machine error
- `invalid-request` advertises structured `detailFields` when the malformed request reaches
  aggregated journal grammar validation, with `details.violations[]` carrying the full ordered
  set of detected issues
- `ContractFailure` carries the stable descriptor plus the caller-facing message, optional hint,
  optional argument name, and optional typed filesystem locations without routing expected failures
  through exceptions
- `ContractFailurePaths` canonically holds one primary filesystem `path` and distinct companion
  `relatedPaths`; public machine renderers publish those locations as data while human renderers
  apply their path-redaction policy
- `ContractDecision` carries either the accepted typed payload or one deterministic
  `ContractFailure`, letting internal seams return structured failures directly
- `ContractFailureException` is the imperative bridge for seams that still must throw while
  preserving an exact deterministic `ContractFailure` for higher layers to map back into the
  public machine contract

## `ContractTemplates.RecognitionIntervalTemplateDescriptor`

```java
public record ContractTemplates.RecognitionIntervalTemplateDescriptor(String startDate, String endDate)
```

`ContractTemplates.RecognitionIntervalTemplateDescriptor` publishes the inclusive recognition interval in accrual cut-off request scaffolds. It is present only for prepayments and deferred revenue, whose lifecycle recognition is schedule-bound.

## `ProtocolAccrualCutoffPostingRequestFieldSets`

```java
public final class ProtocolAccrualCutoffPostingRequestFieldSets
```

`ProtocolAccrualCutoffPostingRequestFieldSets` is the shared request-field contract for the five accrual cut-off commands. The parser, request schema, template, and help surfaces derive their required and optional fields from this one owner.

## `ProtocolPostEntryFields.RecognitionInterval`

```java
public static final class ProtocolPostEntryFields.RecognitionInterval
```

`ProtocolPostEntryFields.RecognitionInterval` owns the `recognitionInterval.startDate` and `recognitionInterval.endDate` field descriptors used by the request schema and public scaffolds.

## `DescriptorNamespaceSupport`

`DescriptorNamespaceSupport` is the discovery-namespace helper that enumerates permitted subclasses
for sealed descriptor roots.

```java
public final class DescriptorNamespaceSupport
```

- Purpose: keep descriptor inventory discovery exact and centralized instead of scattering
  reflection logic across discovery surfaces
- Validation: rejects descriptor roots that are not sealed before exposing their namespace members
