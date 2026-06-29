package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct contract-model coverage for tax types and tax-bearing bookkeeping entries. */
class TaxContractTypesTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T10:15:30Z");

  @Test
  void taxTypes_preserveCanonicalPayloadsAndResultFamilies() {
    TaxCodeDefinition saleCode =
        new TaxCodeDefinition(
            new TaxCode("vat-standard-sale"),
            new TaxCodeName("VAT Standard Sale"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE);
    TaxCodeDefinition expenseCode =
        new TaxCodeDefinition(
            new TaxCode("vat-standard-expense"),
            new TaxCodeName("VAT Standard Expense"),
            new TaxRate(210_000),
            TaxInclusionMode.INCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
    DeclareTaxRegistrationCommand command =
        new DeclareTaxRegistrationCommand(
            new TaxRegistrationId("vat-lv"),
            new TaxRegistrationName("Latvia VAT"),
            new TaxJurisdiction("LV"),
            new TaxRegistrationNumber("LV40001234567"),
            new AccountCode("2100"),
            new AccountCode("1300"),
            TaxObligationFrequency.MONTHLY,
            20,
            List.of(saleCode, expenseCode));
    DeclaredTaxRegistration registration =
        new DeclaredTaxRegistration(
            command.taxRegistrationId(),
            command.taxRegistrationName(),
            command.jurisdiction(),
            command.registrationNumber(),
            command.payableAccountCode(),
            command.recoverableAccountCode(),
            command.obligationFrequency(),
            command.dueDaysAfterPeriodEnd(),
            command.taxCodes(),
            DECLARED_AT);
    TaxRegistrationPageCursor cursor = TaxRegistrationPageCursor.fromRegistration(registration);
    TaxRegistrationPage page =
        new TaxRegistrationPage(
            ContractFixtures.bookIdentity(), List.of(registration), 10, Optional.of(cursor));
    TaxObligationCodeSummary summary =
        new TaxObligationCodeSummary(
            saleCode.taxCode(),
            saleCode.taxCodeName(),
            saleCode.applicationKind(),
            2,
            new MonetaryAmount("EUR", "15000"),
            new MonetaryAmount("EUR", "3150"),
            new MonetaryAmount("EUR", "18150"));
    TaxObligationReport report =
        new TaxObligationReport(
            ContractFixtures.bookIdentity(),
            registration,
            new dev.erst.fingrind.core.ReportingPeriod(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            LocalDate.parse("2026-05-20"),
            List.of(summary),
            new MonetaryAmount("EUR", "3150"),
            new MonetaryAmount("EUR", "1050"),
            new MonetaryAmount("EUR", "0"),
            new MonetaryAmount("EUR", "2100"),
            new MonetaryAmount("EUR", "0"));

    DeclareTaxRegistrationResult.Declared declared =
        new DeclareTaxRegistrationResult.Declared(registration);
    DeclareTaxRegistrationResult.Updated updated =
        new DeclareTaxRegistrationResult.Updated(registration);
    DeclareTaxRegistrationResult.Unchanged unchanged =
        new DeclareTaxRegistrationResult.Unchanged(registration);
    new DeclareTaxRegistrationResult.Rejected(new TaxDeclarationRejection.BookNotInitialized());
    ListTaxRegistrationsResult.Listed listed = new ListTaxRegistrationsResult.Listed(page);
    ListTaxRegistrationsResult.Rejected listRejected =
        new ListTaxRegistrationsResult.Rejected(new TaxQueryRejection.BookNotInitialized());
    TaxObligationResult.Reported reported = new TaxObligationResult.Reported(report);
    TaxObligationResult.Rejected obligationRejected =
        new TaxObligationResult.Rejected(new TaxQueryRejection.BookNotInitialized());

    assertEquals("vat-lv", command.taxRegistrationId().value());
    assertNotNull(registration.registrationNumber());
    assertEquals("LV40001234567", registration.registrationNumber().value());
    assertNotEquals("vat-lv", cursor.wireValue());
    assertEquals(cursor, TaxRegistrationPageCursor.fromWireValue(cursor.wireValue()));
    assertEquals(
        new TaxRegistrationId("vat-lv"), page.nextCursor().orElseThrow().taxRegistrationId());
    assertEquals(2, summary.postingCount());
    assertSame(registration, declared.registration());
    assertSame(registration, updated.registration());
    assertSame(registration, unchanged.registration());
    assertEquals("listed", listed.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("rejected", listRejected.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("reported", reported.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals("rejected", obligationRejected.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "tax-query-book-not-initialized",
        TaxQueryRejection.wireCode(new TaxQueryRejection.BookNotInitialized()));
    assertEquals(
        "tax-obligation-period-mismatch",
        TaxQueryRejection.wireCode(
            new TaxQueryRejection.ObligationPeriodMismatch(
                TaxObligationFrequency.MONTHLY,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-29"))));
    assertEquals(
        "tax-book-not-initialized",
        TaxDeclarationRejection.wireCode(new TaxDeclarationRejection.BookNotInitialized()));
    assertEquals(
        "tax-definition-violations",
        TaxDeclarationRejection.wireCode(
            new TaxDeclarationRejection.DefinitionViolations(
                List.of(
                    new dev.erst.fingrind.contract.tax.TaxDefinitionViolation(
                        "unknown-tax-code", "taxCodes[0].taxCode", "Unknown tax code.")))));
    assertEquals(
        "unknown-tax-registration",
        TaxQueryRejection.wireCode(
            new TaxQueryRejection.UnknownTaxRegistration(new TaxRegistrationId("vat-lv"))));
    assertEquals(List.of("MONTHLY", "QUARTERLY", "ANNUAL"), TaxObligationFrequency.wireValues());
    assertEquals(TaxObligationFrequency.MONTHLY, TaxObligationFrequency.fromWireValue("MONTHLY"));
    assertEquals("MONTHLY", TaxObligationFrequency.MONTHLY.toString());
    assertEquals(List.of("EXCLUSIVE", "INCLUSIVE"), TaxInclusionMode.wireValues());
    assertEquals(TaxInclusionMode.EXCLUSIVE, TaxInclusionMode.fromWireValue("EXCLUSIVE"));
    assertEquals("EXCLUSIVE", TaxInclusionMode.EXCLUSIVE.toString());
    assertEquals(
        List.of("OUTPUT_SALE", "INPUT_EXPENSE_RECOVERABLE", "INPUT_EXPENSE_NONRECOVERABLE"),
        TaxApplicationKind.wireValues());
    assertEquals(
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
        TaxApplicationKind.fromWireValue("INPUT_EXPENSE_NONRECOVERABLE"));
    assertEquals(
        "INPUT_EXPENSE_NONRECOVERABLE", TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE.toString());
    assertEquals(2, TaxDeclarationRejection.descriptors().size());
    assertEquals(3, TaxQueryRejection.descriptors().size());
  }

  @Test
  void taxValueObjects_andQueries_rejectInvalidInputs() {
    assertEquals(120, TaxRegistrationId.maxLength());
    assertEquals("[a-z0-9]+(?:-[a-z0-9]+)*", TaxRegistrationId.pattern());
    assertEquals(120, TaxCode.maxLength());
    assertEquals("[a-z0-9]+(?:-[a-z0-9]+)*", TaxCode.pattern());
    assertEquals(120, TaxRegistrationNumber.maxLength());
    assertEquals(200, TaxRegistrationName.maxLength());
    assertEquals(120, TaxJurisdiction.maxLength());
    assertEquals("21.0000", new TaxRate(210_000).canonicalPercent());
    assertThrows(IllegalArgumentException.class, () -> new TaxRate(-1));
    assertThrows(IllegalArgumentException.class, () -> new TaxRate(1_000_001));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationId("VAT-LV"));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationId(" "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxRegistrationId("x".repeat(TaxRegistrationId.maxLength() + 1)));
    assertThrows(IllegalArgumentException.class, () -> new TaxJurisdiction(" "));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationName(" "));
    assertThrows(IllegalArgumentException.class, () -> new TaxCodeName(" "));
    assertThrows(IllegalArgumentException.class, () -> new TaxRegistrationNumber(" "));
    assertThrows(IllegalArgumentException.class, () -> new TaxCode(" "));
    assertThrows(IllegalArgumentException.class, () -> new TaxCode("VAT-STANDARD"));
    assertThrows(
        IllegalArgumentException.class, () -> new TaxCode("x".repeat(TaxCode.maxLength() + 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxRegistrationNumber("x".repeat(TaxRegistrationNumber.maxLength() + 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxRegistrationName("x".repeat(TaxRegistrationName.maxLength() + 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxJurisdiction("x".repeat(TaxJurisdiction.maxLength() + 1)));
    assertThrows(IllegalArgumentException.class, () -> new TaxCodeName("x".repeat(201)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclareTaxRegistrationCommand(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                20,
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclareTaxRegistrationCommand(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                400,
                List.of(validTaxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclareTaxRegistrationCommand(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                -1,
                List.of(validTaxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclaredTaxRegistration(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                20,
                List.of(),
                DECLARED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclaredTaxRegistration(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                -1,
                List.of(validTaxCode()),
                DECLARED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DeclaredTaxRegistration(
                new TaxRegistrationId("vat-lv"),
                new TaxRegistrationName("Latvia VAT"),
                new TaxJurisdiction("LV"),
                null,
                new AccountCode("2100"),
                new AccountCode("1300"),
                TaxObligationFrequency.MONTHLY,
                400,
                List.of(validTaxCode()),
                DECLARED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AppliedTax(
                new TaxRegistrationId("vat-lv"),
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE,
                new MonetaryAmount("EUR", "1000"),
                new MonetaryAmount("USD", "210"),
                new MonetaryAmount("EUR", "1210"),
                new AccountCode("2100")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AppliedTax(
                new TaxRegistrationId("vat-lv"),
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE,
                new MonetaryAmount("EUR", "1000"),
                new MonetaryAmount("EUR", "210"),
                new MonetaryAmount("USD", "1210"),
                new AccountCode("2100")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TaxObligationCodeSummary(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                TaxApplicationKind.OUTPUT_SALE,
                -1,
                new MonetaryAmount("EUR", "10000"),
                new MonetaryAmount("EUR", "2100"),
                new MonetaryAmount("EUR", "12100")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListTaxRegistrationsQuery(
                0, Optional.of(new TaxRegistrationPageCursor(new TaxRegistrationId("vat-lv")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListTaxRegistrationsQuery(
                201, Optional.of(new TaxRegistrationPageCursor(new TaxRegistrationId("vat-lv")))));
    assertEquals(Optional.empty(), new ListTaxRegistrationsQuery(1, Optional.empty()).cursor());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TaxRegistrationPage(
                ContractFixtures.bookIdentity(), List.of(), 0, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TaxObligationQuery(
                new TaxRegistrationId("vat-lv"),
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01")));
    assertEquals(
        LocalDate.parse("2026-04-30"),
        new TaxObligationQuery(
                new TaxRegistrationId("vat-lv"),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"))
            .effectiveDateTo());
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxDeclarationRejection.DefinitionViolations(List.of()));
    assertEquals(
        Optional.empty(),
        new TaxRegistrationPage(
                ContractFixtures.bookIdentity(), List.of(validRegistration()), 10, Optional.empty())
            .nextCursor());
    assertFalse(
        new TaxRegistrationPage(
                ContractFixtures.bookIdentity(), List.of(validRegistration()), 10, Optional.empty())
            .hasMore());
  }

  @Test
  void taxBearingBookkeepingEntries_publishCanonicalJournalShapes() {
    AppliedTax saleTax =
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-standard-sale"),
            new TaxCodeName("VAT Standard Sale"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "2100"),
            new MonetaryAmount("EUR", "12100"),
            new AccountCode("2100"));
    BookkeepingEntry.Sale sale =
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "10000"),
            null,
            new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
            saleTax);
    AppliedTax recoverableExpenseTax =
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-standard-expense"),
            new TaxCodeName("VAT Standard Expense"),
            new TaxRate(210_000),
            TaxInclusionMode.INCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "2100"),
            new MonetaryAmount("EUR", "12100"),
            new AccountCode("1300"));
    BookkeepingEntry.Expense recoverableExpense =
        new BookkeepingEntry.Expense(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "12100"),
            null,
            new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-expense")),
            recoverableExpenseTax);
    AppliedTax nonrecoverableExpenseTax =
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-nonrecoverable-expense"),
            new TaxCodeName("VAT Nonrecoverable Expense"),
            new TaxRate(120_000),
            TaxInclusionMode.INCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "1200"),
            new MonetaryAmount("EUR", "11200"),
            null);
    BookkeepingEntry.Expense nonrecoverableExpense =
        new BookkeepingEntry.Expense(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "11200"),
            null,
            new TaxSelection(
                new TaxRegistrationId("vat-lv"), new TaxCode("vat-nonrecoverable-expense")),
            nonrecoverableExpenseTax);

    assertEquals(BookkeepingEntryKind.SALE, sale.entryKind());
    assertEquals(3, sale.journalEntry().lines().size());
    assertEquals(
        Money.parse("EUR", "121.00"), sale.journalEntry().lines().getFirst().amount().money());
    assertEquals(3, recoverableExpense.journalEntry().lines().size());
    assertEquals(2, nonrecoverableExpense.journalEntry().lines().size());
    assertEquals(
        new AccountCode("5010"),
        nonrecoverableExpense.journalEntry().lines().getFirst().accountCode());
  }

  @Test
  void taxBearingBookkeepingEntries_rejectMismatchedResolvedTaxFacts() {
    TaxSelection selection =
        new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale"));
    AppliedTax mismatchedAppliedTax =
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-standard-expense"),
            new TaxCodeName("VAT Standard Expense"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "2100"),
            new MonetaryAmount("EUR", "12100"),
            new AccountCode("1300"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookkeepingEntry.Sale(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                mismatchedAppliedTax));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookkeepingEntry.Sale(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                selection,
                mismatchedAppliedTax));
    BookkeepingEntry.Sale unresolvedSale =
        new BookkeepingEntry.Sale(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "10000"),
            null,
            selection,
            null);
    assertThrows(IllegalStateException.class, unresolvedSale::journalEntry);
  }

  private static TaxCodeDefinition validTaxCode() {
    return new TaxCodeDefinition(
        new TaxCode("vat-standard-sale"),
        new TaxCodeName("VAT Standard Sale"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.OUTPUT_SALE);
  }

  private static DeclaredTaxRegistration validRegistration() {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(validTaxCode()),
        DECLARED_AT);
  }
}
