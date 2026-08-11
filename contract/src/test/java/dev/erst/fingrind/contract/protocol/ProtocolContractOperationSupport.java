package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import dev.erst.fingrind.contract.discovery.PlanTemplateTopic;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ErrorDescriptor;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationCommitMode;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationCredentialMode;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationReceiptFinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Operation-id and hyphenated public-vocabulary helpers for split contract-lint tests. */
class ProtocolContractOperationSupport extends ProtocolContractRepositorySupport {
  protected static final Pattern FINGRIND_COMMAND_PATTERN =
      Pattern.compile("\\bfingrind\\s+([a-z][a-z0-9-]*)");
  protected static final Pattern BACKTICKED_HYPHEN_ID_PATTERN =
      Pattern.compile("`([a-z][a-z0-9]*(?:-[a-z0-9]+)+)`");
  protected static final Set<String> NON_OPERATION_BACKTICK_IDS = nonOperationBacktickIds();

  protected final Set<String> registeredOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  protected final Set<String> registeredHyphenatedOperationIds() {
    return ProtocolCatalog.operations().stream()
        .map(operation -> operation.id().wireName())
        .filter(operationId -> operationId.contains("-"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  protected final boolean containsToken(String text, String token) {
    int searchFrom = 0;
    while (searchFrom <= text.length() - token.length()) {
      int index = text.indexOf(token, searchFrom);
      if (index < 0) {
        return false;
      }
      boolean hasLeadingTokenChar = index > 0 && isOperationTokenChar(text.charAt(index - 1));
      int trailingIndex = index + token.length();
      boolean hasTrailingTokenChar =
          trailingIndex < text.length() && isOperationTokenChar(text.charAt(trailingIndex));
      if (!hasLeadingTokenChar && !hasTrailingTokenChar) {
        return true;
      }
      searchFrom = index + 1;
    }
    return false;
  }

  protected final Stream<String> stringLiterals(String source) {
    List<String> literals = new ArrayList<>();
    StringBuilder literal = new StringBuilder();
    boolean insideLiteral = false;
    boolean escaping = false;
    for (int index = 0; index < source.length(); index++) {
      char character = source.charAt(index);
      if (!insideLiteral) {
        if (character == '"') {
          literal.setLength(0);
          insideLiteral = true;
        }
        continue;
      }
      if (escaping) {
        literal.append(character);
        escaping = false;
        continue;
      }
      if (character == '\\') {
        literal.append(character);
        escaping = true;
        continue;
      }
      if (character == '"') {
        literals.add(literal.toString());
        insideLiteral = false;
        continue;
      }
      literal.append(character);
    }
    return literals.stream();
  }

  private static boolean isOperationTokenChar(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9')
        || character == '-'
        || character == '_';
  }

  private static Set<String> nonOperationBacktickIds() {
    Set<String> ids =
        new HashSet<>(
            Set.of(
                "close-target-account-candidate-missing",
                "account-state-violations",
                "attestation-key-file",
                "administration-book-not-initialized",
                "assertion-failed",
                "book-already-initialized",
                "book-key-file",
                "book-contains-schema",
                "build-logic",
                "backup-file",
                "backup-key-file",
                "class-complete",
                "class-start",
                "cli-request",
                "desktop-linux",
                "devcontainer-changes",
                "docker-run",
                "docker-buildx",
                "docker-smoke",
                "idempotency-key-conflict",
                "expected-invalid",
                "export-ignore",
                "fuzz-all",
                "fuzz-cli-request",
                "fuzz-inventory-costing-math",
                "fuzz-ledger-plan-request",
                "fuzz-posting-workflow",
                "fuzz-sqlite-book-roundtrip",
                "inactive-account",
                "invalid-request",
                "inventory-costing-math",
                "inventory-balance-below-zero",
                "json-envelope",
                "ledger-plan-request",
                "list-findings",
                "owner-only-acl",
                "posting-not-found",
                "posting-book-not-initialized",
                "query-book-not-initialized",
                "posting-workflow",
                "pre-tag",
                "promote-seed",
                "published-bundle-smoke",
                "release-publication",
                "replace-before-commit",
                "replace-before-commit-effective-date",
                "regression-input",
                "semver-major",
                "raw-json",
                "replay-clean",
                "reversal-already-exists",
                "reversal-does-not-negate-target",
                "reversal-target-not-found",
                "runtime-failure",
                "cash-reserve",
                "cost-of-sales",
                "misc-revenue",
                "operating-expense",
                "owner-capital",
                "owner-draws",
                "result-holding",
                "sales-discount-allowance",
                "sales-revenue",
                "seed-audit",
                "service-revenue",
                "sqlite-book-roundtrip",
                "sqlite-jdbc",
                "tag-publication",
                "test-complete",
                "test-progress",
                "timeout-minutes",
                "unknown-account",
                "unknown-command",
                "macos-15",
                "macos-15-intel",
                "macos-latest",
                "runs-on",
                "ubuntu-latest",
                "unexpected-failure",
                "windows-bundle-smoke",
                "windows-2022",
                "windows-latest",
                "report-pdf"));
    ids.addAll(PlanTemplateTopic.wireNames());
    ids.addAll(
        java.util.Arrays.stream(AttestationCapability.values())
            .map(AttestationCapability::token)
            .toList());
    ids.addAll(
        java.util.Arrays.stream(AttestationCustodian.values())
            .map(AttestationCustodian::wireValue)
            .toList());
    ids.addAll(
        java.util.Arrays.stream(AttestationReceiptFinding.values())
            .map(AttestationReceiptFinding::code)
            .toList());
    ids.addAll(
        ProtocolCatalog.operations().stream()
            .flatMap(operation -> operation.artifactOutputs().stream())
            .map(ProtocolArtifactOutput::format)
            .toList());
    ids.addAll(
        java.util.Arrays.stream(OpenBookFailureDetails.OpenBookPreparationArtifactRole.values())
            .map(OpenBookFailureDetails.OpenBookPreparationArtifactRole::wireRole)
            .toList());
    ids.addAll(BookInspection.Status.wireValues());
    ids.addAll(BookMaintenanceArtifactRole.wireValues());
    ids.addAll(PublicationPathFailure.wireValues());
    ids.addAll(
        java.util.Arrays.stream(BackupAcknowledgementState.values())
            .map(BackupAcknowledgementState::wireValue)
            .toList());
    ids.addAll(ProtectedBookPairPublicationCompletion.wireValues());
    ids.addAll(ProtectedBookPairPublicationMemberState.wireValues());
    ids.addAll(CapabilityCatalog.entries().stream().map(CapabilityCatalogEntry::id).toList());
    ids.addAll(BookMigrationPolicyMode.wireValues());
    ids.addAll(ContractErrors.descriptors().stream().map(ErrorDescriptor::code).toList());
    ids.addAll(
        java.util.Arrays.stream(FailureCategory.values()).map(FailureCategory::wireValue).toList());
    ids.addAll(collectRejectionCodes(BookAdministrationRejection.descriptors()));
    ids.addAll(collectRejectionCodes(BookQueryRejection.descriptors()));
    ids.addAll(collectRejectionCodes(BookMaintenanceRejection.descriptors()));
    ids.addAll(collectRejectionCodes(PostingRejection.descriptors()));
    ids.addAll(
        java.util.Arrays.stream(AttestationVerificationFailure.values())
            .map(AttestationVerificationFailure::wireCode)
            .toList());
    ids.addAll(
        java.util.Arrays.stream(AdmissionContext.values())
            .map(AdmissionContext::wireValue)
            .toList());
    ids.addAll(LedgerAssertionKind.wireValues());
    ids.addAll(LedgerBoundaryCheckpoint.wireValues());
    ids.addAll(LedgerJournalKind.wireValues());
    ids.addAll(LedgerPlanAttestationCommitMode.wireValues());
    ids.addAll(LedgerPlanAttestationCredentialMode.wireValues());
    ids.addAll(LedgerPlanAttestationDisposition.wireValues());
    ids.addAll(RequestFieldPresence.wireValues());
    ids.addAll(RuntimeDistribution.wireValues());
    ids.addAll(PublicCliDistribution.wireValues());
    ids.addAll(StorageDriver.wireValues());
    ids.addAll(StorageEngine.wireValues());
    ids.addAll(BookProtectionMode.wireValues());
    ids.addAll(BookCipher.wireValues());
    ids.addAll(SqliteLibraryMode.wireValues());
    ids.addAll(SqliteRuntimeProvenance.wireValues());
    ids.addAll(SqliteRuntimeStatus.wireValues());
    ids.addAll(SqliteRuntimeTrustBasis.wireValues());
    ids.addAll(SqliteCompileOptionsVerificationStatus.wireValues());
    ids.addAll(WorkflowSurface.wireValues());
    ids.addAll(ProtocolEnvelopeStatus.wireValues());
    ids.addAll(OperationCategory.wireValues());
    ids.addAll(PublicCliBundleTarget.wireValues());
    ids.addAll(
        java.util.Arrays.stream(PublicBundlePublicationStatus.values())
            .map(PublicBundlePublicationStatus::wireValue)
            .toList());
    ids.addAll(PlanTransactionMode.wireValues());
    ids.addAll(PlanFailurePolicy.wireValues());
    ids.addAll(PlanResultDetail.wireValues());
    ids.addAll(AttestationOperationKind.wireTokens());
    RequestSurfaceContracts.current()
        .bookkeepingEntryKinds()
        .forEach(facts -> ids.addAll(facts.sourceDocumentTypes().acceptedValues()));
    return Set.copyOf(ids);
  }

  private static Set<String> collectRejectionCodes(List<RejectionDescriptor> descriptors) {
    Set<String> codes = new HashSet<>();
    descriptors.forEach(descriptor -> collectRejectionCodes(descriptor, codes));
    return Set.copyOf(codes);
  }

  private static void collectRejectionCodes(RejectionDescriptor descriptor, Set<String> codes) {
    codes.add(descriptor.code());
    descriptor.detailRejections().forEach(detail -> collectRejectionCodes(detail, codes));
  }
}
