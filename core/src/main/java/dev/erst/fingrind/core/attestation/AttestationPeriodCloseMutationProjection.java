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
    String checkedOperationKind =
        requireOperationKind(
            operationKind, AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken());
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
      @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect derivedInterimSweep,
      List<AttestationClosePostingSnapshot> closePostings) {
    String checkedOperationKind =
        requireOperationKind(operationKind, AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken());
    ReportingPeriod checkedPeriod = Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    String checkedCapitalAccountCode = requireText(capitalAccountCode, "capitalAccountCode");
    String checkedResultHoldingAccountCode =
        requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
    String checkedRetainedResultAccountCode =
        requireText(retainedResultAccountCode, "retainedResultAccountCode");
    AttestationInterimResultSweepEffect checkedDerivedInterimSweep =
        derivedInterimSweep == null
            ? null
            : Objects.requireNonNull(derivedInterimSweep, "derivedInterimSweep");
    List<AttestationClosePostingSnapshot> checkedClosePostings = requiredPostings(closePostings);
    requirePositiveOrder(closeOrder, "closeOrder");
    requirePostingKind(checkedClosePostings, checkedOperationKind);
    if (checkedDerivedInterimSweep != null) {
      requireDerivedInterimSweepBelongsToFiscalYearClose(
          checkedDerivedInterimSweep, checkedPeriod, checkedResultHoldingAccountCode);
      requirePostingKind(
          checkedDerivedInterimSweep.postings(),
          AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken());
    }
    return AttestationPeriodClosePreimageProjection.projectFiscalYearClose(
        checkedOperationKind,
        checkedPeriod,
        checkedCapitalAccountCode,
        checkedResultHoldingAccountCode,
        checkedRetainedResultAccountCode,
        closeOrder,
        checkedDerivedInterimSweep,
        checkedClosePostings);
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

  private static String requireOperationKind(String operationKind, String expectedOperationKind) {
    String normalizedOperationKind = tokenValue(operationKind);
    if (!expectedOperationKind.equals(normalizedOperationKind)) {
      throw new IllegalArgumentException(
          "The reporting-period-close projection must use its declared attestation operation kind.");
    }
    return normalizedOperationKind;
  }

  private static void requirePositiveOrder(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be at least one.");
    }
  }

  private static void requireDerivedInterimSweepBelongsToFiscalYearClose(
      AttestationInterimResultSweepEffect derivedInterimSweep,
      ReportingPeriod fiscalYearPeriod,
      String fiscalYearResultHoldingAccountCode) {
    ReportingPeriod sweepPeriod = derivedInterimSweep.reportingPeriod();
    if (sweepPeriod.effectiveDateFrom().isBefore(fiscalYearPeriod.effectiveDateFrom())
        || !sweepPeriod.effectiveDateTo().equals(fiscalYearPeriod.effectiveDateTo())
        || !derivedInterimSweep
            .resultHoldingAccountCode()
            .equals(fiscalYearResultHoldingAccountCode)) {
      throw new IllegalArgumentException(
          "A fiscal-year close may contain only its own result-holding interim sweep.");
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
