package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import java.math.BigInteger;
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
                mutation(),
                unsigned64(sweepOrder),
                date(reportingPeriod.effectiveDateFrom()),
                date(reportingPeriod.effectiveDateTo()),
                text(resultHoldingAccountCode))));
    for (CurrencyBalance sweptTotal : sweptTotals) {
      facts.add(interimResultSweepTotal(sweepOrder, sweptTotal));
    }
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0042, List.of(mutation(), unsigned64(sweepOrder), uuid(posting.postingId()))));
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
                mutation(),
                unsigned64(closeOrder),
                date(reportingPeriod.effectiveDateFrom()),
                date(reportingPeriod.effectiveDateTo()),
                text(capitalAccountCode),
                text(resultHoldingAccountCode),
                text(retainedResultAccountCode))));
    for (AttestationClosePostingSnapshot posting : postings) {
      facts.add(
          new AttestationPreimage.Fact(
              0x0044, List.of(mutation(), unsigned64(closeOrder), uuid(posting.postingId()))));
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
            token(operationKind),
            AttestationField.absent(),
            AttestationField.absent(),
            token(CLI)));
  }

  private static AttestationPreimage.Fact postingRequest(
      String operationKind, ReportingPeriod reportingPeriod) {
    return new AttestationPreimage.Fact(
        0x0120,
        List.of(
            unsigned32(STEP_ORDER),
            token(operationKind),
            date(reportingPeriod.effectiveDateTo()),
            token(operationKind),
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
            token(operationKind),
            date(reportingPeriod.effectiveDateFrom()),
            date(reportingPeriod.effectiveDateTo()),
            AttestationField.absent(),
            text(resultHoldingAccountCode),
            optionalText(capitalAccountCode),
            optionalText(retainedResultAccountCode)));
  }

  private static AttestationPreimage.Fact postingEffect(AttestationClosePostingSnapshot posting) {
    return new AttestationPreimage.Fact(
        0x0020,
        List.of(
            mutation(),
            uuid(posting.postingId()),
            unsigned32(STEP_ORDER),
            token(posting.postingKind()),
            token(posting.postingKind()),
            token(posting.postingOriginKind()),
            date(posting.effectiveDate()),
            instant(posting.recordedAt()),
            AttestationField.absent(),
            uuid(posting.commandId()),
            text(posting.idempotencyKey()),
            text(posting.causationId()),
            token(posting.sourceChannel())));
  }

  private static AttestationPreimage.Fact journalLineEffect(
      AttestationClosePostingSnapshot posting, int lineOrder, AttestationPostingLine line) {
    return new AttestationPreimage.Fact(
        0x0025,
        List.of(
            mutation(),
            uuid(posting.postingId()),
            unsigned32(lineOrder),
            text(line.accountCode()),
            token(line.side()),
            money(line.currencyCode(), line.minorUnits()),
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
            mutation(),
            unsigned64(sweepOrder),
            present(
                AttestationTextFieldValue.currency(sweptTotal.debitTotal().currencyUnit().code())),
            money(sweptTotal.debitTotal().currencyUnit().code(), signedTotal)));
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

  private static AttestationField mutation() {
    return present(
        AttestationNumericFieldValue.mutation(AttestationEffectMutation.CREATE.wireValue()));
  }

  private static AttestationField unsigned32(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Unsigned 32-bit values must not be negative.");
    }
    return present(AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(value)));
  }

  private static AttestationField unsigned64(int value) {
    return present(AttestationNumericFieldValue.unsigned64(BigInteger.valueOf(value)));
  }

  private static AttestationField uuid(java.util.UUID value) {
    return present(AttestationBinaryFieldValue.uuid(value));
  }

  private static AttestationField date(java.time.LocalDate value) {
    return present(AttestationTextFieldValue.date(value));
  }

  private static AttestationField instant(java.time.Instant value) {
    return present(AttestationTextFieldValue.instant(value));
  }

  private static AttestationField text(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  private static AttestationField token(String value) {
    return present(AttestationTextFieldValue.token(tokenValue(value)));
  }

  private static AttestationField optionalText(@org.jspecify.annotations.Nullable String value) {
    return value == null ? AttestationField.absent() : text(value);
  }

  private static AttestationField money(String currencyCode, long signedMinorUnits) {
    boolean negative = signedMinorUnits < 0;
    return present(
        AttestationNumericFieldValue.money(
            currencyCode, negative, BigInteger.valueOf(signedMinorUnits).abs()));
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }
}
