package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for tax-resolution semantics on typed sale and expense events. */
class TaxPostingResolutionTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T00:00:00Z");
  private static final TaxRegistrationId REGISTRATION_ID = new TaxRegistrationId("vat-lv");

  @Test
  void resolveSale_appliesExclusiveOutputTaxToGrossCashAndPayable() {
    BookkeepingEntry.Sale resolved =
        assertInstanceOf(
            BookkeepingEntry.Sale.class,
            TaxPostingResolution.resolve(
                new BookkeepingEntry.Sale(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "10000"),
                    null,
                    new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                    null),
                lookupStore(registration())));

    AppliedTax appliedTax = Objects.requireNonNull(resolved.appliedTax());
    assertEquals("10000", appliedTax.taxableAmount().minorUnits());
    assertEquals("2100", appliedTax.taxAmount().minorUnits());
    assertEquals("12100", appliedTax.grossAmount().minorUnits());
    assertEquals(new AccountCode("2100"), appliedTax.taxAccountCode());
    assertEquals(3, resolved.journalEntry().lines().size());
    assertEquals(
        new JournalLine(
            new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "121.00")),
        resolved.journalEntry().lines().get(0));
    assertEquals(
        new JournalLine(
            new AccountCode("4000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "100.00")),
        resolved.journalEntry().lines().get(1));
    assertEquals(
        new JournalLine(
            new AccountCode("2100"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "21.00")),
        resolved.journalEntry().lines().get(2));
  }

  @Test
  void resolveExpense_appliesInclusiveRecoverableTax() {
    BookkeepingEntry.Expense resolved =
        assertInstanceOf(
            BookkeepingEntry.Expense.class,
            TaxPostingResolution.resolve(
                new BookkeepingEntry.Expense(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("5000"),
                    new AccountCode("1000"),
                    new MonetaryAmount("EUR", "12100"),
                    null,
                    new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-expense")),
                    null),
                lookupStore(registration())));

    AppliedTax appliedTax = Objects.requireNonNull(resolved.appliedTax());
    assertEquals("10000", appliedTax.taxableAmount().minorUnits());
    assertEquals("2100", appliedTax.taxAmount().minorUnits());
    assertEquals("12100", appliedTax.grossAmount().minorUnits());
    assertEquals(new AccountCode("1300"), appliedTax.taxAccountCode());
    assertEquals(3, resolved.journalEntry().lines().size());
    assertEquals(
        new JournalLine(
            new AccountCode("5000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "100.00")),
        resolved.journalEntry().lines().get(0));
    assertEquals(
        new JournalLine(
            new AccountCode("1300"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "21.00")),
        resolved.journalEntry().lines().get(1));
    assertEquals(
        new JournalLine(
            new AccountCode("1000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "121.00")),
        resolved.journalEntry().lines().get(2));
  }

  @Test
  void resolveExpense_appliesInclusiveNonrecoverableTaxWithoutSeparateTaxAccount() {
    BookkeepingEntry.Expense resolved =
        assertInstanceOf(
            BookkeepingEntry.Expense.class,
            TaxPostingResolution.resolve(
                new BookkeepingEntry.Expense(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("5010"),
                    new AccountCode("1000"),
                    new MonetaryAmount("EUR", "11200"),
                    null,
                    new TaxSelection(REGISTRATION_ID, new TaxCode("vat-nonrecoverable-expense")),
                    null),
                lookupStore(registration())));

    AppliedTax appliedTax = Objects.requireNonNull(resolved.appliedTax());
    assertEquals("10000", appliedTax.taxableAmount().minorUnits());
    assertEquals("1200", appliedTax.taxAmount().minorUnits());
    assertEquals("11200", appliedTax.grossAmount().minorUnits());
    assertNull(appliedTax.taxAccountCode());
    assertEquals(2, resolved.journalEntry().lines().size());
    assertEquals(
        new JournalLine(
            new AccountCode("5010"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "112.00")),
        resolved.journalEntry().lines().get(0));
    assertEquals(
        new JournalLine(
            new AccountCode("1000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "112.00")),
        resolved.journalEntry().lines().get(1));
  }

  @Test
  void resolve_returnsOriginalEntriesWhenTaxIsAbsentOrAlreadyResolved() {
    BookkeepingEntry.Sale noTaxSale =
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "10000"),
            null,
            null,
            null);
    BookkeepingEntry.Sale resolvedSale =
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "12100"),
            null,
            new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
            new AppliedTax(
                REGISTRATION_ID,
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE,
                new MonetaryAmount("EUR", "10000"),
                new MonetaryAmount("EUR", "2100"),
                new MonetaryAmount("EUR", "12100"),
                new AccountCode("2100")));
    BookkeepingEntry.Expense resolvedExpense =
        new BookkeepingEntry.Expense(
            LocalDate.parse("2026-04-07"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "12100"),
            null,
            new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-expense")),
            new AppliedTax(
                REGISTRATION_ID,
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                new MonetaryAmount("EUR", "10000"),
                new MonetaryAmount("EUR", "2100"),
                new MonetaryAmount("EUR", "12100"),
                new AccountCode("1300")));

    assertSame(noTaxSale, TaxPostingResolution.resolve(noTaxSale, lookupStore(registration())));
    assertSame(
        resolvedSale, TaxPostingResolution.resolve(resolvedSale, lookupStore(registration())));
    assertSame(
        resolvedExpense,
        TaxPostingResolution.resolve(resolvedExpense, lookupStore(registration())));
  }

  @Test
  void resolve_rejectsUnknownRegistrationUnknownCodeAndMismatchedApplicationKinds() {
    IllegalArgumentException unknownRegistration =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TaxPostingResolution.resolve(
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-07"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        new TaxSelection(
                            new TaxRegistrationId("missing-tax"), new TaxCode("vat-standard-sale")),
                        null),
                    lookupStore(registration())));
    assertEquals("Unknown taxRegistrationId 'missing-tax'.", unknownRegistration.getMessage());

    IllegalArgumentException unknownCode =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TaxPostingResolution.resolve(
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-07"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        new TaxSelection(REGISTRATION_ID, new TaxCode("missing-code")),
                        null),
                    lookupStore(registration())));
    assertEquals(
        "Unknown taxCode 'missing-code' for taxRegistrationId 'vat-lv'.", unknownCode.getMessage());

    IllegalArgumentException mismatchedApplicationKind =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TaxPostingResolution.resolve(
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-07"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-expense")),
                        null),
                    lookupStore(registration())));
    assertEquals(
        "Sale taxSelection must resolve to applicationKind OUTPUT_SALE.",
        mismatchedApplicationKind.getMessage());

    IllegalArgumentException expenseOutputTax =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TaxPostingResolution.resolve(
                    new BookkeepingEntry.Expense(
                        LocalDate.parse("2026-04-07"),
                        new AccountCode("5000"),
                        new AccountCode("1000"),
                        new MonetaryAmount("EUR", "12100"),
                        null,
                        new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                        null),
                    lookupStore(registration())));
    assertEquals(
        "Expense taxSelection cannot resolve to applicationKind OUTPUT_SALE.",
        expenseOutputTax.getMessage());
  }

  @Test
  void resolve_roundsHalfUpWhenTaxMinorUnitsReachHalf() {
    BookkeepingEntry.Sale resolved =
        assertInstanceOf(
            BookkeepingEntry.Sale.class,
            TaxPostingResolution.resolve(
                new BookkeepingEntry.Sale(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "1"),
                    null,
                    new TaxSelection(REGISTRATION_ID, new TaxCode("half-up-sale")),
                    null),
                lookupStore(roundingRegistration())));

    AppliedTax appliedTax = Objects.requireNonNull(resolved.appliedTax());
    assertEquals("1", appliedTax.taxAmount().minorUnits());
    assertEquals("2", appliedTax.grossAmount().minorUnits());
  }

  @Test
  void resolve_preservesOwnedForeignExchangeFacts() {
    ForeignExchangeDetails foreignExchangeDetails =
        new ForeignExchangeDetails(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "10000"),
            new QuotedExchangeRate(
                new MonetaryAmount("USD", "10000"),
                new MonetaryAmount("EUR", "10000"),
                LocalDate.parse("2026-04-06"),
                "ecb-spot"),
            ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);

    BookkeepingEntry.Sale resolved =
        assertInstanceOf(
            BookkeepingEntry.Sale.class,
            TaxPostingResolution.resolve(
                new BookkeepingEntry.Sale(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "10000"),
                    foreignExchangeDetails,
                    new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                    null),
                lookupStore(registration())));

    assertEquals(foreignExchangeDetails, resolved.foreignExchangeDetails());
  }

  private static TaxRegistrationLookupStore lookupStore(DeclaredTaxRegistration registration) {
    return taxRegistrationId ->
        registration.taxRegistrationId().equals(taxRegistrationId)
            ? Optional.of(registration)
            : Optional.empty();
  }

  private static DeclaredTaxRegistration registration() {
    return new DeclaredTaxRegistration(
        REGISTRATION_ID,
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE),
            new TaxCodeDefinition(
                new TaxCode("vat-nonrecoverable-expense"),
                new TaxCodeName("VAT Nonrecoverable Expense"),
                new TaxRate(120_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE)),
        DECLARED_AT);
  }

  private static DeclaredTaxRegistration roundingRegistration() {
    return new DeclaredTaxRegistration(
        REGISTRATION_ID,
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("half-up-sale"),
                new TaxCodeName("Half Up Sale"),
                new TaxRate(500_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)),
        DECLARED_AT);
  }
}
