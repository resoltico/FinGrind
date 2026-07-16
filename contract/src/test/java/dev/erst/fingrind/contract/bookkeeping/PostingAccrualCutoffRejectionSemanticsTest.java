package dev.erst.fingrind.contract.bookkeeping;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Contract coverage for deterministic accrual cut-off lifecycle rejections. */
class PostingAccrualCutoffRejectionSemanticsTest {
  private static final AccrualCutoffId CUTOFF_ID = new AccrualCutoffId("prepayment-2026-q2");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-05-01");
  private static final LocalDate HORIZON_DATE = LocalDate.parse("2026-05-02");

  @Test
  void lifecycleRejections_publishStableCodesFieldsAndConcreteFacts() {
    PostingRejection.EntrySemanticsViolation basis =
        PostingAccrualCutoffRejectionSemantics.requiresAccrualBasis("PREPAYMENT");
    PostingRejection.EntrySemanticsViolation duplicate =
        PostingAccrualCutoffRejectionSemantics.idAlreadyExists("PREPAYMENT", CUTOFF_ID);
    PostingRejection.EntrySemanticsViolation missing =
        PostingAccrualCutoffRejectionSemantics.notFound("ACCRUAL_CUTOFF_RECOGNITION", CUTOFF_ID);
    PostingRejection.EntrySemanticsViolation kind =
        PostingAccrualCutoffRejectionSemantics.applicationKindNotAdmitted(
            "ACCRUAL_CUTOFF_RECOGNITION", CUTOFF_ID, AccrualCutoffKind.ACCRUED_EXPENSE);
    PostingRejection.EntrySemanticsViolation interval =
        PostingAccrualCutoffRejectionSemantics.recognitionOutsideInterval(
            "ACCRUAL_CUTOFF_RECOGNITION",
            CUTOFF_ID,
            EFFECTIVE_DATE,
            LocalDate.parse("2026-05-02"),
            LocalDate.parse("2026-05-31"));
    PostingRejection.EntrySemanticsViolation horizon =
        PostingAccrualCutoffRejectionSemantics.applicationPrecedesHorizon(
            "ACCRUAL_CUTOFF_RECOGNITION", CUTOFF_ID, EFFECTIVE_DATE, HORIZON_DATE);
    PostingRejection.EntrySemanticsViolation exceeds =
        PostingAccrualCutoffRejectionSemantics.applicationExceedsRemainingAmount(
            "ACCRUAL_CUTOFF_RECOGNITION", CUTOFF_ID, money("500"), money("400"));
    PostingRejection.EntrySemanticsViolation reversal =
        PostingAccrualCutoffRejectionSemantics.reversalPrecedesHorizon(
            "REVERSAL", CUTOFF_ID, EFFECTIVE_DATE, HORIZON_DATE);
    PostingRejection.EntrySemanticsViolation originReversal =
        PostingAccrualCutoffRejectionSemantics.originReversalRequiresZeroApplications(
            "REVERSAL", CUTOFF_ID);

    assertViolation(basis, "accrual-cutoff-requires-accrual-basis", "entryKind", "ACCRUAL");
    assertViolation(
        duplicate, "accrual-cutoff-id-already-exists", "accrualCutoffId", CUTOFF_ID.value());
    assertViolation(missing, "accrual-cutoff-not-found", "accrualCutoffId", CUTOFF_ID.value());
    assertViolation(
        kind,
        "accrual-cutoff-application-kind-not-admitted",
        "accrualCutoffId",
        AccrualCutoffKind.ACCRUED_EXPENSE.wireValue());
    assertViolation(
        interval,
        "accrual-cutoff-application-outside-recognition-interval",
        "effectiveDate",
        "2026-05-31");
    assertViolation(
        horizon,
        "accrual-cutoff-application-precedes-horizon",
        "effectiveDate",
        HORIZON_DATE.toString());
    assertViolation(
        exceeds, "accrual-cutoff-application-exceeds-remaining-amount", "amount", "EUR 400");
    assertViolation(
        reversal,
        "accrual-cutoff-reversal-precedes-horizon",
        "effectiveDate",
        HORIZON_DATE.toString());
    assertViolation(
        originReversal,
        "accrual-cutoff-origin-reversal-requires-zero-applications",
        "reversal.priorPostingId",
        "recognition or settlement applications");
  }

  @Test
  void lifecycleRejections_rejectMissingPublishedSelectorAndCutoffFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PostingAccrualCutoffRejectionSemantics.requiresAccrualBasis(" "));
    assertThrows(
        NullPointerException.class,
        () -> PostingAccrualCutoffRejectionSemantics.idAlreadyExists("PREPAYMENT", nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            PostingAccrualCutoffRejectionSemantics.recognitionOutsideInterval(
                "ACCRUAL_CUTOFF_RECOGNITION", CUTOFF_ID, nullOf(), EFFECTIVE_DATE, HORIZON_DATE));
  }

  private static void assertViolation(
      PostingRejection.EntrySemanticsViolation violation,
      String code,
      String field,
      String messageFragment) {
    assertEquals(code, violation.code());
    assertEquals(field, violation.field());
    assertTrue(violation.message().contains(messageFragment), violation.message());
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }
}
