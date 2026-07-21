package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.List;
import java.util.Objects;

/** Projects the aggregate generated effects of one attested reporting-period close. */
public final class AttestationPeriodCloseMutationProjection {
  private static final String SYSTEM = "system";

  private AttestationPeriodCloseMutationProjection() {}

  /** Projects one interim-result sweep and every generated posting that it persists. */
  public static AttestationOperationPreimages projectInterimResultSweep(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      int sweepOrder,
      List<CurrencyBalance> sweptTotals,
      List<AttestationClosePostingSnapshot> postings) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    ReportingPeriod checkedPeriod = Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    String checkedResultHoldingAccountCode =
        requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
    List<CurrencyBalance> checkedTotals =
        List.copyOf(Objects.requireNonNull(sweptTotals, "sweptTotals"));
    List<AttestationClosePostingSnapshot> checkedPostings = optionalPostings(postings);
    requirePositiveOrder(sweepOrder, "sweepOrder");
    requirePostingKind(checkedPostings, checkedOperationKind);
    return AttestationPeriodClosePreimageProjection.projectInterimResultSweep(
        checkedOperationKind,
        checkedPeriod,
        checkedResultHoldingAccountCode,
        sweepOrder,
        checkedTotals,
        checkedPostings);
  }

  /** Projects one fiscal-year close and every generated posting that it persists. */
  public static AttestationOperationPreimages projectFiscalYearClose(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedResultAccountCode,
      int closeOrder,
      List<AttestationClosePostingSnapshot> postings) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    ReportingPeriod checkedPeriod = Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    String checkedCapitalAccountCode = requireText(capitalAccountCode, "capitalAccountCode");
    String checkedResultHoldingAccountCode =
        requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
    String checkedRetainedResultAccountCode =
        requireText(retainedResultAccountCode, "retainedResultAccountCode");
    List<AttestationClosePostingSnapshot> checkedPostings = requiredPostings(postings);
    requirePositiveOrder(closeOrder, "closeOrder");
    requirePostingKind(checkedPostings, checkedOperationKind);
    return AttestationPeriodClosePreimageProjection.projectFiscalYearClose(
        checkedOperationKind,
        checkedPeriod,
        checkedCapitalAccountCode,
        checkedResultHoldingAccountCode,
        checkedRetainedResultAccountCode,
        closeOrder,
        checkedPostings);
  }

  private static List<AttestationClosePostingSnapshot> optionalPostings(
      List<AttestationClosePostingSnapshot> postings) {
    return List.copyOf(Objects.requireNonNull(postings, "postings"));
  }

  private static List<AttestationClosePostingSnapshot> requiredPostings(
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationClosePostingSnapshot> checked = optionalPostings(postings);
    if (checked.isEmpty()) {
      throw new IllegalArgumentException(
          "A reporting-period close must persist at least one posting.");
    }
    return checked;
  }

  private static void requirePostingKind(
      List<AttestationClosePostingSnapshot> postings, String expectedOperationKind) {
    for (AttestationClosePostingSnapshot posting : postings) {
      if (!expectedOperationKind.equals(tokenValue(posting.postingKind()))
          || !expectedOperationKind.equals(tokenValue(posting.postingOriginKind()))
          || !SYSTEM.equals(tokenValue(posting.sourceChannel()))) {
        throw new IllegalArgumentException(
            "Generated close postings must retain their attested operation kind and SYSTEM source channel.");
      }
    }
  }

  private static void requirePositiveOrder(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be at least one.");
    }
  }

  private static String requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
    return value;
  }

  private static String tokenValue(String value) {
    return requireText(value, "value").toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }
}
