package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.runtime.ErrorDescriptor;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.util.Arrays;
import java.util.List;

/** Stable failure vocabulary for top-level {@code execute-plan} outcomes. */
public enum LedgerPlanFailure {
  ASSERTION_FAILED(
      "assertion-failed",
      ProtocolEnvelopeStatus.ERROR,
      FailureCategory.DOMAIN_SEMANTIC,
      3,
      "Ledger-plan execution stopped because an assertion evaluated false and rolled back the transaction."),
  READ_ONLY_PLAN_MUTATION_FORBIDDEN(
      "read-only-plan-mutation-forbidden",
      ProtocolEnvelopeStatus.REJECTED,
      FailureCategory.DOMAIN_SEMANTIC,
      2,
      "A read-only ledger plan cannot include a step that changes the protected book."),
  UNEXPECTED_STEP_FAILURE(
      "unexpected-step-failure",
      ProtocolEnvelopeStatus.REJECTED,
      FailureCategory.INTERNAL,
      2,
      "Ledger-plan execution stopped because FinGrind encountered an unexpected failure while executing a step."),
  UNEXPECTED_PLAN_FAILURE(
      "unexpected-plan-failure",
      ProtocolEnvelopeStatus.REJECTED,
      FailureCategory.INTERNAL,
      2,
      "Ledger-plan execution stopped because FinGrind encountered an unexpected transaction-boundary failure.");

  private final String code;
  private final ProtocolEnvelopeStatus envelopeStatus;
  private final FailureCategory category;
  private final int exitCode;
  private final String description;

  LedgerPlanFailure(
      String code,
      ProtocolEnvelopeStatus envelopeStatus,
      FailureCategory category,
      int exitCode,
      String description) {
    this.code = code;
    this.envelopeStatus = envelopeStatus;
    this.category = category;
    this.exitCode = exitCode;
    this.description = description;
  }

  /** Returns the stable top-level failure code. */
  public String code() {
    return code;
  }

  /** Returns the response status that carries this failure. */
  public ProtocolEnvelopeStatus envelopeStatus() {
    return envelopeStatus;
  }

  /** Returns the published failure category. */
  public FailureCategory category() {
    return category;
  }

  /** Returns the canonical process exit code for this top-level failure. */
  public int exitCode() {
    return exitCode;
  }

  /** Returns the canonical machine-contract explanation. */
  public String description() {
    return description;
  }

  /** Returns every plan-rejection descriptor. */
  public static List<RejectionDescriptor> rejectionDescriptors() {
    return Arrays.stream(values())
        .filter(failure -> failure.envelopeStatus == ProtocolEnvelopeStatus.REJECTED)
        .map(
            failure -> new RejectionDescriptor(failure.code, failure.category, failure.description))
        .toList();
  }

  /** Returns every plan-error descriptor. */
  public static List<ErrorDescriptor> errorDescriptors() {
    return Arrays.stream(values())
        .filter(failure -> failure.envelopeStatus == ProtocolEnvelopeStatus.ERROR)
        .map(
            failure ->
                new ErrorDescriptor(
                    failure.code, failure.category, failure.exitCode, failure.description))
        .toList();
  }
}
