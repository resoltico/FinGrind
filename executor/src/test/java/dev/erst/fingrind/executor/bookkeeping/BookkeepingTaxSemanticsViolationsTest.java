package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import org.junit.jupiter.api.Test;

/** Direct coverage for the executor translation of canonical tax-semantics violations. */
class BookkeepingTaxSemanticsViolationsTest {
  @Test
  void factories_preserveCanonicalTaxViolationCodesAndFields() {
    assertViolation(
        BookkeepingTaxSemanticsViolations.unknownTaxRegistration(
            "entryKind", "SALE_SETTLED", new TaxRegistrationId("vat-lv")),
        "unknown-tax-registration",
        "tax.taxRegistrationId");
    assertViolation(
        BookkeepingTaxSemanticsViolations.unknownTaxCode(
            "entryKind", "SALE_SETTLED", new TaxRegistrationId("vat-lv"), new TaxCode("standard")),
        "unknown-tax-code",
        "tax.taxCode");
    assertViolation(
        BookkeepingTaxSemanticsViolations.taxApplicationKindMismatch(
            "entryKind",
            "SALE_SETTLED",
            new TaxCode("standard"),
            TaxApplicationKind.OUTPUT_SALE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE),
        "tax-application-kind-mismatch",
        "tax.taxCode");
    assertViolation(
        BookkeepingTaxSemanticsViolations.taxCompositionMoneyRangeExceeded("SALE_SETTLED"),
        "tax-composition-money-range-exceeded",
        "amount");
  }

  @Test
  void selectionFactories_rejectNonCanonicalSelectorFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BookkeepingTaxSemanticsViolations.unknownTaxRegistration(
                "entryKindCode", "SALE_SETTLED", new TaxRegistrationId("vat-lv")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BookkeepingTaxSemanticsViolations.unknownTaxCode(
                "entryKindCode",
                "SALE_SETTLED",
                new TaxRegistrationId("vat-lv"),
                new TaxCode("standard")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BookkeepingTaxSemanticsViolations.taxApplicationKindMismatch(
                "entryKindCode",
                "SALE_SETTLED",
                new TaxCode("standard"),
                TaxApplicationKind.OUTPUT_SALE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE));
  }

  private static void assertViolation(
      BookkeepingPostingRejection.EntrySemanticsViolation violation,
      String expectedCode,
      String expectedField) {
    assertEquals(expectedCode, violation.code());
    assertEquals(expectedField, violation.field());
  }
}
