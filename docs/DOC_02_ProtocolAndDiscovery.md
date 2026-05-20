---
afad: "4.0"
version: "0.43.0"
domain: CONTRACT_PROTOCOL
updated: "2026-05-20"
route:
  keywords: [fingrind, contract, protocol, discovery, machine-contract, request-shapes, response-shapes, templates]
  questions: ["where is protocol metadata documented in fingrind", "which doc covers MachineContract and ContractDiscovery", "where are request and response descriptor types documented"]
---

# Contract Protocol And Discovery Reference

This file documents the exported `contract` surfaces that define FinGrind's protocol catalog,
discovery payloads, request and response descriptors, deterministic contract-error vocabulary, and
public book-format metadata.

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
  `ProtocolOperationDocumentation` owns the summary/examples prose plus typed example steps

## `ProtocolExampleStep`

`ProtocolExampleStep` keeps command help examples typed so executable grammar stays distinct from
operator guidance.

```java
public sealed interface ProtocolExampleStep
```

- Variants: `Command` for copy-pasteable invocations and `Note` for non-grammar operator guidance
- Purpose: prevent guidance text from masquerading as first-class command grammar in help,
  discovery, examples, and public CLI docs
- Consumers: `ProtocolOperationDocumentation`, command-help rendering, docs lint, and example sync

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

## `PlanResultDetail`

`PlanResultDetail` is the public result-detail vocabulary for `execute-plan`.

```java
public enum PlanResultDetail implements WireValue
```

- Members: `SUMMARY`, `FULL`
- Purpose: let callers choose between the default concise plan summary and the full per-step
  execution journal without inventing renderer-local flags or ad hoc booleans
- Surface: `wireValue()`, `wireValues()`, and `fromWireValue(...)`

## `ProtocolSuccessPayload`, `ProtocolSuccessStatus`, `ProtocolRejectionStatus`, `ProtocolFailureStatus`, And `ProtocolDiagnosticCode`

This marker interface plus these enums are the canonical owners of public envelope success payload
typing, status tokens, and diagnostics tokens.

```java
public sealed interface ProtocolSuccessPayload
public enum ProtocolSuccessStatus implements WireValue
public enum ProtocolRejectionStatus implements WireValue
public enum ProtocolFailureStatus implements WireValue
public enum ProtocolDiagnosticCode implements WireValue
```

- Purpose: distinguish success, deterministic rejection, and runtime failure statuses with
  compile-time subset boundaries instead of one open string bucket, keep post-success diagnostics
  codes typed instead of renderer-local string literals, and prevent arbitrary records from
  drifting onto the public success-envelope payload surface.
- Surface: `ProtocolSuccessPayload` as the marker interface plus `wireValue()`, `wireValues()`,
  and `fromWireValue(...)` on the enums.

## `ProtocolOptions`

`ProtocolOptions` owns canonical public CLI option spellings.

```java
public final class ProtocolOptions
```

- Purpose: keep option text consistent across parser, help, capabilities, templates, and docs
- Scope: book access, passphrase sources, request files, report output, PDF export, pagination,
  posting lookup, date filters, and `execute-plan` result detail

## `ProtocolContractSchemaKeys`

`ProtocolContractSchemaKeys` is the typed owner for the external field names used by the
protocol-owned JSON contract resources.

```java
final class ProtocolContractSchemaKeys
```

- Purpose: keep runtime loaders and build/distribution tooling aligned on one schema-key registry
  instead of duplicating field-name literals or partial maps
- Current scope: runtime-surface, public-distribution, managed-SQLite, bundle-layout, and the
  full lower-camel semantic map for the operation-id contract

## `OperationIdContract`

`OperationIdContract` is the JSON-backed wire-name registry for public FinGrind operations.

```java
final class OperationIdContract
```

- Purpose: keep operation wire ids in one resource-backed owner shared by typed catalog code,
  shell-side contract readers, and distribution verification
- Related contract: the semantic-key-to-enum-name mapping is explicitly owned by
  `contract-schema-keys.json`, so shell consumers do not infer semantic keys by transforming enum
  names

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
    List<PublicCliBundleTarget> supportedPublicCliBundleTargets,
    List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets)
```

- Purpose: keep release-target metadata in one typed owner shared by capabilities, docs, and build
  verification
- Validation: requires both bundle-target lists to be present in the canonical JSON resource and
  rejects unknown targets, duplicates, and overlap between the supported and unsupported lists
- Resource authority: loaded from the protocol-owned JSON contract resource instead of ad hoc
  build or shell literals

## `PublicCliBundleTarget`

`PublicCliBundleTarget` is the canonical host-classifier vocabulary for public CLI bundles.

```java
public enum PublicCliBundleTarget implements WireValue
```

- Purpose: keep bundle-target publication tied to one typed classifier vocabulary instead of open
  strings that can drift away from the real release matrix

## `ManagedSqliteContract`

`ManagedSqliteContract` is the protocol-owned managed native-library version pin shared across
runtime, bundle, and operator surfaces.

```java
record ManagedSqliteContract(String requiredMinimumSqliteVersion, String requiredSqlite3mcVersion)
```

- Purpose: keep the required SQLite and SQLite3 Multiple Ciphers versions in one canonical
  contract owner instead of copying version literals through build scripts, bundle docs, and shell
  verification

## `BundleLayoutContract`

`BundleLayoutContract` is the protocol-owned per-target bundle layout registry.

```java
record BundleLayoutContract(Map<PublicCliBundleTarget, BundleTarget> bundleTargets)
```

- Purpose: keep archive format, launcher path, launcher command, and native library filename tied
  to one typed target owner shared by build logic, bundle metadata, and operator verification
- Validation: requires one layout entry for every `PublicCliBundleTarget`

## `BundleLayoutContract.BundleTarget`

`BundleLayoutContract.BundleTarget` is one canonical bundle layout descriptor.

```java
record BundleTarget(
    String operatingSystemId,
    String architectureId,
    String archiveFormat,
    String launcherPath,
    String launcherCommand,
    String sqliteLibraryFileName)
```

- Purpose: expose the per-target self-contained archive facts that the bundle manifest and
  acceptance scripts must report without reauthoring platform switch statements

## `PlanTransactionMode`, And `PlanFailurePolicy`

These enums are the canonical ledger-plan execution semantics.

```java
public enum PlanTransactionMode implements WireValue
public enum PlanFailurePolicy implements WireValue
```

- Purpose: keep the plan execution contract typed through the public discovery/model surface

## `RuntimeDistribution`, `PublicCliDistribution`, `StorageDriver`, `StorageEngine`, `BookProtectionMode`, `BookCipher`, `SqliteLibraryMode`, `SqliteRuntimeProvenance`, `SqliteRuntimeTrustBasis`, `SqliteRuntimeStatus`, And `SqliteRuntimeStateValidator`

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
public enum SqliteRuntimeProvenance implements WireValue
public enum SqliteRuntimeTrustBasis implements WireValue
public enum SqliteRuntimeStatus implements WireValue
public final class SqliteRuntimeStateValidator
```

- Purpose: keep runtime distribution, public bundle identity, storage backend, protected-book
  defaults, managed SQLite loading mode, runtime provenance, runtime trust basis, and runtime
  readiness in enum-owned wire vocabularies
- `SqliteRuntimeTrustBasis`: publishes whether the selected runtime is
  `publisher-authenticated` or `source-verified-local-build`, so machine consumers can distinguish
  public bundle identity from checkout-local build identity
- `SqliteRuntimeStatus`: distinguishes `ready`, `unavailable`, `failed`, and `incompatible`, so
  discovery can separate missing-runtime failures from late probe failures after one concrete
  library target was already resolved
- `SqliteRuntimeStateValidator`: canonical owner for the valid runtime-state matrix shared by the
  public descriptor layer and the SQLite runtime probe surface
- Validation surface: `wireValue()`, `wireValues()`, `fromWireValue(...)`, and typed discovery
  records such as `EnvironmentDistributionDescriptor`, `EnvironmentStorageDescriptor`, and
  `EnvironmentSqliteDescriptor`
- Operational reach: build logic, bundle metadata/launchers, Docker staging, and shell verifiers
  consume the same protocol-owned runtime-surface contract instead of carrying private copies of
  those wire values

## `BookModelFacts`, `CurrencyFacts`, `AccountingBaselineFacts`, `AccountingBaselineTarget`, `ReportCapabilityFacts`, `AccountingPolicyPackFacts`, `PolicyDimensionFacts`, `ExtensionSurfaceFacts`, `PolicySeamFacts`, `CapabilityStatus`, `PreflightFacts`, And `PlanExecutionFacts`

These typed records publish FinGrind's hard public model facts.

```java
public record BookModelFacts(...)
public record CurrencyFacts(...)
public record AccountingBaselineFacts(...)
public enum AccountingBaselineTarget ...
public record ReportCapabilityFacts(...)
public record AccountingPolicyPackFacts(...)
public record PolicyDimensionFacts(...)
public record ExtensionSurfaceFacts(...)
public record PolicySeamFacts(...)
public enum CapabilityStatus ...
public record PreflightFacts(...)
public record PlanExecutionFacts(...)
```

- Purpose: keep book-model scope, currency support, advisory preflight behavior, and ledger-plan
  execution semantics structured before help or capabilities render them
- Policy: these are not CLI-local prose constants
- Related types: `BookBoundaryFact`, `BookEntityScopeFact`, `BookFilesystemFact`,
  `BookCredentialFact`, `BookInitializationFact`, `BookAccountRegistryFact`,
  and `BookCurrencyScopeFact` are the semantic text wrappers carried by `BookModelFacts`
- `AccountingBaselineTarget`: publishes the current and next declared accounting-foundation target
  so the protocol can distinguish bookkeeping-kernel scope from broader reporting ambitions
- `ReportCapabilityFacts`: publishes the current status, target-baseline relevance, bounded-context
  owner, and blocking model gaps for each statement/reporting capability
- `AccountingPolicyPackFacts`: publishes the built-in neutral policy-pack identity, supported
  entity forms, and policy-dimension coverage
- `PolicyDimensionFacts`: publishes one structured status row for the executable accounting-basis,
  comparatives, chart, close, and statement-presentation dimensions
- `CapabilityStatus`: publishes the shared lifecycle vocabulary for implemented, planned,
  future-context, deliberately-excluded, and unsupported capability states
- `AccountingBaselineFacts`: publishes the declared standards posture, explicit non-claims,
  current/next baseline targets, built-in statement inventory, report-capability inventory,
  default policy pack, small-entity position, organizational
  position, and the ISO-vs-IFRS clarification as typed machine facts instead of burying that scope
  only in prose documentation
- `PolicySeamFacts`: publishes the structured inventory of executable policy seams
- `ExtensionSurfaceFacts`: publishes the current policy-pack model, the default pack id, the
  implemented extension seams, and the structured seam inventory without promoting roadmap domains
  into the machine contract

## `MonetaryAmount`

`MonetaryAmount` is the canonical machine-facing exact-money object reused by posting,
ledger-plan, discovery, and response contracts.

```java
public record MonetaryAmount(String currencyCode, String minorUnits)
```

- Purpose: keep public money facts typed at the machine boundary instead of routing them through
  free-form money strings
- Shape: one ISO currency code plus one exact non-negative minor-unit string
- Validation: rejects non-ISO currencies, redundant leading zeroes, non-digit minor-unit text,
  minor-unit strings longer than 19 digits, and out-of-range exact amounts

## `ProtocolSharedRequestFields`

`ProtocolSharedRequestFields` owns the cross-request field names reused by declare-account,
posting journal lines, and ledger-plan query/assertion payloads.

```java
public final class ProtocolSharedRequestFields
```

- Purpose: give shared request vocabulary one canonical owner before surface-specific field classes
  project it into their local JSON scopes

## `ProtocolMoneyFields`

`ProtocolMoneyFields` owns the canonical JSON field names for nested exact-money objects.

```java
public final class ProtocolMoneyFields
```

- Purpose: keep public exact-money object fields in one canonical owner shared by machine
  contracts, request parsing, templates, response renderers, and docs
- Current fields: `currencyCode` and `minorUnits`, returned in stable wire order by `fields()`

## `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields`

These protocol-owned constant classes keep public JSON field names canonical for their
surface-specific JSON scopes.

```java
public final class ProtocolOpenBookFields
public final class ProtocolDeclareAccountFields
public final class ProtocolPostEntryFields
public final class ProtocolLedgerPlanFields
```

- Purpose: prevent request parsing, templates, capabilities, and docs from carrying divergent
  field-name registries
- `ProtocolOpenBookFields`: owns the nested `entityName`, `functionalCurrency`, and
  `fiscalYearStart` field names for explicit book initialization
- `ProtocolPostEntryFields.TopLevel`, `.JournalLine`, `.Provenance`, and `.Reversal` group the
  canonical posting-request field families by JSON object scope; journal-line `amount` is one
  nested exact-money object keyed by `ProtocolMoneyFields`
- `ProtocolLedgerPlanFields.Plan`, `.Step`, `.Query`, and `.Assertion` group the canonical
  ledger-plan field families by JSON object scope; assertion `netAmount` is the same nested
  exact-money object shape

## `MachineContract`

`MachineContract` is the public discovery assembler for `help`, `version`, `capabilities`,
`print-request-template`, and `print-plan-template`.

```java
public final class MachineContract
```

- Purpose: render discovery payloads from typed contract state instead of CLI-owned literals
- Inputs: `ProtocolCatalog`, `ContractDiscovery`, the top-level discovery descriptor types,
  `ContractRequestShapes`, `ContractResponse`, and `ContractTemplates`
- Help behavior: `help()` now owns the canonical typed workflow and note inventory, while the CLI
  transport deliberately narrows the root help surface to one concise overview and uses
  `capabilities` as the deep doctrine/runtime contract
- Command-help behavior: `help(OperationId)` and the CLI `<command> --help` alias both scope the
  rendered discovery payload to one selected operation, while the CLI help renderer rewrites
  canonical `fingrind ...` examples and repair hints to the active launcher surface such as
  `./bin/fingrind` or `.\bin\fingrind.ps1`; request-file commands additionally inline the
  canonical request template, accepted field tables, and enum vocabularies so a caller can form a
  valid payload from the CLI alone
- Template behavior: `requestTemplate()` and `planTemplate()` emit deterministic runnable sample
  documents with demo evidence and provenance values, while the CLI raw-template commands now
  route both help snippets and `print-request-template` / `print-plan-template` through the same
  canonical serializer so checked-in template fixtures remain byte-identical to live command
  output without drifting into stale placeholder doctrine

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
  `EnvironmentDistributionDescriptor`, `EnvironmentStorageDescriptor`,
  `EnvironmentSqliteDescriptor`, `EnvironmentDescriptor`, and
  `SqliteCompileOptionsVerificationStatus` are the top-level typed discovery payloads
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
  `.ReversalDescriptor`, `.PreflightDescriptor`, `.CommitGuarantee`, `.CurrencyDescriptor`,
  `.AccountingBaselineDescriptor`, and `.ExtensionSurfaceDescriptor` are the nested typed
  response descriptors
- `ContractTemplates`: canonical request and ledger-plan template descriptors
- `ContractTemplates.TemplateDescriptorType` is the sealed nested owner for the published
  template-descriptor inventory
- `ContractTemplates.OpenBookTemplateDescriptor`, `.PostingRequestTemplateDescriptor`,
  `.JournalLineTemplateDescriptor`, `.ProvenanceTemplateDescriptor`, `.ReversalTemplateDescriptor`,
  `.LedgerPlanTemplateDescriptor`, `.LedgerPlanStepTemplateDescriptor`,
  `.LedgerPlanQueryTemplateDescriptor`, `.DeclareAccountTemplateDescriptor`, and
  `.LedgerAssertionTemplateDescriptor` are the nested typed template descriptors
- Template descriptors keep actor type, account type, entry side, normal balance, step kind,
  assertion kind, balance side, and exact money typed at the public boundary, and they reject
  structurally impossible ledger plan step or assertion combinations before any renderer publishes
  them

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
  `invalid-page-cursor`, `protected-book-verification-failed`, `managed-runtime-failure`,
  `storage-runtime-failure`, `pdf-export-failure`, and `interactive-prompt-unavailable`
- `invalid-request` now advertises structured `detailFields` when the malformed request reaches
  aggregated journal grammar validation, with `details.violations[]` carrying the full ordered
  set of detected issues
- `ContractFailure` carries the stable descriptor plus the caller-facing message, optional hint,
  and optional argument name without routing expected failures through exceptions
- `ContractDecision` carries either the accepted typed payload or one deterministic
  `ContractFailure`, letting internal seams return structured failures directly
- `ContractFailureException` is the imperative bridge for seams that still must throw while
  preserving an exact deterministic `ContractFailure` for higher layers to map back into the
  public machine contract

## `DescriptorNamespaceSupport`

`DescriptorNamespaceSupport` is the discovery-namespace helper that enumerates permitted subclasses
for sealed descriptor roots.

```java
public final class DescriptorNamespaceSupport
```

- Purpose: keep descriptor inventory discovery exact and centralized instead of scattering
  reflection logic across discovery surfaces
- Validation: rejects descriptor roots that are not sealed before exposing their namespace members

## `BookFormatContract`

`BookFormatContract` is the canonical public owner of the FinGrind SQLite book identity and format
version constants.

```java
public final class BookFormatContract
```

- Purpose: keep the stable `application_id` and supported on-disk format version in one contract
  owner shared by inspections, fixtures, and storage adapters
- Current contract: `APPLICATION_ID = 1179079236` and `FORMAT_VERSION = 12`

## `ProtectedBookFormatContract`

`ProtectedBookFormatContract` is the canonical public owner of the active SQLite protected-book
format defaults that discovery, runtime verification, fixtures, and shell/build contracts share.

```java
public record ProtectedBookFormatContract(...)
```

- Purpose: keep one typed contract for the default cipher, page size, reserve bytes, legacy mode,
  legacy page size, KDF iteration count, and plaintext-header policy instead of duplicating those
  facts across runtime discovery, fixture metadata, and build-time contract readers
- Discovery reach: `EnvironmentStorageDescriptor.defaultProtectedBookFormat` exposes the same
  typed defaults through `capabilities`, so machine consumers do not need to reconstruct the
  protected-book format from partial scalar fields
- Verification reach: the managed SQLite runtime probe, committed compatibility fixture metadata,
  and newly created protected books all compare back to this contract before repository gates pass

## `SqliteRuntimeArtifactEvidence`

`SqliteRuntimeArtifactEvidence` is the typed discovery payload that identifies the provenance
sidecars for one loaded managed-SQLite runtime artifact.

```java
public record SqliteRuntimeArtifactEvidence(...)
```

- Purpose: publish the toolchain-fingerprint path and digest plus the build-contract path and
  digest through one typed runtime descriptor instead of scattering native-artifact provenance
  fields across CLI renderers, discovery payloads, and verification scripts
- Boundary: discovery/runtime descriptors may carry this evidence only when one concrete managed
  SQLite artifact was selected and verified
- Trust split: the evidence identifies the selected artifact sidecars, while
  `SqliteRuntimeTrustBasis` remains the owner of the trust posture vocabulary for that artifact
