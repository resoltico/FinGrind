---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_DISCOVERY
updated: "2026-07-26"
route:
  keywords: [fingrind, machine-contract, discovery, request-shapes, response-shapes, templates, workflow, contract-errors, source-artifact-identity-duplicated, source-artifact-identity-changed, pair-targets-conflict, pair-target-leaf-portability-required, target-owner-only-required, protected-book-pair-publication-evidence-blocked]
  questions: ["where is MachineContract documented", "where are request and response descriptor types documented", "where are discovery templates and workflow descriptors documented", "which machine descriptor owns protected-book pair target failures", "where does capabilities list protected-book path failure values"]
---

# Machine Contract And Descriptor Reference

This file documents the exported `contract` owners that assemble machine discovery, define typed
request and response descriptors, publish templates and workflow scaffolds, and expose deterministic
contract failures.

## `MachineContract`

`MachineContract` is the public discovery assembler for `help`, `version`, `capabilities`,
ordinary request templates, and plan templates.

```java
public final class MachineContract
```

- Purpose: render discovery payloads from typed contract state instead of CLI-owned literals
- Versioning: `protocolVersion()` is the single canonical owner for the public discovery and
  machine-envelope contract line, and discovery JSON payloads carry that field directly so
  callers can detect hard public breaks without inferring from the application version
- Inputs: `ProtocolCatalog`, `ContractDiscovery`, the top-level discovery descriptor types,
  `ContractRequestShapes`, the direct response descriptor types rooted at `ResponseDescriptorType`,
  and `ContractTemplates`
- Help behavior: `help()` now owns the canonical typed workflow and note inventory, while the CLI
  transport deliberately narrows the root help surface to one concise overview and uses
  `capabilities` as the deep doctrine/runtime contract
- Command-help behavior: `help(OperationId)` and the CLI `<command> --help` alias both scope the
  rendered discovery payload to one selected operation, while the CLI help renderer rewrites
  canonical `fingrind ...` examples and repair hints to the active launcher surface such as
  `./bin/fingrind` or `.\\bin\\fingrind.ps1`; structured-input commands additionally inline the
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

## `ProtocolCapabilityBaselineSyncMain`

`ProtocolCapabilityBaselineSyncMain` is the explicit build-tool entrypoint that synchronizes the
release-smoke capability baseline from the typed `MachineContract` and `ProtocolCatalog` owners.
It accepts exactly one destination directory and writes one canonical index plus one deterministic
JSON fragment per published command owner; it is not a user-facing FinGrind CLI command or a
second capability vocabulary.

## `MachineContractAttestationTemplates`, `ContractAttestationRegistryTemplates`, And `ContractAttestationReviewTemplates`

Attestation administration and review scaffolds are a separate public contract family, rather
than additional responsibilities on the general discovery assembler.

```java
public final class MachineContractAttestationTemplates
```

- `registryTemplate(OperationId)` emits the exact typed scaffold for `enroll-key`,
  `rollover-key`, `revoke-key`, or `alter-policy`; unsupported operation IDs refuse rather than
  returning an ambiguous empty template.
- `reviewFileTemplate()` emits the complete non-persisted compromise-review declaration accepted
  by `verify-book` and `attestation-review`, with affected orders encoded as strings.
- This is the sole typed owner for those attestation templates; Java consumers use it directly and
  no generic compatibility facade is exposed.

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

## `ContractDiscovery`, `ContractRequestShapes`, `ResponseDescriptorType`, And `ContractTemplates`

These public contract owners define the typed descriptor families used by `MachineContract`.

```java
public final class ContractDiscovery
public final class ContractRequestShapes
public sealed interface ResponseDescriptorType
public enum FailureCategory
public record ResponseModelDescriptor(...)
public record PlanExecutionDescriptor(...)
public final class AttestationDiagnosticDescriptors
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
  machine-readable `capabilities` payload publishes per-command identity, canonical display label,
  aliases, options, execution mode, output modes, artifact outputs, and summaries without falling
  back to one lossy global stdout-mode list
- `CommandDescriptor.displayLabel` is required to equal the corresponding `ProtocolCatalog`
  operation label, so the machine contract cannot publish a report title or command heading that
  drifts from the canonical owner. Compact command surfaces project that same field.
- `CommandDescriptor` keeps command identity, display label, execution mode, output modes, and
  artifact outputs typed through `OperationId`, `String`, `ExecutionMode`, `OutputMode`, and
  `ArtifactOutputDescriptor` instead of flattening closed protocol vocabularies into strings
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
- `ResponseDescriptorType` is the sealed public inventory root for response-model, rejection,
  audit, preflight, currency, and plan-execution descriptors; its `descriptorTypes()` method
  enumerates the direct descriptor records without a forwarding namespace
- `ContractResponseCatalog` owns the complete published error and rejection descriptor union and
  requires every failure code to declare exactly one `FailureCategory`; lookup has
  no fallback category
- `FailureCategory` distinguishes `structural-invalid`, `domain-semantic`,
  `precondition`, `unsupported-selection`, and `internal` failures for machine consumers
- `BookModelDescriptor`, `FieldDescriptor`, `ErrorDescriptor`, `ResponseModelDescriptor`,
  `PlanExecutionDescriptor`, `PlanAttestationOutcomeDescriptor`, `RejectionDescriptor`,
  `AuditDescriptor`, `AccountRegistryDescriptor`, `InitializationRequirement`,
  `ReversalDescriptor`, `PreflightDescriptor`, `CommitGuarantee`, `CurrencyDescriptor`, and
  `BookkeepingKernelDescriptor` are direct typed response-contract types in the exported runtime
  package
- `PlanExecutionDescriptor.attestationOutcomes` is the complete closed successful
  `execute-plan` table, not a bare disposition vocabulary. Each
  `PlanAttestationOutcomeDescriptor` binds one disposition to exact `attestationCommit`
  (`required` or `must-be-null`) and `attestationCredentials` (`required` or `prohibited`) modes,
  so automation can derive response and request requirements without parsing prose.
- `AttestationDiagnosticDescriptors.DiagnosticDescriptor`,
  `.AdmissionDiagnosticsDescriptor`, and `.VerificationDiagnosticsDescriptor` are sibling
  response descriptor records permitted by `ResponseDescriptorType`; they keep
  the exact published attestation `{ code, message, hint }` projections typed rather than leaving
  them as CLI-owned maps
- `ContractTemplates`: canonical request template descriptors
- `ContractAttestationRegistryTemplates`: canonical credential-registry and authority-policy
  request descriptors for `enroll-key`, `rollover-key`, `revoke-key`, and `alter-policy`
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
- `ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor`,
  `.RolloverKeyTemplateDescriptor`, `.RevokeKeyTemplateDescriptor`, and
  `.AlterPolicyTemplateDescriptor` publish the exact lifecycle scaffold under full-detail command
  help; their nested policy-rule, capability-grant, and workflow-policy descriptors make every
  accepted registry field visible before request submission
- `InventoryReliefTemplateDescriptor` is the top-level typed trading-sale relief descriptor
  reused by posting request templates instead of remaining buried as an anonymous object shape
- `ForeignExchangeTemplateDescriptor` and `QuotedExchangeRateTemplateDescriptor` are the
  top-level typed FX request template descriptors reused by posting request templates
- Template descriptors keep actor type, account type, entry side, normal balance, step kind,
  assertion kind, balance side, and exact money typed at the public boundary, and they reject
  structurally impossible ledger plan step or assertion combinations before any renderer publishes
  them

## `AttestationDiagnosticDescriptors`

`AttestationDiagnosticDescriptors` is the public typed discovery owner for every exact attestation
diagnostic exposed by the response model. Its `descriptorTypes()` method enumerates the three
response descriptor records owned by that namespace.

```java
public final class AttestationDiagnosticDescriptors
public enum AttestationDiagnosticDescriptors.AdmissionContext
public record AttestationDiagnosticDescriptors.DiagnosticDescriptor(...)
public record AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor(...)
public record AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor(...)
```

- `DiagnosticDescriptor` carries one stable `{ code, message, hint }` triplet.
- `AdmissionContext` is the closed wire vocabulary `ordinary-live-admission`,
  `registry-mutation`, and `backup-acknowledgement`.
- `AdmissionDiagnosticsDescriptor` groups only the rows actually emitted by one
  `AdmissionContext`; registry and backup-acknowledgement groups never advertise manifest or
  receipt failures from artifact-verification routes.
- `VerificationDiagnosticsDescriptor` groups historical diagnostic triplets by the emitting
  `OperationId` surface.
- `ResponseModelDescriptor.attestationAdmissionDiagnostics` and
  `.attestationVerificationDiagnostics` publish the two groups under
  `capabilities --output json --detail full` at
  `payload.fullContract.responseModel`. Machine consumers must use these discovery rows as the
  exact message and hint contract instead of copying diagnostics into their own code.

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

## `ContractErrors`, `ContractFailure`, `ContractFailureDetails`, `ContractFailurePaths`, `ContractDecision`, And `ContractFailureException`

These contract-owned types publish deterministic non-rejection failures and the accepted-or-rejected
decision seam used by low-level contract boundaries.

```java
public final class ContractErrors
public record ContractFailure(...)
public sealed interface ContractFailureDetails
public record ContractFailurePaths(Path path, List<Path> relatedPaths)
public sealed interface ContractDecision<T>
public final class ContractFailureException extends IllegalStateException
```

- Purpose: distinguish malformed input and deterministic invocation failures from runtime failure
- Contract: `ContractErrors.Descriptor` owns stable error codes such as `invalid-request`,
  `invalid-page-cursor`, `attestation-credentials-not-allowed`,
  `protected-book-verification-failed`, `unsupported-book-format-version`, `stale-head`, `internal-defect`,
  `internal-error`, `managed-runtime-failure`, `storage-runtime-failure`,
  `artifact-publication-outcome-uncertain`, `artifact-publication-durability-uncertain`,
  `protected-book-pair-publication-uncertain`,
  `protected-book-pair-publication-evidence-blocked`,
  `open-book-preparation-artifacts-retained`, `pdf-export-failure`, and
  `interactive-prompt-unavailable`, plus the published process
  `exitCode` for each deterministic machine error
- `BookMaintenanceRejection` owns `pair-targets-conflict`, the `rejected`, `precondition`,
  exit-`2` response for final targets that establish one filesystem identity, and
  `artifact-path-invalid`, the `rejected`, `precondition`, exit-`6` response for a typed
  maintenance path failure. `PairTargetsConflict` details retain normalized absolute submitted
  `bookTarget` and `generatedSecretTarget` spellings rather than claiming canonical physical
  paths. `ArtifactPathInvalid` details retain `artifactRole`, `artifactPath`, and `pathFailure`.
  `payload.fullContract.responseModel.rejections[code="artifact-path-invalid"].detailFields[name="pathFailure"]`
  dynamically publishes the complete closed `BookMaintenancePathFailure` vocabulary, while
  `rejections[code="artifact-verification-failed"].detailFields[name="verificationFailure"]`
  does the same for `BookMaintenanceVerificationFailure`. The shared
  `responseModel.rejectionFields[].details` prose summarizes both vocabularies, but an agent must
  use the structured per-code descriptors rather than infer a newly added typed failure from prose
  or examples.
  `source-artifact-identity-duplicated` means the later role in the complete selected source set
  resolves to the same physical object as an earlier source, including through a hard link.
  `source-artifact-identity-changed` means post-lock revalidation found that a selected source no
  longer has the physical identity FinGrind locked; restore the trustworthy intended source, keep
  every source stable, and rerun the complete maintenance command.
  `pair-target-leaf-portability-required` means two distinct absent leaves resolve to one physical
  parent but one violates the portable lowercase-ASCII leaf grammar. Lifecycle source validation
  and final-parent admission precede initial pair-target identity, so an eligible missing private
  parent may remain. That initial admission creates no final target, retained lease-control file,
  stage, capability witness, reservation, claim, or pair-evidence artifact. Existing final
  targets establish physical identity through `Files.isSameFile`; the exact absent same-parent
  leaf equality case is `pair-targets-conflict`.
- `BookMaintenanceRejection` separately owns the `maintenance-recovery-pending` `rejected`,
  `precondition`, exit-`7` maintenance-state conflict. Before any maintenance stage, probe,
  reservation, or final mutation, current evidence binds the full operation: exact source,
  book target, generated-secret target, secret identity, and owner-record-constrained derived
  stages. Its non-null `details` are `recoveryOperation`, `bookTarget`, and
  `generatedSecretTarget`: a canonical operation wire value and canonical absolute target paths.
  Text labels are `Recovery operation`, `Book target`, and `Generated secret target`. Restart only
  the named operation with its complete original source, target, and secret inputs. Its top-level
  `argument` is `null`; `path` is the book target and `relatedPaths` contains the
  generated-secret target. The details do not reconstruct source, backup ID, credentials, or
  secret material, and no evidence is manually renamed, overwritten, deleted, recreated, or
  otherwise cleaned. Malformed, legacy, incomplete, or inconsistent evidence fails closed as
  exit-`4` `protected-book-pair-publication-evidence-blocked`, never as a partial-workflow retry.
- `invalid-request` advertises structured `detailFields` when the malformed request reaches
  aggregated journal grammar validation, with `details.violations[]` carrying the full ordered
  set of detected issues
- Every successful staged artifact is `artifacts[].{format,path,retainedStage}`. The retained
  stage is immutable evidence, never a cleanup handle. A non-success envelope whose
  `ContractFailure` has a retention fact carries the same top-level `retainedStage`.
- `artifact-publication-outcome-uncertain` is exit 4 and exposes
  `details.{candidateArtifact,retainedStage}`. The stage field is null only when no retained stage
  exists. It means the no-replace link threw without proving whether it created the candidate final
  name; callers preserve and inspect the candidate and any reported stage, then use a fresh
  destination rather than inferring or retrying the original name.
- `artifact-publication-durability-uncertain` is exit 4 and exposes one
  `details.publishedArtifact.{path,retainedStage}` object as well as top-level `retainedStage`.
  It means a no-clobber final link was created but the required directory durability barrier did
  not complete; callers preserve and inspect the final path and retained stage and do not retry
  that destination.
- `open-book-preparation-artifacts-retained` is exit 4 and reports a non-empty ordered
  `details.retainedArtifacts[]` list of `{role,path,retainedStage}` facts. Each reported artifact
  is immutable evidence from an opening attempt that did not complete; callers choose fresh paths,
  never delete, reuse, or repair the reported ones.
- `protected-book-pair-publication-uncertain` is the distinct exit-4 precondition when
  `backup-book`, `restore-book`, or `rekey-book` cannot establish a safe durable disposition for
  its operation-bound book-and-generated-secret pair. Its top-level `argument` is explicitly
  `null`; `path` is the canonical book target and `relatedPaths` contains the canonical
  generated-secret target. Its details retain `operation` and `pairPublication.bookTarget.{path,state}` plus
  `pairPublication.generatedSecretTarget.{path,state}`. Member `state` is exactly one of
  `not-attempted`, `outcome-uncertain`, `published-durability-unconfirmed`, or
  `published-durable`; the two paths are distinct canonical final targets. JSON always carries
  `pairPublication.recoveryRecordState`: it is `durably-retained` or
  `durability-unconfirmed` exactly when both member states are `not-attempted`, otherwise `null`.
  JSON also always carries nullable `pairPublication.pairPublicationRetention`; when non-null,
  its `bookPublication.{path,retainedStage}` and
  `generatedSecretPublication.{path,retainedStage}` paths bind exactly to the two final targets.
  `null` means no authoritative pair-stage fact was established, never that the evidence may be
  cleaned. Preserve FinGrind pair evidence and both named
  final paths. When FinGrind has verified the retained operation-bound pair, rerun the exact same
  operation with its complete original source, target, and secret inputs. FinGrind resumes only
  derived stages named by that owner record. Never rename, overwrite, delete, recreate, or
  manually clean pair evidence or either final member; do not start a fresh pair. When
  `recoveryRecordState` is non-null, preserve FinGrind's recovery material too.
  A recovered rekey verifies the generated-key pair before attempting any
  prior-key access.
- `protected-book-pair-publication-evidence-blocked` is the distinct exit-4 precondition where
  retained evidence exists but cannot establish a safe final-member publication state. Its details
  are `pairPublication.bookTarget.{path,state}` and
  `pairPublication.generatedSecretTarget.{path,state}`, both with `state: "unestablished"`,
  `pairPublication.recoveryRecordState: null`, and always-present nullable
  `pairPublication.pairPublicationRetention`. A null retention field means no authoritative
  pair-stage fact is safe to report; it does not establish a recoverable original operation or
  permit cleanup. Preserve every reported path and investigate independently; do not rerun, infer,
  or manually repair the pair.
- `attestation-credentials-not-allowed` is the distinct exit-`1` structural-invalid error for a
  complete credential selection paired with a decoded query-only or assertion-only ledger plan;
  it is not a partial-argument parse error and occurs before any credential is opened
- `unsupported-book-format-version` is the distinct exit-`6` precondition for an opened
  FinGrind book whose declared format is not this binary's exact supported version; its typed
  detail contract publishes `detectedBookFormatVersion` and `supportedBookFormatVersion`
- `ContractFailure` carries the stable descriptor plus the caller-facing message, optional hint,
  optional argument name, optional `ContractFailureDetails`, optional immutable
  `ArtifactPublicationRetention`, and optional typed filesystem locations without routing expected
  failures through exceptions
- `ContractFailurePaths` canonically holds one primary filesystem `path` and distinct companion
  `relatedPaths`; public machine renderers publish those locations as data while human renderers
  apply their path-redaction policy
- `ContractDecision` carries either the accepted typed payload or one deterministic
  `ContractFailure`, letting internal seams return structured failures directly
- `ContractFailureException` is the imperative bridge for seams that still must throw while
  preserving an exact deterministic `ContractFailure` for higher layers to map back into the
  public machine contract

## `OpenBookFailureDetails`, `OpenBookPreparationArtifactsRetained`, `RetainedOpenBookPreparationArtifact`, `OpenBookPreparationArtifactRole`, And `OpenBookCompletionUncertain`

These immutable facts describe the two narrow failure states that can follow successful genesis
preparation but precede proof that a new protected book was durably initialized.

```java
public final class OpenBookFailureDetails
public record OpenBookPreparationArtifactsRetained(...)
public record RetainedOpenBookPreparationArtifact(...)
public enum OpenBookPreparationArtifactRole
public record OpenBookCompletionUncertain(...)
```

- `OpenBookPreparationArtifactsRetained` is the non-empty, distinct-path evidence set for an
  opening attempt whose preparatory artifact publication did not complete.
- `RetainedOpenBookPreparationArtifact` records a canonical absolute path, its stable role, and
  optional retained-stage evidence. `OpenBookFailureDetails.retainedArtifact(...)` is the
  normalization seam for producing this public fact.
- `OpenBookPreparationArtifactRole` is the closed vocabulary: `attestation-founder-key`,
  `attestation-founder-key-stage`, `book-file`, and `book-sidecar`.
- `OpenBookCompletionUncertain` retains the initialized book identity, exact reported genesis
  trust root and commit, distinct founder-key publication facts, and a non-empty, distinct
  book-artifact set that includes the canonical `book-file` path. Its trust-root order and head
  must exactly match its reported commitment.
- These states preserve evidence but do not authorize manual repair, reuse, or deletion of any
  reported artifact. Select fresh paths after investigation as directed by the returned failure.

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
