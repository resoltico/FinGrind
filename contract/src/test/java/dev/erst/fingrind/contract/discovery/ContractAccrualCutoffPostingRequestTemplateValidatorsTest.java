package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Contract coverage for accrual cut-off template fields that ordinary entries must reject. */
class ContractAccrualCutoffPostingRequestTemplateValidatorsTest {
  private static final ContractTemplates.RecognitionIntervalTemplateDescriptor
      RECOGNITION_INTERVAL =
          new ContractTemplates.RecognitionIntervalTemplateDescriptor("2026-04-25", "2026-06-30");

  @Test
  void validationRejectsMissingAndForbiddenRecognitionIntervals() {
    IllegalArgumentException missingPrepaymentInterval =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateValidators.validate(
                    BookkeepingEntryKind.PREPAYMENT, prepaymentFields(null), null));
    IllegalArgumentException accruedExpenseInterval =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateValidators.validate(
                    BookkeepingEntryKind.ACCRUED_EXPENSE,
                    accruedExpenseFields(RECOGNITION_INTERVAL),
                    null));

    assertEquals(
        "recognitionInterval is required for prepayment.", missingPrepaymentInterval.getMessage());
    assertEquals(
        "recognitionInterval must be absent for accruedExpense.",
        accruedExpenseInterval.getMessage());
  }

  @Test
  void ordinaryTemplatesRejectAccrualCutoffRecognitionIntervals() {
    IllegalArgumentException ordinaryTemplateInterval =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateValidators.validate(
                    BookkeepingEntryKind.SALE_SETTLED,
                    ordinaryFieldsWithRecognitionInterval(),
                    null));

    assertEquals(
        "recognitionInterval must be absent for SALE_SETTLED.",
        ordinaryTemplateInterval.getMessage());
  }

  private static PostingTemplateFields prepaymentFields(
      ContractTemplates.@Nullable RecognitionIntervalTemplateDescriptor recognitionInterval) {
    return new PostingTemplateFields(
        "cash",
        null,
        null,
        null,
        null,
        "operating-expense",
        null,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "prepayment-2026-q2",
        "prepaid-expense",
        null,
        null,
        recognitionInterval,
        null,
        null,
        null,
        null,
        null);
  }

  private static PostingTemplateFields accruedExpenseFields(
      ContractTemplates.@Nullable RecognitionIntervalTemplateDescriptor recognitionInterval) {
    return new PostingTemplateFields(
        null,
        null,
        null,
        null,
        null,
        "operating-expense",
        null,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "accrued-expense-2026-04",
        null,
        null,
        "accrued-expense",
        recognitionInterval,
        null,
        null,
        null,
        null,
        null);
  }

  private static PostingTemplateFields ordinaryFieldsWithRecognitionInterval() {
    return new PostingTemplateFields(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        RECOGNITION_INTERVAL,
        null,
        null,
        null,
        null,
        null);
  }
}
