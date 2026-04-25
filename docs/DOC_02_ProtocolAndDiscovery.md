---
afad: "3.5"
version: "0.25.0"
domain: CONTRACT_PROTOCOL
updated: "2026-04-25"
route:
  keywords: [fingrind, contract, protocol, discovery, machine-contract, request-shapes, response-shapes, templates, migration-policy]
  questions: ["where is protocol metadata documented in fingrind", "which doc covers MachineContract and ContractDiscovery", "where are request and response descriptor types documented"]
---

# Contract Protocol And Discovery Reference

This file documents the exported `contract` surfaces that define FinGrind's protocol catalog,
discovery payloads, request and response descriptors, deterministic contract-error vocabulary, and
public migration-policy metadata.

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
- Related types: `ProtocolCommandSignature` owns display label, aliases, options, and usage;
  `ProtocolOperationOutputs` owns execution/output modes plus artifact outputs; and
  `ProtocolOperationDocumentation` owns the summary/examples prose

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
public enum ExecutionMode implements WireValue
```

- Members: `JSON_ENVELOPE`, `RAW_JSON`
- Purpose: distinguish normal envelopes from raw template JSON
- Wire contract: `wireValue()` owns the stable machine-readable execution-mode vocabulary

## `OutputMode`

`OutputMode` is the public output-selection vocabulary for commands that advertise `--output`.

```java
public enum OutputMode implements WireValue
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
- Resource authority: loaded from the protocol-owned JSON contract resource instead of ad hoc
  build or shell literals

## `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, And `SqliteRuntimeStatus`

These enums are the public runtime-surface vocabularies shared by discovery payloads, CLI
rendering, shell verifiers, and build/distribution checks.

```java
public enum RuntimeDistribution implements WireValue
public enum PublicCliDistribution implements WireValue
public enum StorageDriver implements WireValue
public enum StorageEngine implements WireValue
public enum BookProtectionMode implements WireValue
public enum BookCipher implements WireValue
public enum SqliteLibraryMode implements WireValue
public enum SqliteRuntimeStatus implements WireValue
```

- Purpose: keep runtime distribution, public bundle identity, storage backend, protected-book
  defaults, managed SQLite loading mode, and runtime readiness in enum-owned wire vocabularies
- Validation surface: `wireValue()`, `wireValues()`, `fromWireValue(...)`, and typed discovery
  records such as `EnvironmentDistributionDescriptor`, `EnvironmentStorageDescriptor`, and
  `EnvironmentSqliteDescriptor`
- Operational reach: build logic, bundle metadata/launchers, Docker staging, and shell verifiers
  consume the same protocol-owned runtime-surface contract instead of carrying private copies of
  those wire values

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
- Related types: `BookBoundaryFact`, `BookEntityScopeFact`, `BookFilesystemFact`,
  `BookCredentialFact`, `BookInitializationFact`, `BookAccountRegistryFact`,
  `BookMigrationFact`, and `BookCurrencyScopeFact` are the semantic text wrappers carried by
  `BookModelFacts`

## `ProtocolSharedRequestFields`

`ProtocolSharedRequestFields` owns the cross-request field names reused by declare-account,
posting journal lines, and ledger-plan query/assertion payloads.

```java
public final class ProtocolSharedRequestFields
```

- Purpose: give shared request vocabulary one canonical owner before surface-specific field classes
  project it into their local JSON scopes

## `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields`

These protocol-owned constant classes keep public JSON field names canonical for their
surface-specific JSON scopes.

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
- Inputs: `ProtocolCatalog`, `ContractDiscovery`, the top-level discovery descriptor types,
  `ContractRequestShapes`, `ContractResponse`, and `ContractTemplates`
- Template behavior: `requestTemplate(clock)` and `planTemplate(clock)` stamp their example
  `effectiveDate` fields from `LocalDate.now(clock)`, so checked-in template fixtures mirror the
  live request shape without promising byte-identical current-date output forever

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
  `EnvironmentDistributionDescriptor`, `EnvironmentStorageDescriptor`,
  `EnvironmentSqliteDescriptor`, `EnvironmentDescriptor`, and
  `SqliteCompileOptionsVerificationStatus` are the top-level typed discovery payloads
- `CommandCatalogDescriptor` and `CommandDescriptor` keep command grouping, command identity,
  execution mode, and output modes typed through `OperationId`, `ExecutionMode`, and `OutputMode`
  instead of flattening those closed vocabularies into strings
- `ContractRequestShapes`: request-input plumbing plus posting, account-declaration, and ledger-plan
  request-shape descriptors
- `ContractRequestShapes.RequestShapeDescriptorType` is the sealed nested owner that keeps the
  published request-shape inventory exhaustive
- `ContractRequestShapes.RequestInputDescriptor`, `.RequestShapesDescriptor`,
  `.PostEntryRequestShapeDescriptor`, `.DeclareAccountRequestShapeDescriptor`,
  `.LedgerPlanRequestShapeDescriptor`, `.RequestFieldDescriptor`, and
  `.EnumVocabularyDescriptor` are the nested typed request-shape descriptors
- `RequestFieldPresence` is the stable request-field presence vocabulary shared by request-shape
  descriptors and executable schema authorship, with `required`, `conditional`, `optional`, and
  `forbidden` wire values
- `ContractResponse`: response-model, rejection, audit, preflight, currency, and plan-execution
  descriptors
- `ContractResponse.ResponseDescriptorType` is the sealed nested owner for the published response
  descriptor inventory
- `ContractResponse.BookModelDescriptor`, `.FieldDescriptor`, `.ErrorDescriptor`,
  `.ResponseModelDescriptor`, `.PlanExecutionDescriptor`, `.RejectionDescriptor`,
  `.AuditDescriptor`, `.AccountRegistryDescriptor`, `.InitializationRequirement`,
  `.ReversalDescriptor`, `.PreflightDescriptor`, `.CommitGuarantee`, and
  `.CurrencyDescriptor` are the nested typed response descriptors
- `ContractTemplates`: canonical request and ledger-plan template descriptors
- `ContractTemplates.TemplateDescriptorType` is the sealed nested owner for the published
  template-descriptor inventory
- `ContractTemplates.PostingRequestTemplateDescriptor`, `.JournalLineTemplateDescriptor`,
  `.ProvenanceTemplateDescriptor`, `.ReversalTemplateDescriptor`,
  `.LedgerPlanTemplateDescriptor`, `.LedgerPlanStepTemplateDescriptor`,
  `.LedgerPlanQueryTemplateDescriptor`, `.DeclareAccountTemplateDescriptor`, and
  `.LedgerAssertionTemplateDescriptor` are the nested typed template descriptors
- Template descriptors keep actor type, entry side, normal balance, step kind, assertion kind,
  and balance side typed at the public boundary, and they reject structurally impossible ledger
  plan step or assertion combinations before any renderer publishes them

## `ContractErrors`, `ContractFailure`, `ContractDecision`, And `ContractFailureException`

These contract-owned types publish deterministic non-rejection failures and the accepted-or-rejected
decision seam used by low-level contract boundaries.

```java
public final class ContractErrors
public record ContractFailure(...)
public sealed interface ContractDecision<T>
public final class ContractFailureException extends IllegalStateException
```

- Purpose: distinguish malformed input and deterministic invocation failures from runtime failure
- Contract: `ContractErrors.Descriptor` owns stable error codes such as `invalid-request`,
  `invalid-page-cursor`, `book-authentication-failed`, `managed-runtime-failure`,
  `storage-runtime-failure`, `pdf-export-failure`, and `interactive-prompt-unavailable`
- `ContractFailure` carries the stable descriptor plus the caller-facing message, optional hint,
  and optional argument name without routing expected failures through exceptions
- `ContractDecision` carries either the accepted typed payload or one deterministic
  `ContractFailure`, letting internal seams return structured failures directly
- `ContractFailureException` is the imperative bridge for seams that still must throw while
  preserving an exact deterministic `ContractFailure` for higher layers to map back into the
  public machine contract

## `BookMigrationPolicy`

`BookMigrationPolicy` is the public vocabulary of supported on-disk migration strategies.

```java
public enum BookMigrationPolicy {
  SEQUENTIAL_IN_PLACE
}
```

- Purpose: make book migration policy explicit in inspection and discovery payloads
- Wire contract: `wireValue()` returns `sequential-in-place`
