package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
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
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures pre-tax inventory admission failures remain deterministic resolution outcomes. */
class PostEntryResolutionSupportTest {
  @Test
  void resolve_returnsEntrySemanticsRejectionWhenExclusiveTaxCompositionExceedsMoneyRange() {
    TaxRegistrationId registrationId = new TaxRegistrationId("vat-lv");
    var outcome =
        PostEntryResolutionSupport.resolve(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", Long.toString(Long.MAX_VALUE)),
                null,
                null,
                null,
                new TaxSelection(registrationId, new TaxCode("vat-standard-sale")),
                null),
            PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble.withTaxRegistrations(
                PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(),
                Map.of(),
                Map.of(
                    registrationId,
                    new DeclaredTaxRegistration(
                        registrationId,
                        new TaxRegistrationName("Latvia VAT"),
                        new TaxJurisdiction("LV"),
                        null,
                        new AccountCode("2100"),
                        new AccountCode("1300"),
                        TaxObligationFrequency.MONTHLY,
                        20,
                        java.util.List.of(
                            new TaxCodeDefinition(
                                new TaxCode("vat-standard-sale"),
                                new TaxCodeName("VAT Standard Sale"),
                                new TaxRate(210_000),
                                TaxInclusionMode.EXCLUSIVE,
                                TaxApplicationKind.OUTPUT_SALE)),
                        java.time.Instant.parse("2026-04-01T00:00:00Z")))));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());

    assertEquals("tax-composition-money-range-exceeded", rejection.violations().getFirst().code());
    assertEquals("amount", rejection.violations().getFirst().field());
  }

  @Test
  void resolve_returnsQuantityAdmissionRejectionBeforeTaxLookup() {
    var outcome =
        PostEntryResolutionSupport.resolve(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("0.5"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard")),
                null),
            new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
                Map.of(
                    new AccountCode("1400"),
                    inventoryAssetAccount("1400"),
                    new AccountCode("1000"),
                    account("1000", AccountType.ASSET))));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());

    assertEquals(
        "inventory-quantity-incompatible-with-unit-of-measure",
        rejection.violations().getFirst().code());
  }

  @Test
  void resolve_returnsAccrualCutoffAdmissionRejectionBeforeJournalCompletion() {
    var outcome =
        PostEntryResolutionSupport.resolve(
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                LocalDate.parse("2026-04-07"),
                new AccrualCutoffId("missing-cutoff"),
                new MonetaryAmount("EUR", "1000"),
                null),
            new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
                PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(), Map.of()));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());

    assertEquals("accrual-cutoff-not-found", rejection.violations().getFirst().code());
  }

  @Test
  void resolve_returnsLatvianPayrollProfileRejectionBeforeJournalCompletion() {
    BookIdentity eurBook = ExecutorAccountingTestSupport.bookIdentity();
    BookIdentity usdBook =
        new BookIdentity(
            eurBook.entityProfile(),
            eurBook.bookDoctrine(),
            CurrencyUnit.of("USD"),
            eurBook.fiscalYearStart(),
            java.time.LocalDate.parse("2026-01-01"));
    var outcome =
        PostEntryResolutionSupport.resolve(
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                LocalDate.parse("2026-07-31"),
                new LatvianPayrollRunId("payroll-2026-07-employee-1"),
                new LatvianPayrollEmployeeReference("employee-1"),
                new LatvianPayrollMonth(YearMonth.of(2026, 7)),
                dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
                    .taxBookWithNoDependantsFor2026(),
                new AccountCode("5000"),
                new AccountCode("5010"),
                new AccountCode("2200"),
                new AccountCode("2210"),
                new AccountCode("2220"),
                new AccountCode("2230"),
                new MonetaryAmount("EUR", "200000"),
                null),
            new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
                usdBook, Map.of()));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());

    assertEquals("latvian-payroll-requires-eur-book", rejection.violations().getFirst().code());
  }
}
