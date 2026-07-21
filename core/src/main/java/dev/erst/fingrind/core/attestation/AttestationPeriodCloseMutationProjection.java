package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Projects the aggregate generated effects of one attested reporting-period close. */
public final class AttestationPeriodCloseMutationProjection {
  private static final String CLI = "cli";
  private static final int STEP_ORDER = 0;

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
    List<AttestationClosePostingSnapshot> checkedPostings = postings(postings);
    requirePositiveOrder(sweepOrder, "sweepOrder");
    requirePostingKind(checkedPostings, checkedOperationKind);
    return new AttestationOperationPreimages(
        requestPreimage(
                checkedOperationKind, checkedPeriod, checkedResultHoldingAccountCode, null, null)
            .encoded(),
        interimResultSweepEffect(
                checkedPeriod,
                checkedResultHoldingAccountCode,
                sweepOrder,
                checkedTotals,
                checkedPostings)
            .encoded());
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
    List<AttestationClosePostingSnapshot> checkedPostings = postings(postings);
    requirePositiveOrder(closeOrder, "closeOrder");
    requirePostingKind(checkedPostings, checkedOperationKind);
    return new AttestationOperationPreimages(
        requestPreimage(
                checkedOperationKind,
                checkedPeriod,
                checkedResultHoldingAccountCode,
                checkedCapitalAccountCode,
                checkedRetainedResultAccountCode)
            .encoded(),
        fiscalYearCloseEffect(
                checkedPeriod,
                checkedCapitalAccountCode,
                checkedResultHoldingAccountCode,
                checkedRetainedResultAccountCode,
                closeOrder,
                checkedPostings)
            .encoded());
  }

  private static AttestationPreimage requestPreimage(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      @org.jspecify.annotations.Nullable String capitalAccountCode,
      @org.jspecify.annotations.Nullable String retainedResultAccountCode) {
    return AttestationPreimage.of(
        List.of(
            command(operationKind),
            postingRequest(operationKind, reportingPeriod),
            periodCloseRequest(
                operationKind,
                reportingPeriod,
                resultHoldingAccountCode,
                capitalAccountCode,
                retainedResultAccountCode)));
  }

  private static AttestationPreimage interimResultSweepEffect(
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      int sweepOrder,
      List<CurrencyBalance> sweptTotals,
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationPreimage.Fact> facts = postingEffectFacts(postings);
    facts.add(
        new AttestationPreimage.Fact(
            0x0040,
            List.of(
                AttestationPreimageProjectionFields.mutation(),
                AttestationPreimageProjectionFields.unsigned64(sweepOrder),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateFrom()),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateTo()),
                AttestationPreimageProjectionFields.text(resultHoldingAccountCode))));
    for (CurrencyBalance sweptTotal : sweptTotals) {
      facts.add(interimResultSweepTotal(sweepOrder, sweptTotal));
    }
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0042,
              List.of(
                  AttestationPreimageProjectionFields.mutation(),
                  AttestationPreimageProjectionFields.unsigned64(sweepOrder),
                  AttestationPreimageProjectionFields.uuid(posting.postingId()))));
    }
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage fiscalYearCloseEffect(
      ReportingPeriod reportingPeriod,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedResultAccountCode,
      int closeOrder,
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationPreimage.Fact> facts = postingEffectFacts(postings);
    facts.add(
        new AttestationPreimage.Fact(
            0x0043,
            List.of(
                AttestationPreimageProjectionFields.mutation(),
                AttestationPreimageProjectionFields.unsigned64(closeOrder),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateFrom()),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateTo()),
                AttestationPreimageProjectionFields.text(capitalAccountCode),
                AttestationPreimageProjectionFields.text(resultHoldingAccountCode),
                AttestationPreimageProjectionFields.text(retainedResultAccountCode))));
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0044,
              List.of(
                  AttestationPreimageProjectionFields.mutation(),
                  AttestationPreimageProjectionFields.unsigned64(closeOrder),
                  AttestationPreimageProjectionFields.uuid(posting.postingId()))));
    }
    return AttestationPreimage.of(facts);
  }

  private static List<AttestationPreimage.Fact> postingEffectFacts(
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(postingEffect(posting));
      for (int lineOrder = 0; lineOrder < posting.journalLines().size(); lineOrder++) {
        facts.add(journalLineEffect(posting, lineOrder, posting.journalLines().get(lineOrder)));
      }
    }
    return facts;
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationPreimageProjectionFields.token(tokenValue(operationKind)),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationPreimageProjectionFields.token(CLI)));
  }

  private static AttestationPreimage.Fact postingRequest(
      String operationKind, ReportingPeriod reportingPeriod) {
    return new AttestationPreimage.Fact(
        0x0120,
        List.of(
            AttestationPreimageProjectionFields.unsigned32(STEP_ORDER),
            AttestationPreimageProjectionFields.token(tokenValue(operationKind)),
            AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateTo()),
            AttestationPreimageProjectionFields.token(tokenValue(operationKind)),
            AttestationField.absent(),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact periodCloseRequest(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      @org.jspecify.annotations.Nullable String capitalAccountCode,
      @org.jspecify.annotations.Nullable String retainedResultAccountCode) {
    return new AttestationPreimage.Fact(
        0x0140,
        List.of(
            AttestationPreimageProjectionFields.token(tokenValue(operationKind)),
            AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateFrom()),
            AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateTo()),
            AttestationField.absent(),
            AttestationPreimageProjectionFields.text(resultHoldingAccountCode),
            AttestationPreimageProjectionFields.optionalText(capitalAccountCode),
            AttestationPreimageProjectionFields.optionalText(retainedResultAccountCode)));
  }

  private static AttestationPreimage.Fact postingEffect(AttestationClosePostingSnapshot posting) {
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(posting.postingId()),
            AttestationPreimageProjectionFields.unsigned32(STEP_ORDER),
            AttestationPreimageProjectionFields.token(tokenValue(posting.postingKind())),
            AttestationPreimageProjectionFields.token(tokenValue(posting.postingKind())),
            AttestationPreimageProjectionFields.token(tokenValue(posting.postingOriginKind())),
            AttestationPreimageProjectionFields.date(posting.effectiveDate()),
            AttestationPreimageProjectionFields.instant(posting.recordedAt()),
            AttestationField.absent(),
            AttestationPreimageProjectionFields.uuid(posting.commandId()),
            AttestationPreimageProjectionFields.text(posting.idempotencyKey()),
            AttestationPreimageProjectionFields.text(posting.causationId()),
            AttestationPreimageProjectionFields.token(tokenValue(posting.sourceChannel()))));
  }

  private static AttestationPreimage.Fact journalLineEffect(
      AttestationClosePostingSnapshot posting, int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x0025,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(posting.postingId()),
            AttestationPreimageProjectionFields.unsigned32(lineOrder),
            AttestationPreimageProjectionFields.text(line.accountCode()),
            AttestationPreimageProjectionFields.token(tokenValue(line.side())),
            AttestationPreimageProjectionFields.signedMoney(line.currencyCode(), line.minorUnits()),
            AttestationField.absent()));
  }

  private static AttestationPreimage.Fact interimResultSweepTotal(
      int sweepOrder, CurrencyBalance sweptTotal) {
    long signedTotal =
        Math.subtractExact(
            sweptTotal.debitTotal().minorUnits(), sweptTotal.creditTotal().minorUnits());
    return new AttestationPreimage.Fact(
        0x0041,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.unsigned64(sweepOrder),
            AttestationPreimageProjectionFields.present(
                AttestationTextFieldValue.currency(sweptTotal.debitTotal().currencyUnit().code())),
            AttestationPreimageProjectionFields.signedMoney(
                sweptTotal.debitTotal().currencyUnit().code(), signedTotal)));
  }

  private static List<AttestationClosePostingSnapshot> postings(
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationClosePostingSnapshot> checked =
        List.copyOf(Objects.requireNonNull(postings, "postings"));
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
          || !CLI.equals(tokenValue(posting.sourceChannel()))) {
        throw new IllegalArgumentException(
            "Generated close postings must retain their attested operation kind and CLI source channel.");
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
    return requireText(value, "value").toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
