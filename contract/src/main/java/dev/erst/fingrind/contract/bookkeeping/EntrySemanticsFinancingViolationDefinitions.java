package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Financing lifecycle violation definitions. */
final class EntrySemanticsFinancingViolationDefinitions {
  private EntrySemanticsFinancingViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "financing-arrangement-id-already-exists",
            "financing-lifecycle",
            "The selected financingArrangementId already identifies one durable financing arrangement in this book.",
            "Choose a new financingArrangementId for a distinct borrowing, or use the existing arrangement's admitted application command."),
        definition(
            "financing-arrangement-not-found",
            "financing-lifecycle",
            "The selected financingArrangementId does not identify one active financing arrangement in this book.",
            "Use a financingArrangementId returned by a prior financing-borrowing posting."),
        definition(
            "financing-principal-repayment-exceeds-outstanding",
            "financing-principal",
            "The requested principal repayment exceeds the selected financing arrangement's outstanding principal.",
            "Reduce principalAmount to the outstanding principal reported for the selected financingArrangementId."),
        definition(
            "financing-interest-payment-exceeds-accrued",
            "financing-interest",
            "The requested interest payment exceeds accrued unpaid interest on the selected financing arrangement.",
            "Record the required interest accrual first, or reduce interestAmount to the reported unpaid accrued interest."),
        definition(
            "financing-lifecycle-precedes-horizon",
            "financing-ordering",
            "The requested financing lifecycle event precedes the arrangement's retained lifecycle horizon.",
            "Use an effectiveDate on or after the originating borrowing and its latest retained application."),
        definition(
            "financing-currency-mismatch",
            "financing-currency",
            "The requested financing amount does not use the arrangement's functional currency.",
            "Use an amount in the functional currency retained by the selected financing arrangement."),
        definition(
            "financing-borrowing-reversal-requires-applications-reversed",
            "financing-reversal",
            "A financing borrowing cannot be reversed while active principal or interest applications remain.",
            "Reverse every active principal repayment, interest accrual, and interest payment before reversing the borrowing."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
