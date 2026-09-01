package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for tax-period validation, tax extraction, and declaration guards. */
class TaxValidationSupportTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T00:00:00Z");

  @Test
  void matchesObligationPeriod_acceptsOwnedCadencesAndRejectsShapeMismatches() {
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.MONTHLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30")));
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-06-30")));
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-03-31")));
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-09-30")));
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-12-31")));
    assertTrue(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.ANNUAL,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31")));

    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.MONTHLY,
            LocalDate.parse("2026-04-02"),
            LocalDate.parse("2026-04-30")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.MONTHLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-05-31")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.MONTHLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2027-04-30")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.MONTHLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-29")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-07-31")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-04-02"),
            LocalDate.parse("2026-07-01")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.QUARTERLY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-06-29")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.ANNUAL,
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-12-31")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.ANNUAL,
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-12-31")));
    assertFalse(
        TaxValidationSupport.matchesObligationPeriod(
            TaxObligationFrequency.ANNUAL,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-30")));
  }

  @Test
  void appliedTax_extractsOnlyResolvedSaleAndExpenseFacts() {
    AppliedTax saleTax =
        appliedTax(
            TaxApplicationKind.OUTPUT_SALE,
            TaxInclusionMode.EXCLUSIVE,
            "vat-standard-sale",
            "10000",
            "2100",
            "12100",
            "2100");
    AppliedTax expenseTax =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            TaxInclusionMode.INCLUSIVE,
            "vat-standard-expense",
            "10000",
            "2100",
            "12100",
            "1300");

    assertNull(TaxValidationSupport.appliedTax(null));
    assertEquals(
        saleTax,
        TaxValidationSupport.appliedTax(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
                saleTax)));
    assertEquals(
        expenseTax,
        TaxValidationSupport.appliedTax(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "12100"),
                null,
                new TaxSelection(
                    new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-expense")),
                expenseTax)));
    assertEquals(
        saleTax,
        TaxValidationSupport.appliedTax(
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
                saleTax)));
    assertEquals(
        expenseTax,
        TaxValidationSupport.appliedTax(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "12100"),
                null,
                new TaxSelection(
                    new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-expense")),
                expenseTax)));
    assertNull(
        TaxValidationSupport.appliedTax(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "10000"),
                null)));
  }

  @Test
  void declarationViolations_coverUnknownInactiveAndNonPostableAccounts() {
    DeclareTaxRegistrationCommand command = validCommand();
    AccountLookupStore store =
        lookupStore(
            Map.of(
                command.payableAccountCode(), inactivePayableAccount(),
                command.recoverableAccountCode(), nonPostableRecoverableAccount()));

    List<TaxDefinitionViolation> violations =
        TaxValidationSupport.declarationViolations(command, store);

    assertEquals(
        List.of("inactive-account", "non-postable-account"),
        violations.stream().map(TaxDefinitionViolation::code).toList());
  }

  @Test
  void declarationViolations_acceptValidDefinitionsAndFlagUnknownAccounts() {
    DeclareTaxRegistrationCommand command = validCommand();

    assertTrue(
        TaxValidationSupport.declarationViolations(
                command,
                lookupStore(
                    Map.of(
                        command.payableAccountCode(), validPayableAccount(),
                        command.recoverableAccountCode(), validRecoverableAccount())))
            .isEmpty());
    assertEquals(
        List.of("unknown-account", "unknown-account"),
        TaxValidationSupport.declarationViolations(command, accountCode -> Optional.empty())
            .stream()
            .map(TaxDefinitionViolation::code)
            .toList());
  }

  @Test
  void declarationViolations_flagRecoverableAccountTypeAndClassificationMismatches() {
    DeclareTaxRegistrationCommand command = validCommand();

    List<TaxDefinitionViolation> violations =
        TaxValidationSupport.declarationViolations(
            command,
            lookupStore(
                Map.of(
                    command.payableAccountCode(), validPayableAccount(),
                    command.recoverableAccountCode(), wrongRecoverableAccount())));

    assertEquals(
        List.of(
            "account-type-mismatch",
            "financial-position-classification-mismatch",
            "cash-flow-asset-classification-mismatch"),
        violations.stream()
            .filter(violation -> "recoverableAccountCode".equals(violation.field()))
            .map(TaxDefinitionViolation::code)
            .toList());
  }

  @Test
  void declarationViolations_flagsDuplicateTaxCodesAndPayableAccountMismatches() {
    DeclareTaxRegistrationCommand command = duplicateTaxCodeCommand();

    List<TaxDefinitionViolation> violations =
        TaxValidationSupport.declarationViolations(
            command,
            lookupStore(
                Map.of(
                    command.payableAccountCode(), validRecoverableAccount(),
                    command.recoverableAccountCode(), validRecoverableAccount())));

    assertEquals(
        List.of(
            "duplicate-tax-code",
            "account-type-mismatch",
            "financial-position-classification-mismatch"),
        violations.stream().map(TaxDefinitionViolation::code).toList());
  }

  private static AccountLookupStore lookupStore(Map<AccountCode, RegisteredAccount> accounts) {
    return accountCode -> Optional.ofNullable(accounts.get(accountCode));
  }

  private static DeclareTaxRegistrationCommand validCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
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
                TaxApplicationKind.OUTPUT_SALE)));
  }

  private static DeclareTaxRegistrationCommand duplicateTaxCodeCommand() {
    DeclareTaxRegistrationCommand command = validCommand();
    TaxCodeDefinition taxCodeDefinition = command.taxCodes().getFirst();
    return new DeclareTaxRegistrationCommand(
        command.taxRegistrationId(),
        command.taxRegistrationName(),
        command.jurisdiction(),
        command.registrationNumber(),
        command.payableAccountCode(),
        command.recoverableAccountCode(),
        command.obligationFrequency(),
        command.dueDaysAfterPeriodEnd(),
        List.of(taxCodeDefinition, taxCodeDefinition));
  }

  private static RegisteredAccount validPayableAccount() {
    return registeredAccount(
        new AccountCode("2100"),
        new AccountName("VAT Payable"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount inactivePayableAccount() {
    return registeredAccount(
        new AccountCode("2100"),
        new AccountName("Inactive VAT Payable"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        false,
        DECLARED_AT);
  }

  private static RegisteredAccount validRecoverableAccount() {
    return registeredAccount(
        new AccountCode("1300"),
        new AccountName("VAT Recoverable"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount nonPostableRecoverableAccount() {
    return registeredAccount(
        new AccountCode("1300"),
        new AccountName("Recoverable Header"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.HEADER,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount wrongRecoverableAccount() {
    return registeredAccount(
        new AccountCode("1300"),
        new AccountName("Wrong Recoverable"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        true,
        DECLARED_AT);
  }

  private static AppliedTax appliedTax(
      TaxApplicationKind applicationKind,
      TaxInclusionMode inclusionMode,
      String taxCode,
      String taxableMinorUnits,
      String taxMinorUnits,
      String grossMinorUnits,
      String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT"),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableMinorUnits),
        new MonetaryAmount("EUR", taxMinorUnits),
        new MonetaryAmount("EUR", grossMinorUnits),
        new AccountCode(taxAccountCode));
  }
}
