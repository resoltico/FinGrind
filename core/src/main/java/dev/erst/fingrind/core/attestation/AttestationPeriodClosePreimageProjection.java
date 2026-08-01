package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Projects each reporting-period close's declared request and generated posting effects. */
final class AttestationPeriodClosePreimageProjection {
  private static final String CLI = "cli";
  private static final String PERIOD_CLOSE = "period-close";
  private static final int STEP_ORDER = 0;

  private AttestationPeriodClosePreimageProjection() {}

  static AttestationOperationPreimages projectInterimResultSweep(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      int sweepOrder,
      List<CurrencyBalance> sweptTotals,
      List<AttestationClosePostingSnapshot> postings) {
    return new AttestationOperationPreimages(
        requestPreimage(operationKind, reportingPeriod, resultHoldingAccountCode, null, null)
            .encoded(),
        interimResultSweepEffect(
                operationKind,
                reportingPeriod,
                resultHoldingAccountCode,
                sweepOrder,
                sweptTotals,
                postings)
            .encoded());
  }

  static AttestationOperationPreimages projectFiscalYearClose(
      String operationKind,
      ReportingPeriod reportingPeriod,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedResultAccountCode,
      int closeOrder,
      @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect derivedInterimSweep,
      List<AttestationClosePostingSnapshot> closePostings) {
    return new AttestationOperationPreimages(
        requestPreimage(
                operationKind,
                reportingPeriod,
                resultHoldingAccountCode,
                capitalAccountCode,
                retainedResultAccountCode)
            .encoded(),
        fiscalYearCloseEffect(
                operationKind,
                reportingPeriod,
                capitalAccountCode,
                resultHoldingAccountCode,
                retainedResultAccountCode,
                closeOrder,
                derivedInterimSweep,
                closePostings)
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
      String operationKind,
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      int sweepOrder,
      List<CurrencyBalance> sweptTotals,
      List<AttestationClosePostingSnapshot> postings) {
    List<AttestationPreimage.Fact> facts = postingEffectFacts(operationKind, postings);
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
      String operationKind,
      ReportingPeriod reportingPeriod,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedResultAccountCode,
      int closeOrder,
      @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect derivedInterimSweep,
      List<AttestationClosePostingSnapshot> closePostings) {
    List<AttestationClosePostingSnapshot> allPostings = new ArrayList<>();
    if (derivedInterimSweep != null) {
      allPostings.addAll(derivedInterimSweep.postings());
    }
    allPostings.addAll(closePostings);
    requireDistinctPostingIds(allPostings);
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    if (derivedInterimSweep != null) {
      facts.addAll(
          postingEffectFacts(
              AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken(),
              derivedInterimSweep.postings()));
    }
    facts.addAll(postingEffectFacts(operationKind, closePostings));
    if (derivedInterimSweep != null) {
      appendInterimResultSweepFacts(facts, derivedInterimSweep);
    }
    appendFiscalYearCloseFacts(
        facts,
        reportingPeriod,
        capitalAccountCode,
        resultHoldingAccountCode,
        retainedResultAccountCode,
        closeOrder,
        closePostings);
    return AttestationPreimage.of(facts);
  }

  private static void appendInterimResultSweepFacts(
      List<AttestationPreimage.Fact> facts,
      AttestationInterimResultSweepEffect derivedInterimSweep) {
    ReportingPeriod reportingPeriod = derivedInterimSweep.reportingPeriod();
    int sweepOrder = derivedInterimSweep.sweepOrder();
    facts.add(
        new AttestationPreimage.Fact(
            0x0040,
            List.of(
                AttestationPreimageProjectionFields.mutation(),
                AttestationPreimageProjectionFields.unsigned64(sweepOrder),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateFrom()),
                AttestationPreimageProjectionFields.date(reportingPeriod.effectiveDateTo()),
                AttestationPreimageProjectionFields.text(
                    derivedInterimSweep.resultHoldingAccountCode()))));
    for (CurrencyBalance sweptTotal : derivedInterimSweep.sweptTotals()) {
      facts.add(interimResultSweepTotal(sweepOrder, sweptTotal));
    }
    for (AttestationClosePostingSnapshot posting : derivedInterimSweep.postings()) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0042,
              List.of(
                  AttestationPreimageProjectionFields.mutation(),
                  AttestationPreimageProjectionFields.unsigned64(sweepOrder),
                  AttestationPreimageProjectionFields.uuid(posting.postingId()))));
    }
  }

  private static void appendFiscalYearCloseFacts(
      List<AttestationPreimage.Fact> facts,
      ReportingPeriod reportingPeriod,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedResultAccountCode,
      int closeOrder,
      List<AttestationClosePostingSnapshot> closePostings) {
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
    for (AttestationClosePostingSnapshot posting : closePostings) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0044,
              List.of(
                  AttestationPreimageProjectionFields.mutation(),
                  AttestationPreimageProjectionFields.unsigned64(closeOrder),
                  AttestationPreimageProjectionFields.uuid(posting.postingId()))));
    }
  }

  private static void requireDistinctPostingIds(List<AttestationClosePostingSnapshot> postings) {
    long distinctPostingIds =
        postings.stream().map(AttestationClosePostingSnapshot::postingId).distinct().count();
    if (distinctPostingIds != postings.size()) {
      throw new IllegalArgumentException(
          "A fiscal-year close must not project one generated posting more than once.");
    }
  }

  private static List<AttestationPreimage.Fact> postingEffectFacts(
      String operationKind, List<AttestationClosePostingSnapshot> postings) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(postingEffect(operationKind, posting));
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
            AttestationPreimageProjectionFields.token(PERIOD_CLOSE),
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
            operationKind.equals(AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken())
                ? AttestationPreimageProjectionFields.unsigned32(
                    reportingPeriod.effectiveDateTo().getYear())
                : AttestationField.absent(),
            AttestationPreimageProjectionFields.text(resultHoldingAccountCode),
            AttestationPreimageProjectionFields.optionalText(capitalAccountCode),
            AttestationPreimageProjectionFields.optionalText(retainedResultAccountCode)));
  }

  private static AttestationPreimage.Fact postingEffect(
      String operationKind, AttestationClosePostingSnapshot posting) {
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            AttestationPreimageProjectionFields.mutation(),
            AttestationPreimageProjectionFields.uuid(posting.postingId()),
            AttestationPreimageProjectionFields.unsigned32(STEP_ORDER),
            AttestationPreimageProjectionFields.token(tokenValue(operationKind)),
            AttestationPreimageProjectionFields.token(PERIOD_CLOSE),
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

  private static String tokenValue(String value) {
    String checkedValue = Objects.requireNonNull(value, "value");
    if (checkedValue.isBlank()) {
      throw new IllegalArgumentException("value must not be blank.");
    }
    return checkedValue.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
