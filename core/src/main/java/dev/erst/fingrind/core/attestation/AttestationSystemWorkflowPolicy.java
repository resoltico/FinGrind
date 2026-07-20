package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One immutable activation or retirement fact for an autonomous CLOSE_PERIOD workflow. */
record AttestationSystemWorkflowPolicy(
    BigInteger acceptedOrder,
    UUID workflowId,
    AttestationSystemWorkflowKind workflowKind,
    String resultHoldingAccountCode,
    @Nullable String capitalAccountCode,
    @Nullable String retainedResultAccountCode,
    boolean active) {
  AttestationSystemWorkflowPolicy {
    acceptedOrder =
        AttestationUnsignedEncoding.requireUnsigned(acceptedOrder, Long.BYTES, "acceptedOrder");
    Objects.requireNonNull(workflowId, "workflowId");
    Objects.requireNonNull(workflowKind, "workflowKind");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    requireNonBlankAccountCode(resultHoldingAccountCode, "resultHoldingAccountCode");
    if (workflowKind.requiresCapitalAndRetainedResultAccounts()) {
      requireAccountCode(capitalAccountCode, "capitalAccountCode");
      requireAccountCode(retainedResultAccountCode, "retainedResultAccountCode");
    } else if (capitalAccountCode != null || retainedResultAccountCode != null) {
      throw new IllegalArgumentException(
          "Attestation interim workflows must omit capital and retained-result accounts.");
    }
  }

  boolean hasSameConfiguration(AttestationSystemWorkflowPolicy other) {
    return workflowKind == other.workflowKind
        && resultHoldingAccountCode.equals(other.resultHoldingAccountCode)
        && Objects.equals(capitalAccountCode, other.capitalAccountCode)
        && Objects.equals(retainedResultAccountCode, other.retainedResultAccountCode);
  }

  private static void requireAccountCode(@Nullable String accountCode, String name) {
    if (accountCode == null) {
      throw new IllegalArgumentException("Attestation %s must be present.".formatted(name));
    }
    requireNonBlankAccountCode(accountCode, name);
  }

  private static void requireNonBlankAccountCode(String accountCode, String name) {
    if (accountCode.isBlank()) {
      throw new IllegalArgumentException("Attestation %s must be present.".formatted(name));
    }
  }
}
