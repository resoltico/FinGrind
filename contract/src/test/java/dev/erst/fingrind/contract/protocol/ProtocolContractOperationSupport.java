package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicyMode;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
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
                "account-role-conflict",
                "account-state-violations",
                "administration-book-not-initialized",
                "assertion-failed",
                "book-already-initialized",
                "book-contains-schema",
                "build-logic",
                "class-complete",
                "class-start",
                "cli-request",
                "desktop-linux",
                "devcontainer-changes",
                "docker-buildx",
                "docker-smoke",
                "duplicate-idempotency-key",
                "expected-invalid",
                "export-ignore",
                "fuzz-all",
                "fuzz-cli-request",
                "fuzz-ledger-plan-request",
                "fuzz-posting-workflow",
                "fuzz-sqlite-book-roundtrip",
                "inactive-account",
                "invalid-request",
                "json-envelope",
                "ledger-plan-request",
                "list-findings",
                "owner-only-acl",
                "posting-not-found",
                "posting-book-not-initialized",
                "query-book-not-initialized",
                "posting-workflow",
                "promote-seed",
                "replace-before-commit-effective-date",
                "regression-input",
                "semver-major",
                "raw-json",
                "replay-clean",
                "reversal-already-exists",
                "reversal-does-not-negate-target",
                "reversal-target-not-found",
                "runtime-failure",
                "seed-audit",
                "sqlite-book-roundtrip",
                "sqlite-jdbc",
                "test-complete",
                "test-progress",
                "timeout-minutes",
                "unknown-account",
                "unknown-command",
                "macos-15",
                "macos-latest",
                "ubuntu-latest",
                "unexpected-failure",
                "windows-bundle-smoke",
                "windows-2022",
                "windows-latest",
                "report-pdf"));
    ids.addAll(BookInspection.Status.wireValues());
    ids.addAll(BookMigrationPolicyMode.wireValues());
    ids.addAll(
        ContractErrors.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.ErrorDescriptor::code)
            .toList());
    ids.addAll(
        BookAdministrationRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        BookQueryRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        BookMaintenanceRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(
        PostingRejection.descriptors().stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    ids.addAll(LedgerAssertionKind.wireValues());
    ids.addAll(LedgerBoundaryPhase.wireValues());
    ids.addAll(LedgerJournalKind.wireValues());
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
    ids.addAll(ProtocolDiagnosticCode.wireValues());
    ids.addAll(OperationCategory.wireValues());
    ids.addAll(PublicCliBundleTarget.wireValues());
    ids.addAll(PlanTransactionMode.wireValues());
    ids.addAll(PlanFailurePolicy.wireValues());
    ids.addAll(PlanResultDetail.wireValues());
    return Set.copyOf(ids);
  }
}
