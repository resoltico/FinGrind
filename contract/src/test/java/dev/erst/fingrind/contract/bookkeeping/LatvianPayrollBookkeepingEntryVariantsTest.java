package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Proves that Latvian payroll journals remain executor-resolved rather than caller-authored. */
class LatvianPayrollBookkeepingEntryVariantsTest {
  private static final LatvianPayrollRunId RUN_ID =
      new LatvianPayrollRunId("payroll-run-2026-07-employee-001");
  private static final LatvianPayrollWithholdingProfile WITHHOLDING_PROFILE =
      LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026();
  private static final AccountCode CASH = new AccountCode("cash");
  private static final AccountCode WAGE_EXPENSE = new AccountCode("wage-expense");
  private static final AccountCode EMPLOYER_SOCIAL_EXPENSE =
      new AccountCode("employer-social-expense");
  private static final AccountCode NET_WAGES_PAYABLE = new AccountCode("net-wages-payable");
  private static final AccountCode EMPLOYEE_SOCIAL_PAYABLE =
      new AccountCode("employee-social-payable");
  private static final AccountCode EMPLOYER_SOCIAL_PAYABLE =
      new AccountCode("employer-social-payable");
  private static final AccountCode PERSONAL_INCOME_TAX_PAYABLE =
      new AccountCode("personal-income-tax-payable");

  @Test
  void journalEntry_requiresExecutorResolvedPayrollFactsBeforeItCanDeriveAnyJournal() {
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll = monthlyPayroll(null);
    LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWageSettlement =
        new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
            LocalDate.parse("2026-07-31"), RUN_ID, CASH, null);
    LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance =
        new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
            LocalDate.parse("2026-08-05"), RUN_ID, CASH, null);

    assertThrows(IllegalStateException.class, monthlyPayroll::journalEntry);
    assertThrows(IllegalStateException.class, netWageSettlement::journalEntry);
    assertThrows(IllegalStateException.class, stateRemittance::journalEntry);
  }

  @Test
  void resolvedPayrollFactsDeriveTheExactMonthlyAndSettlementJournals() {
    var calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "2000.00"),
            WITHHOLDING_PROFILE);
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll =
        monthlyPayroll(calculation);
    ResolvedLatvianPayrollSettlement netWageSettlement =
        resolvedSettlement(LatvianPayrollSettlementKind.NET_WAGES, calculation);
    ResolvedLatvianPayrollSettlement stateRemittance =
        resolvedSettlement(LatvianPayrollSettlementKind.STATE_REMITTANCE, calculation);

    assertEquals(
        List.of(
            "wage-expense:DEBIT:2000.00",
            "employer-social-expense:DEBIT:471.80",
            "net-wages-payable:CREDIT:1473.80",
            "employee-social-payable:CREDIT:210.00",
            "employer-social-payable:CREDIT:471.80",
            "personal-income-tax-payable:CREDIT:316.20"),
        lines(monthlyPayroll.journalEntry().lines()));
    assertEquals(
        List.of("net-wages-payable:DEBIT:1473.80", "cash:CREDIT:1473.80"),
        lines(
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                    LocalDate.parse("2026-07-31"), RUN_ID, CASH, netWageSettlement)
                .journalEntry()
                .lines()));
    assertEquals(
        List.of(
            "employee-social-payable:DEBIT:210.00",
            "employer-social-payable:DEBIT:471.80",
            "personal-income-tax-payable:DEBIT:316.20",
            "cash:CREDIT:998.00"),
        lines(
            new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
                    LocalDate.parse("2026-08-05"), RUN_ID, CASH, stateRemittance)
                .journalEntry()
                .lines()));
  }

  @Test
  void typedPayrollVariantsPublishTheirEntryAndOriginKindsThroughTheCommonSurface() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "2000.00"),
            WITHHOLDING_PROFILE);
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthly = monthlyPayroll(calculation);
    LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWages =
        new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
            LocalDate.parse("2026-07-31"),
            RUN_ID,
            CASH,
            resolvedSettlement(LatvianPayrollSettlementKind.NET_WAGES, calculation));
    LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance =
        new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
            LocalDate.parse("2026-08-05"),
            RUN_ID,
            CASH,
            resolvedSettlement(LatvianPayrollSettlementKind.STATE_REMITTANCE, calculation));

    assertEquals(BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL, monthly.entryKind());
    assertEquals(PostingOriginKind.LATVIAN_MONTHLY_PAYROLL, monthly.postingOriginKind());
    assertEquals(BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT, netWages.entryKind());
    assertEquals(
        PostingOriginKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT, netWages.postingOriginKind());
    assertEquals(
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE, stateRemittance.entryKind());
    assertEquals(
        PostingOriginKind.LATVIAN_PAYROLL_STATE_REMITTANCE, stateRemittance.postingOriginKind());
  }

  @Test
  void payrollConstructionRejectsIncoherentCallerFactsBeforeJournalResolution() {
    LatvianMonthlyPayrollCalculation standardCalculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "2000.00"),
            WITHHOLDING_PROFILE);
    LatvianMonthlyPayrollCalculation lowerCalculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "100.00"),
            WITHHOLDING_PROFILE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            monthlyPayroll(
                LocalDate.parse("2026-07-30"),
                standardCalculation,
                standardCalculation.grossWages()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            monthlyPayroll(
                LocalDate.parse("2026-07-31"), lowerCalculation, standardCalculation.grossWages()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                LocalDate.parse("2026-07-31"),
                RUN_ID,
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-07"),
                new LatvianPayrollWithholdingProfile(true, 1),
                WAGE_EXPENSE,
                EMPLOYER_SOCIAL_EXPENSE,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                MonetaryAmount.of(standardCalculation.grossWages()),
                standardCalculation));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                LocalDate.parse("2026-07-31"),
                RUN_ID,
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-07"),
                WITHHOLDING_PROFILE,
                WAGE_EXPENSE,
                WAGE_EXPENSE,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                MonetaryAmount.of(standardCalculation.grossWages()),
                standardCalculation));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                LocalDate.parse("2026-07-31"),
                RUN_ID,
                CASH,
                resolvedSettlement(
                    LatvianPayrollSettlementKind.STATE_REMITTANCE, standardCalculation)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                LocalDate.parse("2026-07-31"),
                new LatvianPayrollRunId("different-payroll-run"),
                CASH,
                resolvedSettlement(LatvianPayrollSettlementKind.NET_WAGES, standardCalculation)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                LocalDate.parse("2026-07-31"),
                RUN_ID,
                new AccountCode("other-cash"),
                resolvedSettlement(LatvianPayrollSettlementKind.NET_WAGES, standardCalculation)));
  }

  @Test
  void monthlyPayrollOmitsZeroTaxLineInsteadOfPublishingAZeroJournalLine() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "100.00"),
            WITHHOLDING_PROFILE);

    assertEquals(
        List.of(
            "wage-expense:DEBIT:100.00",
            "employer-social-expense:DEBIT:23.59",
            "net-wages-payable:CREDIT:89.50",
            "employee-social-payable:CREDIT:10.50",
            "employer-social-payable:CREDIT:23.59"),
        lines(monthlyPayroll(calculation).journalEntry().lines()));
  }

  @Test
  void resolvedSettlementRequiresPositiveNetWagesAndOneCurrency() {
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(
            LatvianPayrollMonth.parse("2026-07"),
            Money.parse("EUR", "2000.00"),
            WITHHOLDING_PROFILE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedLatvianPayrollSettlement(
                LatvianPayrollSettlementKind.NET_WAGES,
                RUN_ID,
                CASH,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                calculation.netWages(),
                Money.parse("USD", "210.00"),
                calculation.employerSocialContribution(),
                calculation.personalIncomeTax()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedLatvianPayrollSettlement(
                LatvianPayrollSettlementKind.NET_WAGES,
                RUN_ID,
                CASH,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                calculation.netWages(),
                calculation.employeeSocialContribution(),
                Money.parse("USD", "471.80"),
                calculation.personalIncomeTax()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedLatvianPayrollSettlement(
                LatvianPayrollSettlementKind.NET_WAGES,
                RUN_ID,
                CASH,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                calculation.netWages(),
                calculation.employeeSocialContribution(),
                calculation.employerSocialContribution(),
                Money.parse("USD", "316.20")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedLatvianPayrollSettlement(
                LatvianPayrollSettlementKind.NET_WAGES,
                RUN_ID,
                CASH,
                NET_WAGES_PAYABLE,
                EMPLOYEE_SOCIAL_PAYABLE,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX_PAYABLE,
                Money.zero(calculation.netWages().currencyUnit()),
                calculation.employeeSocialContribution(),
                calculation.employerSocialContribution(),
                calculation.personalIncomeTax()));
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll(
      @Nullable LatvianMonthlyPayrollCalculation calculation) {
    return monthlyPayroll(
        LocalDate.parse("2026-07-31"),
        calculation,
        calculation == null ? Money.parse("EUR", "2000.00") : calculation.grossWages());
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll(
      LocalDate effectiveDate,
      @Nullable LatvianMonthlyPayrollCalculation calculation,
      Money grossWages) {
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        effectiveDate,
        RUN_ID,
        new LatvianPayrollEmployeeReference("employee-001"),
        LatvianPayrollMonth.parse("2026-07"),
        WITHHOLDING_PROFILE,
        WAGE_EXPENSE,
        EMPLOYER_SOCIAL_EXPENSE,
        NET_WAGES_PAYABLE,
        EMPLOYEE_SOCIAL_PAYABLE,
        EMPLOYER_SOCIAL_PAYABLE,
        PERSONAL_INCOME_TAX_PAYABLE,
        MonetaryAmount.of(grossWages),
        calculation);
  }

  private static ResolvedLatvianPayrollSettlement resolvedSettlement(
      LatvianPayrollSettlementKind settlementKind, LatvianMonthlyPayrollCalculation calculation) {
    return new ResolvedLatvianPayrollSettlement(
        settlementKind,
        RUN_ID,
        CASH,
        NET_WAGES_PAYABLE,
        EMPLOYEE_SOCIAL_PAYABLE,
        EMPLOYER_SOCIAL_PAYABLE,
        PERSONAL_INCOME_TAX_PAYABLE,
        calculation.netWages(),
        calculation.employeeSocialContribution(),
        calculation.employerSocialContribution(),
        calculation.personalIncomeTax());
  }

  private static List<String> lines(List<JournalLine> lines) {
    return lines.stream()
        .map(
            line ->
                line.accountCode().value()
                    + ":"
                    + line.side()
                    + ":"
                    + line.amount().money().canonicalDecimal())
        .toList();
  }
}
