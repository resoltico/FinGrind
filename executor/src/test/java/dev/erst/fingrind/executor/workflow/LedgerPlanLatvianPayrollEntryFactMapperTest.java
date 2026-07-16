package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that every Latvian monthly-payroll entry exposes its durable workflow facts. */
class LedgerPlanLatvianPayrollEntryFactMapperTest {
  private static final LatvianPayrollMonth PAYROLL_MONTH =
      new LatvianPayrollMonth(YearMonth.of(2026, 7));
  private static final LocalDate EFFECTIVE_DATE = PAYROLL_MONTH.value().atEndOfMonth();
  private static final LatvianPayrollRunId PAYROLL_RUN_ID =
      new LatvianPayrollRunId("payroll-2026-07-employee-1");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode WAGES = new AccountCode("5000");
  private static final AccountCode EMPLOYER_SOCIAL = new AccountCode("5010");
  private static final AccountCode NET_WAGES = new AccountCode("2200");
  private static final AccountCode EMPLOYEE_SOCIAL = new AccountCode("2210");
  private static final AccountCode EMPLOYER_SOCIAL_PAYABLE = new AccountCode("2220");
  private static final AccountCode PERSONAL_INCOME_TAX = new AccountCode("2230");
  private static final LatvianMonthlyPayrollCalculation CALCULATION =
      LatvianMonthlyPayroll2026.calculate(PAYROLL_MONTH, Money.parse("EUR", "2000.00"));

  @Test
  void entryFacts_projectThePayrollRunAndBothExactSettlementObligations() {
    List<BookWorkflowFact> runFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
                EFFECTIVE_DATE,
                PAYROLL_RUN_ID,
                new LatvianPayrollEmployeeReference("employee-1"),
                PAYROLL_MONTH,
                WAGES,
                EMPLOYER_SOCIAL,
                NET_WAGES,
                EMPLOYEE_SOCIAL,
                EMPLOYER_SOCIAL_PAYABLE,
                PERSONAL_INCOME_TAX,
                MonetaryAmount.of(CALCULATION.grossWages()),
                CALCULATION));
    List<BookWorkflowFact> netWagesFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
                EFFECTIVE_DATE,
                PAYROLL_RUN_ID,
                CASH,
                resolved(LatvianPayrollSettlementKind.NET_WAGES)));
    List<BookWorkflowFact> stateRemittanceFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
                EFFECTIVE_DATE,
                PAYROLL_RUN_ID,
                CASH,
                resolved(LatvianPayrollSettlementKind.STATE_REMITTANCE)));

    assertText(
        group(runFacts, "latvianMonthlyPayroll").facts(), "payrollRunId", PAYROLL_RUN_ID.value());
    assertText(group(runFacts, "latvianMonthlyPayroll").facts(), "employeeReference", "employee-1");
    assertText(group(runFacts, "latvianMonthlyPayroll").facts(), "payrollMonth", "2026-07");
    assertEquals(
        BookWorkflowFact.money("netWages", MonetaryAmount.of(CALCULATION.netWages())),
        fact(group(runFacts, "latvianMonthlyPayroll").facts(), "netWages"));
    assertSettlementFacts(netWagesFacts, "NET_WAGES", CALCULATION.netWages());
    assertSettlementFacts(stateRemittanceFacts, "STATE_REMITTANCE", CALCULATION.stateRemittance());
  }

  private static ResolvedLatvianPayrollSettlement resolved(
      LatvianPayrollSettlementKind settlementKind) {
    return new ResolvedLatvianPayrollSettlement(
        settlementKind,
        PAYROLL_RUN_ID,
        CASH,
        NET_WAGES,
        EMPLOYEE_SOCIAL,
        EMPLOYER_SOCIAL_PAYABLE,
        PERSONAL_INCOME_TAX,
        CALCULATION.netWages(),
        CALCULATION.employeeSocialContribution(),
        CALCULATION.employerSocialContribution(),
        CALCULATION.personalIncomeTax());
  }

  private static void assertSettlementFacts(
      List<BookWorkflowFact> facts, String expectedKind, Money expectedAmount) {
    List<BookWorkflowFact> settlementFacts = group(facts, "latvianPayrollSettlement").facts();
    assertText(settlementFacts, "settlementKind", expectedKind);
    assertText(settlementFacts, "payrollRunId", PAYROLL_RUN_ID.value());
    assertText(settlementFacts, "cashAccountCode", CASH.value());
    if ("NET_WAGES".equals(expectedKind)) {
      assertEquals(
          BookWorkflowFact.money("netWages", MonetaryAmount.of(expectedAmount)),
          fact(settlementFacts, "netWages"));
      return;
    }
    assertEquals(
        BookWorkflowFact.money(
            "employeeSocialContribution",
            MonetaryAmount.of(CALCULATION.employeeSocialContribution())),
        fact(settlementFacts, "employeeSocialContribution"));
    assertEquals(
        BookWorkflowFact.money(
            "employerSocialContribution",
            MonetaryAmount.of(CALCULATION.employerSocialContribution())),
        fact(settlementFacts, "employerSocialContribution"));
    assertEquals(
        BookWorkflowFact.money(
            "personalIncomeTax", MonetaryAmount.of(CALCULATION.personalIncomeTax())),
        fact(settlementFacts, "personalIncomeTax"));
  }

  private static BookWorkflowFact.Group group(List<BookWorkflowFact> facts, String name) {
    return facts.stream()
        .filter(fact -> fact instanceof BookWorkflowFact.Group group && name.equals(group.name()))
        .map(BookWorkflowFact.Group.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static BookWorkflowFact fact(List<BookWorkflowFact> facts, String name) {
    return facts.stream()
        .filter(fact -> !(fact instanceof BookWorkflowFact.Group) && name.equals(fact.name()))
        .findFirst()
        .orElseThrow();
  }

  private static void assertText(List<BookWorkflowFact> facts, String name, String value) {
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && name.equals(text.name())
                        && value.equals(text.value())));
  }
}
