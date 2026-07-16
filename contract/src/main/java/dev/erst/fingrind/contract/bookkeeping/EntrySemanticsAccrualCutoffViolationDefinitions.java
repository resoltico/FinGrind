package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Accrual cut-off lifecycle violation definitions. */
final class EntrySemanticsAccrualCutoffViolationDefinitions {
  private EntrySemanticsAccrualCutoffViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "accrual-cutoff-requires-accrual-basis",
            "accounting-basis",
            "The selected cut-off event belongs to accrual accounting and is not admitted on a cash-basis book.",
            "Use an accrual-basis book for a prepayment, deferred-revenue, accrued-expense, recognition, or accrued-expense settlement event."),
        definition(
            "accrual-cutoff-id-already-exists",
            "accrual-cutoff-lifecycle",
            "The selected accrualCutoffId already identifies one durable cut-off in this book.",
            "Use the existing accrualCutoffId for its admitted lifecycle application, or choose a new identifier for a distinct business fact."),
        definition(
            "accrual-cutoff-not-found",
            "accrual-cutoff-lifecycle",
            "The selected accrualCutoffId does not identify one durable cut-off in this book.",
            "Use an accrualCutoffId returned by a prior prepayment, deferred-revenue, or accrued-expense posting."),
        definition(
            "accrual-cutoff-application-kind-not-admitted",
            "accrual-cutoff-lifecycle",
            "The requested lifecycle application does not belong to the selected accrual cut-off kind.",
            "Recognize only prepayments or deferred revenue, and settle only accrued expenses."),
        definition(
            "accrual-cutoff-application-outside-recognition-interval",
            "accrual-cutoff-recognition",
            "The requested recognition effective date falls outside the cut-off's declared recognition interval.",
            "Use an effectiveDate inside the cut-off's inclusive recognition interval."),
        definition(
            "accrual-cutoff-application-precedes-horizon",
            "accrual-cutoff-ordering",
            "The requested application effective date precedes the cut-off's existing lifecycle horizon.",
            "Use an effectiveDate on or after the original cut-off and its latest durable application."),
        definition(
            "accrual-cutoff-application-exceeds-remaining-amount",
            "accrual-cutoff-amount",
            "The requested recognition or settlement exceeds the cut-off's remaining amount.",
            "Reduce amount to the remaining carrying amount or unpaid liability reported for the selected accrualCutoffId."),
        definition(
            "accrual-cutoff-reversal-precedes-horizon",
            "accrual-cutoff-ordering",
            "The requested reversal effective date precedes the cut-off's existing lifecycle horizon.",
            "Reverse the newest lifecycle fact first, or use an effectiveDate on or after the existing lifecycle horizon."),
        definition(
            "accrual-cutoff-origin-reversal-requires-zero-applications",
            "accrual-cutoff-reversal",
            "The requested origin reversal would leave active recognition or settlement lifecycle facts without their durable origin.",
            "Reverse every active recognition or settlement application first, then reverse the originating cut-off posting."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
