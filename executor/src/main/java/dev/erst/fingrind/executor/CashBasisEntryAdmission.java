package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffEntrySemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntryModeSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Owns cash-basis exclusions for typed business-event verbs. */
final class CashBasisEntryAdmission {
  private static final Map<BookkeepingEntryKind, Restriction> RESTRICTIONS =
      Map.ofEntries(
          Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, Restriction.RECEIVABLE),
          Map.entry(BookkeepingEntryKind.RECEIPT, Restriction.RECEIVABLE),
          Map.entry(BookkeepingEntryKind.PURCHASE_ON_CREDIT, Restriction.PAYABLE),
          Map.entry(BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT, Restriction.PAYABLE),
          Map.entry(BookkeepingEntryKind.EXPENSE_ON_CREDIT, Restriction.PAYABLE),
          Map.entry(BookkeepingEntryKind.PAYMENT, Restriction.PAYABLE),
          Map.entry(BookkeepingEntryKind.PREPAYMENT, Restriction.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.DEFERRED_REVENUE, Restriction.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE, Restriction.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION, Restriction.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT, Restriction.ACCRUAL_CUTOFF));

  private CashBasisEntryAdmission() {}

  static void appendViolation(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntryKind entryKind,
      String selectorField,
      String selectorValue,
      @Nullable ClassificationResult classification) {
    if (classification != null && classification.eventClass() == EconomicEventClass.ADJUSTMENT) {
      return;
    }
    Restriction restriction = RESTRICTIONS.get(entryKind);
    if (restriction == null) {
      return;
    }
    if (restriction == Restriction.RECEIVABLE) {
      PostEntryAdmissionSupport.appendViolationOnce(
          violations,
          "verb-requires-receivable-role",
          () ->
              BookkeepingEntryModeSemanticsViolations.verbRequiresReceivableRole(
                  selectorField, selectorValue));
      return;
    }
    if (restriction == Restriction.PAYABLE) {
      PostEntryAdmissionSupport.appendViolationOnce(
          violations,
          "verb-requires-payable-role",
          () ->
              BookkeepingEntryModeSemanticsViolations.verbRequiresPayableRole(
                  selectorField, selectorValue));
      return;
    }
    PostEntryAdmissionSupport.appendViolationOnce(
        violations,
        "accrual-cutoff-requires-accrual-basis",
        () ->
            AccrualCutoffEntrySemanticsViolations.requiresAccrualBasis(
                selectorField, selectorValue));
  }

  /** The capability a cash-basis book does not admit for one typed verb. */
  private enum Restriction {
    RECEIVABLE,
    PAYABLE,
    ACCRUAL_CUTOFF
  }
}
