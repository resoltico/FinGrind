package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves cash-basis exclusions across receivable, payable, and accrual cut-off command families.
 */
class CashBasisEntryAdmissionTest {
  @Test
  void appendViolation_classifiesEveryRestrictedCommandFamily() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    CashBasisEntryAdmission.appendViolation(
        violations,
        dev.erst.fingrind.core.BookkeepingEntryKind.SALE_ON_CREDIT,
        "entryKind",
        "SALE_ON_CREDIT",
        null);
    CashBasisEntryAdmission.appendViolation(
        violations,
        dev.erst.fingrind.core.BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        "entryKind",
        "PURCHASE_ON_CREDIT",
        null);
    CashBasisEntryAdmission.appendViolation(
        violations,
        dev.erst.fingrind.core.BookkeepingEntryKind.PREPAYMENT,
        "entryKind",
        "PREPAYMENT",
        null);
    CashBasisEntryAdmission.appendViolation(
        violations,
        dev.erst.fingrind.core.BookkeepingEntryKind.OWNER_CONTRIBUTION,
        "entryKind",
        "OWNER_CONTRIBUTION",
        null);

    assertEquals(
        List.of(
            "verb-requires-receivable-role",
            "verb-requires-payable-role",
            "accrual-cutoff-requires-accrual-basis"),
        violations.stream()
            .map(BookkeepingPostingRejection.EntrySemanticsViolation::code)
            .toList());
  }
}
