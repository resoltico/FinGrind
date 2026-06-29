package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CliFuzzSyntheticTaxRegistrationsTest {
  @Test
  void lookupStore_returns_empty_for_entries_without_tax_selection() {
    PostEntryCommand template =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    BookkeepingEntry expenseWithoutTax =
        new BookkeepingEntry.Expense(
            LocalDate.parse("2026-04-09"),
            new AccountCode("6100"),
            new AccountCode("1100"),
            new MonetaryAmount("EUR", "42"),
            null,
            null,
            null);

    var lookupStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(template, expenseWithoutTax).entry(),
            CliFuzzFixtures.fixedClock().instant());

    assertTrue(lookupStore.findTaxRegistration(new TaxRegistrationId("vat-lv")).isEmpty());
  }

  @Test
  void
      lookupStore_derives_synthetic_registration_shapes_for_selection_only_and_applied_tax_entries() {
    PostEntryCommand template =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));

    var saleSelectionStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-10"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        taxSelection("vat-lv", "vat-standard-sale"),
                        null))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    var saleRegistration =
        saleSelectionStore.findTaxRegistration(new TaxRegistrationId("vat-lv")).orElseThrow();
    assertEquals("Synthetic vat-lv", saleRegistration.taxRegistrationName().value());
    assertEquals("2100", saleRegistration.payableAccountCode().value());
    assertEquals("1300", saleRegistration.recoverableAccountCode().value());
    assertEquals(
        "Synthetic vat-standard-sale",
        saleRegistration.taxCodes().getFirst().taxCodeName().value());
    assertEquals(
        TaxApplicationKind.OUTPUT_SALE, saleRegistration.taxCodes().getFirst().applicationKind());
    assertTrue(saleSelectionStore.findTaxRegistration(new TaxRegistrationId("vat-ee")).isEmpty());

    var expenseRecoverableStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Expense(
                        LocalDate.parse("2026-04-11"),
                        new AccountCode("6100"),
                        new AccountCode("1100"),
                        new MonetaryAmount("EUR", "12100"),
                        null,
                        taxSelection("vat-lv", "vat-standard-expense"),
                        null))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    var recoverableCode =
        expenseRecoverableStore
            .findTaxRegistration(new TaxRegistrationId("vat-lv"))
            .orElseThrow()
            .taxCodes()
            .getFirst();
    assertEquals(TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE, recoverableCode.applicationKind());
    assertEquals(210_000, recoverableCode.rate().partsPerMillionOfWhole());

    var expenseNonrecoverableStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Expense(
                        LocalDate.parse("2026-04-12"),
                        new AccountCode("6100"),
                        new AccountCode("1100"),
                        new MonetaryAmount("EUR", "11200"),
                        null,
                        taxSelection("vat-lv", "vat-nonrecoverable-expense"),
                        null))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    var nonrecoverableCode =
        expenseNonrecoverableStore
            .findTaxRegistration(new TaxRegistrationId("vat-lv"))
            .orElseThrow()
            .taxCodes()
            .getFirst();
    assertEquals(
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE, nonrecoverableCode.applicationKind());
    assertEquals(120_000, nonrecoverableCode.rate().partsPerMillionOfWhole());

    var saleAppliedStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-13"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        taxSelection("vat-lv", "vat-standard-sale"),
                        appliedTax(
                            "vat-lv",
                            "vat-standard-sale",
                            "Filed VAT 21",
                            TaxInclusionMode.EXCLUSIVE,
                            TaxApplicationKind.OUTPUT_SALE,
                            "10000",
                            "2100",
                            "12100",
                            "2199")))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    var saleAppliedRegistration =
        saleAppliedStore.findTaxRegistration(new TaxRegistrationId("vat-lv")).orElseThrow();
    assertEquals("2199", saleAppliedRegistration.payableAccountCode().value());
    assertEquals("1300", saleAppliedRegistration.recoverableAccountCode().value());
    assertEquals(
        "Filed VAT 21", saleAppliedRegistration.taxCodes().getFirst().taxCodeName().value());

    var expenseAppliedStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Expense(
                        LocalDate.parse("2026-04-14"),
                        new AccountCode("6100"),
                        new AccountCode("1100"),
                        new MonetaryAmount("EUR", "12100"),
                        null,
                        taxSelection("vat-lv", "vat-standard-expense"),
                        appliedTax(
                            "vat-lv",
                            "vat-standard-expense",
                            "Filed Input VAT 21",
                            TaxInclusionMode.INCLUSIVE,
                            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                            "10000",
                            "2100",
                            "12100",
                            "1307")))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    var expenseAppliedRegistration =
        expenseAppliedStore.findTaxRegistration(new TaxRegistrationId("vat-lv")).orElseThrow();
    assertEquals("2100", expenseAppliedRegistration.payableAccountCode().value());
    assertEquals("1307", expenseAppliedRegistration.recoverableAccountCode().value());
    assertEquals(
        "Filed Input VAT 21",
        expenseAppliedRegistration.taxCodes().getFirst().taxCodeName().value());

    var saleAppliedWithoutAccountStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-15"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        taxSelection("vat-lv", "vat-standard-sale"),
                        appliedTax(
                            "vat-lv",
                            "vat-standard-sale",
                            "Filed VAT 21",
                            TaxInclusionMode.EXCLUSIVE,
                            TaxApplicationKind.OUTPUT_SALE,
                            "10000",
                            "2100",
                            "12100",
                            null)))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    assertEquals(
        "2100",
        saleAppliedWithoutAccountStore
            .findTaxRegistration(new TaxRegistrationId("vat-lv"))
            .orElseThrow()
            .payableAccountCode()
            .value());

    var expenseAppliedWithoutAccountStore =
        CliFuzzSyntheticTaxRegistrations.lookupStore(
            CliFuzzFixtureCommandSupport.withEntry(
                    template,
                    new BookkeepingEntry.Expense(
                        LocalDate.parse("2026-04-16"),
                        new AccountCode("6100"),
                        new AccountCode("1100"),
                        new MonetaryAmount("EUR", "12100"),
                        null,
                        taxSelection("vat-lv", "vat-standard-expense"),
                        appliedTax(
                            "vat-lv",
                            "vat-standard-expense",
                            "Filed Input VAT 21",
                            TaxInclusionMode.INCLUSIVE,
                            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                            "10000",
                            "2100",
                            "12100",
                            null)))
                .entry(),
            CliFuzzFixtures.fixedClock().instant());
    assertEquals(
        "1300",
        expenseAppliedWithoutAccountStore
            .findTaxRegistration(new TaxRegistrationId("vat-lv"))
            .orElseThrow()
            .recoverableAccountCode()
            .value());
  }

  @Test
  void bookkeepingCommand_preserves_nonrecoverable_expense_tax_account_nullability() {
    PostEntryCommand template =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
    PostEntryCommand nonrecoverableExpense =
        CliFuzzFixtureCommandSupport.withEntry(
            template,
            new BookkeepingEntry.Expense(
                LocalDate.parse("2026-04-15"),
                new AccountCode("6100"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "11200"),
                null,
                taxSelection("vat-lv", "vat-nonrecoverable-expense"),
                null));

    BookkeepingEntry.Expense resolvedExpense =
        (BookkeepingEntry.Expense)
            CliFuzzFixtures.bookkeepingCommand(nonrecoverableExpense)
                .callerAuthoredEntry()
                .orElseThrow();
    AppliedTax appliedTax = resolvedExpense.appliedTax();
    assertNotNull(appliedTax);

    assertEquals(TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE, appliedTax.applicationKind());
    assertNull(appliedTax.taxAccountCode());
  }

  private static TaxSelection taxSelection(String registrationId, String taxCode) {
    return new TaxSelection(new TaxRegistrationId(registrationId), new TaxCode(taxCode));
  }

  private static AppliedTax appliedTax(
      String registrationId,
      String taxCode,
      String taxCodeName,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind,
      String taxableMinorUnits,
      String taxMinorUnits,
      String grossMinorUnits,
      @org.jspecify.annotations.Nullable String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId(registrationId),
        new TaxCode(taxCode),
        new TaxCodeName(taxCodeName),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableMinorUnits),
        new MonetaryAmount("EUR", taxMinorUnits),
        new MonetaryAmount("EUR", grossMinorUnits),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }
}
