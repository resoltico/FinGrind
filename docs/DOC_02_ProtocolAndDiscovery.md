---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_PROTOCOL
updated: "2026-07-22"
route:
  keywords: [fingrind, contract, protocol, discovery, machine-contract, request-shapes, response-shapes, templates, attestation credential, enroll-key, rollover-key, revoke-key, alter-policy, tax-setup, declare-tax-registration, amend-account, retire-account, tax-obligation]
  questions: ["where is protocol metadata documented in fingrind", "where is the attestation credential and policy request surface documented", "where is the tax setup request surface documented", "where are account lifecycle commands documented"]
---

# Contract Protocol And Discovery Reference

This file documents the exported `contract` surfaces that define FinGrind's protocol catalog,
runtime and distribution facts, shared request-field vocabularies, and public book-format metadata.

## `ProtocolCatalog`

`ProtocolCatalog` is the contract-owned registry for public FinGrind operation metadata and hard
book-model facts.

```java
public final class ProtocolCatalog
```

- Purpose: own operation ids, aliases, display labels, output modes, usage lines, summaries,
  examples, query limits, public bundle targets, and fixed bookkeeping limitations
- Consumers: CLI parsing, `help`, `capabilities`, `MachineContract`, docs lint, and examples

## `CapabilityCatalog`, `CapabilityCatalogEntry`, And `CapabilityStatus`

`CapabilityCatalog` is the canonical owner of FinGrind's published capability scope. Each
`CapabilityCatalogEntry` supplies a stable capability id, a `CapabilityStatus`, and an operative
boundary when the status is partial.

```java
public final class CapabilityCatalog
public record CapabilityCatalogEntry(
    String id, CapabilityStatus status, @Nullable String operativeBoundary)
public enum CapabilityStatus { IMPLEMENTED, PARTIAL, EXCLUDED }
```

- Invariant: every partial entry has one nonblank operative boundary; implemented and excluded
  entries do not carry an unowned boundary
- Consumers: `ProtocolDomainCatalog`, `CapabilitiesDescriptor`, the accounting-kernel scope ADR
  renderer, and the ADR parity contract test
- Discovery reach: `capabilities --output json --detail full` publishes the canonical list at
  `fullContract.capabilityCatalog`; `capabilities --output json --focus capability-catalog`
  publishes the same complete list as a focused slice, and text capabilities renders the same
  source in its Capability Scope section
- Boundary: this catalog owns published implementation scope, not command grammar, storage
  schema, or prospective capability design

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

- Members: `JSON`, `TEXT`, `CSV`
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

## `ProtocolSuccessPayload` And `ProtocolEnvelopeStatus`

This marker interface plus this enum are the canonical owners of public envelope success payload
typing and top-level status tokens.

```java
public sealed interface ProtocolSuccessPayload
public enum ProtocolEnvelopeStatus implements WireValue
```

- Purpose: distinguish success, deterministic rejection, and runtime failure statuses through one
  canonical top-level envelope vocabulary and prevent arbitrary records from drifting onto the
  public success-envelope payload surface.
- Surface: `ProtocolSuccessPayload` as the marker interface plus `wireValue()`, `wireValues()`,
  and `fromWireValue(...)` on the enum.

## `ProtocolOptions`

`ProtocolOptions` owns canonical public CLI option spellings.

```java
public final class ProtocolOptions
```

- Purpose: keep option text consistent across parser, help, capabilities, templates, and docs
- Scope: book access, passphrase sources, request files, report output, PDF export, pagination,
  posting lookup, date filters, and `execute-plan` result detail
- `ProtocolBookAccessOptions` owns the protected-book file, key, passphrase, backup, restore,
  generated-key, and rollback option spellings; `ProtocolOptions` consumes its passphrase-source
  vocabulary rather than duplicating it
- `ProtocolOptions.Attestation` owns credential-source, founder, receipt, and review option
  spellings for protected-book attestation commands; callers must use these canonical names rather
  than assembling private-key or passphrase options ad hoc

## `ProtocolOptions.Request`, `ProtocolOptions.DateRange`, `ProtocolOptions.ReportQuery`, `ProtocolOptions.BookDefinition`, `ProtocolOptions.Presentation`, And `ProtocolOptions.Discovery`

These nested owners partition public CLI option spellings by the contract they shape.

- `Request`: request document and primary-resource selectors
- `DateRange`: effective-date, reporting-period, fiscal-year, and as-of selectors
- `ReportQuery`: comparative, coverage, pagination, and cursor selectors
- `BookDefinition`: open-book identity, doctrine, accounting-basis, and initialization selectors
- `Presentation`: output-mode and PDF artifact selectors
- `Discovery`: discovery filtering and result-detail selectors

## `ProtocolInteractionLimits`

`ProtocolInteractionLimits` owns the public interaction and request-shape limits that define what
the CLI and machine contracts accept.

```java
public final class ProtocolInteractionLimits
```

- Purpose: keep public paging limits, request-size limits, passphrase byte limits, and ledger-plan
  step limits under one protocol-owned contract instead of duplicating them across CLI parsing,
  discovery payloads, and executor-boundary validation
- Current contract: `BOOK_PASSPHRASE_MAX_UTF8_BYTES = 4096`,
  `REQUEST_PAYLOAD_MAX_BYTES = 1048576`, `PAGE_LIMIT_MIN = 1`, `DEFAULT_PAGE_LIMIT = 50`,
  `PAGE_LIMIT_MAX = 200`, `LEDGER_PLAN_STEP_MAX = 100`

## `DiscoveryDetail`

`DiscoveryDetail` is the typed wire vocabulary for discovery-surface detail selection.

```java
public enum DiscoveryDetail implements WireValue
```

- Members: `MINIMAL`, `COMPACT`, `FULL`
- Purpose: keep discovery detail selection consistent across the parser, discovery contract,
  help payloads, capabilities payloads, and public documentation
- Semantics: `MINIMAL` is the front-door machine index, `COMPACT` is the stable descriptor layer,
  and `FULL` adds embedded templates, schemas, vocabularies, and doctrine bodies
- Boundary: accepted on JSON discovery surfaces only; text discovery surfaces reject it instead
  of silently ignoring it

## `DiscoveryFocus`

`DiscoveryFocus` is the typed wire vocabulary for concern-scoped JSON discovery retrieval.

```java
public enum DiscoveryFocus implements WireValue
```

- Members: `OVERVIEW`, `COMMANDS`, `STORAGE`, `REQUEST_INPUT`, `CURRENCY_MODEL`,
  `BOOKKEEPING_KERNEL`, `RESPONSE_CONTRACT`
- Purpose: let machine callers retrieve one discovery concern without overfetching unrelated
  contract bodies
- Boundary: accepted on JSON discovery surfaces only; text discovery surfaces reject it instead
  of silently ignoring it

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
- Related contract: `contract-schema-keys.json` owns only the discovery/build schema keys that
  need stable public names, while `operation-id-contract.json` remains the sole owner of the full
  public operation catalog consumed by typed loaders and shell-side verifiers

## `ProtocolArtifactOutput`

`ProtocolArtifactOutput` is one contract-owned export descriptor for a non-stdout artifact.

```java
public record ProtocolArtifactOutput(String format, String option, String description)
```

- Purpose: advertise supported artifact outputs without ad hoc CLI strings
- Current scope: report PDF export plus the generated/replacement book-key-file, restored
  book-file, backup-file, backup-key-file, and rollback-book artifact families published through
  the uniform `artifacts[]` response home

## `PublicCliBundleTarget`

`PublicCliBundleTarget` is the canonical host-classifier vocabulary for public CLI bundles.

```java
public enum PublicCliBundleTarget implements WireValue
```

- Purpose: keep bundle-target publication tied to one typed classifier vocabulary instead of open
  strings that can drift away from the real release matrix

## `PublicBundlePublicationStatus`

`PublicBundlePublicationStatus` is the canonical publication-state vocabulary for public CLI bundle
targets.

```java
public enum PublicBundlePublicationStatus
```

- Purpose: keep published versus not-published bundle status typed through the protocol surface
  instead of copying string literals into docs, capabilities, build logic, and shell verifiers
- Validation: `fromWireValue` rejects unsupported publication states before any bundle-target
  registry is accepted

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
  to one typed target owner shared by build logic, bundle metadata, operator verification, and
  public bundle publication
- Validation: requires one layout entry for every `PublicCliBundleTarget`, and derives the
  supported-versus-unsupported public bundle lists from the per-target publication facts instead of
  a second sidecar resource

## `BundleLayoutContract.BundleTarget`

`BundleLayoutContract.BundleTarget` is one canonical bundle layout descriptor.

```java
record BundleTarget(
    String operatingSystemId,
    String architectureId,
    String archiveFormat,
    String launcherPath,
    String launcherCommand,
    String sqliteLibraryFileName,
    String compatibilityLabel)
```

- Purpose: expose the per-target self-contained archive facts that the bundle manifest and
  acceptance scripts must report without reauthoring platform switch statements

## `BundleLayoutContract.PublicBundlePublication`

`BundleLayoutContract.PublicBundlePublication` is the nested publication descriptor carried by each
bundle target.

```java
record PublicBundlePublication(
    PublicBundlePublicationStatus status,
    Optional<String> runnerLabel)
```

- Purpose: make public-bundle publication status and the proving runner metadata part of the same
  canonical bundle-target fact instead of maintaining a parallel publication registry
- Validation: published targets must declare the proving runner label; non-published targets must
  omit it

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
  `bundle-sidecar-consistency` or `source-checkout-sidecar-consistency`, so machine consumers can distinguish
  public bundle identity from checkout-local build identity
- `SqliteRuntimeStatus`: distinguishes `ready`, `unavailable`, `failed`, and `incompatible`, so
  discovery can separate missing-runtime failures from late probe failures after one concrete
  library target was already resolved
- `SqliteRuntimeStateValidator`: canonical owner for the valid runtime-state matrix shared by the
  public descriptor layer and the SQLite runtime probe surface
- Validation surface: `wireValue()`, `wireValues()`, `fromWireValue(...)`, and typed discovery
  records such as `EnvironmentRuntimeDescriptor`, `EnvironmentPublicationDescriptor`,
  `EnvironmentStorageDescriptor`, and `EnvironmentSqliteDescriptor`
- Operational reach: build logic, bundle metadata/launchers, Docker staging, and shell verifiers
  consume the same protocol-owned runtime-surface contract instead of carrying private copies of
  those wire values

## `BookModelFacts`, `CurrencyFacts`, `BookkeepingKernelFacts`, `ReportCapabilityFacts`, `PreflightFacts`, And `PlanExecutionFacts`

These typed records publish FinGrind's hard public model facts.

```java
public record BookModelFacts(...)
public record CurrencyFacts(...)
public record BookkeepingKernelFacts(...)
public record ReportCapabilityFacts(...)
public record PreflightFacts(...)
public record PlanExecutionFacts(...)
```

- Purpose: keep book-model scope, currency support, advisory preflight behavior, and ledger-plan
  execution semantics structured before help or capabilities render them
- Policy: these are not CLI-local prose constants
- Related types: `BookBoundaryFact`, `BookEntityScopeFact`, `BookFilesystemFact`,
  `BookCredentialFact`, `BookInitializationFact`, `BookAccountRegistryFact`,
  and `BookCurrencyScopeFact` are the semantic text wrappers carried by `BookModelFacts`
- `BookkeepingKernelFacts`: publishes the live executable kernel scope, the built-in statement
  inventory, the per-report capability inventory, and the concise machine description of the
  shipped bookkeeping kernel
- `ReportCapabilityFacts`: publishes the statement id, comparative support flag, and contract
  description for each built-in report

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

## `ForeignExchangeDetails`, `ForeignExchangeTreatmentKind`, And `QuotedExchangeRate`

These contract-owned FX types keep foreign-currency event facts separate from the book's
functional-currency posting amounts.

```java
public record ForeignExchangeDetails(...)
public enum ForeignExchangeTreatmentKind
public record QuotedExchangeRate(...)
```

- Purpose: publish one owned transaction-currency amount, one translated functional amount, one
  exact quoted rate, and one closed treatment vocabulary without reopening mixed-currency journal
  lines
- `ForeignExchangeDetails`: validates distinct transaction and functional currencies, requires one
  positive amount on both sides, and proves that the published functional amount equals the quoted
  rate translation exactly
- `ForeignExchangeTreatmentKind`: closes the public treatment vocabulary to
  `SPOT_TRANSACTION` and `UNREALIZED_REMEASUREMENT`
- `QuotedExchangeRate`: owns one directional exact quote with quote date and quote source, and
  performs half-up translation from transaction currency into functional currency

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

## `ProtocolOpenBookFields`, `ProtocolDeclareAccountFields`, `ProtocolTaxRegistrationFields`, `ProtocolPostEntryFields`, And `ProtocolLedgerPlanFields`

These protocol-owned constant classes keep public JSON field names canonical for their
surface-specific JSON scopes.

```java
public final class ProtocolOpenBookFields
public final class ProtocolDeclareAccountFields
public final class ProtocolTaxRegistrationFields
public final class ProtocolPostEntryFields
public final class ProtocolLedgerPlanFields
```

- Purpose: prevent request parsing, templates, capabilities, and docs from carrying divergent
  field-name registries
- `ProtocolOpenBookFields`: owns the nested `entityName`, `functionalCurrency`, `fiscalYearStart`,
  and `bookStartEffectiveDate` field names for explicit book initialization
- `ProtocolDeclareAccountFields`: owns the top-level account-declaration field names, including
  the conditional nested `unitOfMeasure` object used only by inventory-account declarations
- `ProtocolDeclareAccountFields.UnitOfMeasure`: owns the nested `token` and `quantityScale` field
  names for that inventory unit-of-measure object
- `ProtocolTaxRegistrationFields` and nested `.TaxCode` own the canonical top-level and declared
  tax-code field names for `declare-tax-registration`
- `ProtocolPostEntryFields.SettlementAdjunct`, `.ForeignExchange`, `.QuotedRate`,
  `.InventoryRelief`, `.Tax`, `.JournalLine`, `.OpeningBalance`, `.Provenance`, and `.Reversal`
  group the canonical posting-request field families by JSON object scope; foreign-currency
  business events carry one nested `foreignExchange` object with one nested `quotedRate` object,
  trading inventory requests use the explicit top-level `inventoryAccountCode`,
  `writeDownLossAccountCode`, `shrinkageLossAccountCode`, `countGainAccountCode`, `quantity`, and
  `unitCost` facts as their selected entry kind requires, trading sale requests may carry one
  nested `inventoryRelief` object, inventory opening balances carry nested `quantity`, and
  journal-line `amount` remains one nested exact-money object keyed by `ProtocolMoneyFields`
- `ProtocolLedgerPlanFields.Plan`, `.Step`, `.Query`, and `.Assertion` group the canonical
  ledger-plan field families by JSON object scope; assertion `netAmount` is the same nested
  exact-money object shape

## `ProtocolAttestationRegistryRequestFields`

`ProtocolAttestationRegistryRequestFields` owns the strict JSON vocabulary for `enroll-key`,
`rollover-key`, `revoke-key`, and `alter-policy`.

```java
public final class ProtocolAttestationRegistryRequestFields
```

- Credential operations use `principalId`, canonical base64url `credentialSpki`,
  `credentialPurpose`, and, for rollover, `predecessorCredentialSpki`; revocation additionally
  permits an optional `reason`.
- Policy changes use optional `policyRules`, `capabilityGrants`, and
  `systemWorkflowPolicies` arrays, but at least one must be nonempty. Nested fields own the
  capability, quorum, grant state, workflow identity, workflow kind, account-code, and active
  values shared by parser, documentation, and signed mutation projection. Each array may carry a
  given capability, principal-capability pair, or workflow ID only once, respectively.

## Attestation Credential Custody Commands

`generate-attestation-key-file` owns the off-book private-credential creation boundary. It takes
an absent `--new-attestation-key-file` target plus a separate
`--attestation-passphrase-file`, publishes the encrypted credential as an
`attestation-key-file` artifact, and returns only `credentialSpki` and its derived `keyId`.
`inspect-attestation-key-file` takes only `--attestation-key-file` and returns those same public
values without decrypting the private key or reading a passphrase. Both commands are ordinary
JSON-envelope surfaces in the machine contract; neither accepts a request document.

## `ProtocolBookRequestFieldSets`, `ProtocolPostingRequestFieldSets`, `ProtocolPostingNestedFieldSets`, And `ProtocolLedgerPlanRequestFieldSets`

These protocol field-set owners are the canonical accepted-field registries across public request
families.

```java
public final class ProtocolBookRequestFieldSets
public final class ProtocolPostingRequestFieldSets
public final class ProtocolPostingNestedFieldSets
public final class ProtocolLedgerPlanRequestFieldSets
```

- Purpose: keep JSON schema descriptors, CLI request validation, and docs aligned on one
  allowed-field registry instead of hand-maintained accepted-key sets in multiple surfaces
- Scope: `ProtocolBookRequestFieldSets` owns explicit field inventories for `open-book` and
  `declare-account` plus `declare-tax-registration`; `ProtocolPostingRequestFieldSets` owns
  posting top-level request families; `ProtocolPostingNestedFieldSets` owns nested posting
  objects, including journal lines, evidence, tax selectors, and foreign-exchange fact bundles;
  `ProtocolLedgerPlanRequestFieldSets` owns ledger-plan top-level, step, query, and assertion
  objects

## `ProtocolBusinessEventFields`, `ProtocolBusinessEventFields.Core`, `ProtocolBusinessEventFields.AccrualCutoff`, `ProtocolBusinessEventFields.FixedAsset`, `ProtocolBusinessEventFields.Financing`, `ProtocolBusinessEventFields.RealizedForeignExchange`, `ProtocolBusinessEventFields.Inventory`, And `ProtocolBusinessEventFields.LatvianPayroll`

`ProtocolBusinessEventFields` is the canonical namespace for top-level posting facts. Its nested
owners group each field by the business event whose meaning it carries rather than by a generic
transport wrapper.

```java
public interface ProtocolBusinessEventFields
```

- `Core`: fields shared by more than one posting family, including event identity, dates,
  settlement, foreign exchange, tax, evidence, provenance, and reversal
- `AccrualCutoff`, `FixedAsset`, `Financing`, and `RealizedForeignExchange`: fields owned by their
  respective lifecycle contexts
- `Inventory`: quantity, unit-cost, account, and inventory-relief facts for the inventory context
- `LatvianPayroll`: the narrow payroll-run identity, withholding, account-role, and gross-wage
  facts used by the owned Latvian payroll context
- Current declare-account line: the accepted top-level field set includes `unitOfMeasure`, while
  the narrower declare-account schema and parser-owning validation layer enforce the
  inventory-only requiredness of that nested object
- Boundary: these types own accepted-field inventories only; scalar grammar, requiredness, and
  nested object semantics remain owned by the narrower protocol field classes and request-shape
  descriptors

## `ProtocolFixedAssetRequestFields`, `ProtocolFixedAssetRequestFields.DepreciationSchedule`, `ProtocolForeignExchangeRequestFields`, `ProtocolForeignExchangeRequestFields.ForeignExchange`, And `ProtocolForeignExchangeRequestFields.QuotedRate`

```java
public final class ProtocolFixedAssetRequestFields
public static final class ProtocolFixedAssetRequestFields.DepreciationSchedule
public final class ProtocolForeignExchangeRequestFields
public static final class ProtocolForeignExchangeRequestFields.ForeignExchange
public static final class ProtocolForeignExchangeRequestFields.QuotedRate
```

These owners keep lifecycle-specific request vocabulary independent from the generic posting envelope. `ProtocolFixedAssetRequestFields` names fixed-asset identifiers, cost, disposal proceeds, and the straight-line schedule fields. `ProtocolForeignExchangeRequestFields` owns the nested transaction and functional amounts, quote, date, and source fields used by foreign-currency obligation and settlement requests.

## `ProtocolFixedAssetPostingRequestFieldSets` And `ProtocolFinancingPostingRequestFieldSets`

```java
public final class ProtocolFixedAssetPostingRequestFieldSets
public final class ProtocolFinancingPostingRequestFieldSets
```

These context-owned sets publish the accepted request keys for fixed-asset and financing commands. Machine schemas, request templates, help, and CLI parsing consume the same inventories; entry-specific requiredness remains owned by the corresponding variant validators.

## `ProtocolPostingRequestTopics`

This protocol helper is the canonical command-topic selector for published posting-request lanes.

```java
public final class ProtocolPostingRequestTopics
```

- Purpose: keep discovery templates, CLI request parsing, and request-topic narrowing aligned on
  one published ownership table instead of duplicating command-to-entry-kind mappings
- Scope: identifies which posting commands accept the full published family, which require one
  exact `entryKind`, and which scaffold entry kind each request-template topic should emit
- Boundary: this type owns topic selection only; field inventories remain owned by the posting
  field registries, while business semantics remain owned by the entry validators and request
  surface facts

## `ProtocolRequestTemplateTopics`

This protocol helper is the canonical topic inventory for `print-request-template`.

```java
public final class ProtocolRequestTemplateTopics
```

- Purpose: keep request-template syntax, topic validation, and unsupported-topic diagnostics
  derived from one published ownership table instead of hand-maintained string lists
- Scope: identifies every operation that owns one raw request-template scaffold and publishes the
  stable wire-name inventory and command syntax used by discovery and CLI help, including
  `declare-tax-registration`
- Boundary: this type owns request-template topics only; posting entry-kind selection remains owned
  by `ProtocolPostingRequestTopics`, while scaffold document shapes remain owned by the contract
  template and request-shape builders

## `ProtocolEnvelopeCatalog`, `ProtocolDomainCatalog`, `ProtocolRuntimeCatalog`, `ProtocolDistributionCatalog`, And `ProtocolManagedSqliteCatalog`

These catalog owners split the top-level protocol registry into bounded public subcatalogs.

```java
public final class ProtocolEnvelopeCatalog
public final class ProtocolDomainCatalog
public final class ProtocolRuntimeCatalog
public final class ProtocolDistributionCatalog
public final class ProtocolManagedSqliteCatalog
```

- Purpose: keep envelope defaults, domain facts, runtime facts, distribution facts, and managed
  SQLite facts owned by explicit catalog roots instead of one swollen registry type
- Scope: `ProtocolCatalog` composes these catalogs into the public discovery surface without
  collapsing their ownership boundaries
- `ProtocolDomainCatalog` now publishes `RequestSurfaceFacts` as the canonical owner for per-entry
  posting request semantics, co-located source-document-type policy, account-classification
  reachability facts, and the temporal-scope lexicon reused by discovery, validation, help, and
  repair hints
- `RequestSurfaceFacts` groups `BookkeepingEntryKindFacts`, `ReachabilityCellFacts`,
  `EvidenceRequirementFacts`, `TemporalScopeFacts`, and `CommandTemporalScopeFacts` so one edit
  can update request-shape discovery and runtime validation together
- `SourceDocumentTypePolicyMode` distinguishes entry kinds that publish one closed
  `sourceDocumentType` enum from entry kinds that leave that token caller-authored under the shared
  token grammar
- `ReachabilityCellFacts` expose declarable, opening, operational-journal, and reversal
  reachability per declared-account classification cell, and those facts derive from the live
  current-kernel account-classification reachability owner instead of one hand-maintained discovery
  copy
- `TemporalScopeArchetype` keeps ranged-filter, bounded-period, through-date, fiscal-year-label,
  and as-of-date semantics explicit so option names, labels, help-level boundary guidance, and
  empty-state wording derive from one owned vocabulary instead of parallel literals
- Boundary: each catalog owns one coherent slice of public protocol metadata and leaves field-level
  structure to the narrower protocol field owners above

## `Machine Contract And Descriptor Reference`

Machine-contract assembly, discovery descriptor families, request and response shapes, workflow and
template descriptors, and deterministic contract failures are documented in
[DOC_02_MachineContractAndDescriptors.md](./DOC_02_MachineContractAndDescriptors.md).

## `BookFormatContract`

`BookFormatContract` is the canonical public owner of the FinGrind SQLite book identity and format
version constants.

```java
public final class BookFormatContract
```

- Purpose: keep the stable `application_id` and supported on-disk format version in one contract
  owner shared by inspections, fixtures, and storage adapters
- Current contract: `APPLICATION_ID = 1179079236` and `FORMAT_VERSION = 51`

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
