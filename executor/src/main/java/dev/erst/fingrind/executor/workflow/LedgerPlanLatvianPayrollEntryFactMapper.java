package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import java.util.List;
import java.util.Objects;

/** Workflow-fact projection owned by the Latvian monthly-payroll context. */
final class LedgerPlanLatvianPayrollEntryFactMapper {
  private LedgerPlanLatvianPayrollEntryFactMapper() {}

  static void append(
      List<BookWorkflowFact> facts, LatvianPayrollBookkeepingEntryVariants payrollEntry) {
    switch (payrollEntry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll -> {
        var calculation =
            Objects.requireNonNull(payroll.resolvedCalculation(), "resolvedCalculation");
        facts.add(
            BookWorkflowFact.group(
                "latvianMonthlyPayroll",
                List.of(
                    BookWorkflowFact.text("payrollRunId", payroll.payrollRunId().value()),
                    BookWorkflowFact.text("employeeReference", payroll.employeeReference().value()),
                    BookWorkflowFact.text("payrollMonth", payroll.payrollMonth().wireValue()),
                    BookWorkflowFact.flag(
                        "taxBookHeldAtEmployer",
                        payroll.withholdingProfile().taxBookHeldAtEmployer()),
                    BookWorkflowFact.count(
                        "dependantCount", payroll.withholdingProfile().dependantCount()),
                    BookWorkflowFact.text(
                        "wageExpenseAccountCode", payroll.wageExpenseAccountCode().value()),
                    BookWorkflowFact.text(
                        "employerSocialContributionExpenseAccountCode",
                        payroll.employerSocialContributionExpenseAccountCode().value()),
                    BookWorkflowFact.text(
                        "netWagesPayableAccountCode", payroll.netWagesPayableAccountCode().value()),
                    BookWorkflowFact.text(
                        "employeeSocialContributionPayableAccountCode",
                        payroll.employeeSocialContributionPayableAccountCode().value()),
                    BookWorkflowFact.text(
                        "employerSocialContributionPayableAccountCode",
                        payroll.employerSocialContributionPayableAccountCode().value()),
                    BookWorkflowFact.text(
                        "personalIncomeTaxPayableAccountCode",
                        payroll.personalIncomeTaxPayableAccountCode().value()),
                    BookWorkflowFact.money("grossWages", payroll.grossWages()),
                    BookWorkflowFact.money(
                        "employeeSocialContribution",
                        MonetaryAmount.of(calculation.employeeSocialContribution())),
                    BookWorkflowFact.money(
                        "employerSocialContribution",
                        MonetaryAmount.of(calculation.employerSocialContribution())),
                    BookWorkflowFact.money(
                        "monthlyNonTaxableMinimum",
                        MonetaryAmount.of(calculation.monthlyNonTaxableMinimum())),
                    BookWorkflowFact.money(
                        "personalIncomeTax", MonetaryAmount.of(calculation.personalIncomeTax())),
                    BookWorkflowFact.money(
                        "netWages", MonetaryAmount.of(calculation.netWages())))));
      }
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          appendSettlement(
              facts,
              "NET_WAGES",
              settlement.payrollRunId().value(),
              settlement.cashAccountCode().value(),
              Objects.requireNonNull(settlement.resolvedSettlement(), "resolvedSettlement"));
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          appendSettlement(
              facts,
              "STATE_REMITTANCE",
              settlement.payrollRunId().value(),
              settlement.cashAccountCode().value(),
              Objects.requireNonNull(settlement.resolvedSettlement(), "resolvedSettlement"));
    }
  }

  private static void appendSettlement(
      List<BookWorkflowFact> facts,
      String settlementKind,
      String payrollRunId,
      String cashAccountCode,
      ResolvedLatvianPayrollSettlement resolvedSettlement) {
    facts.add(
        BookWorkflowFact.group(
            "latvianPayrollSettlement",
            List.of(
                BookWorkflowFact.text("settlementKind", settlementKind),
                BookWorkflowFact.text("payrollRunId", payrollRunId),
                BookWorkflowFact.text("cashAccountCode", cashAccountCode),
                BookWorkflowFact.text(
                    "netWagesPayableAccountCode",
                    resolvedSettlement.netWagesPayableAccountCode().value()),
                BookWorkflowFact.text(
                    "employeeSocialContributionPayableAccountCode",
                    resolvedSettlement.employeeSocialContributionPayableAccountCode().value()),
                BookWorkflowFact.text(
                    "employerSocialContributionPayableAccountCode",
                    resolvedSettlement.employerSocialContributionPayableAccountCode().value()),
                BookWorkflowFact.text(
                    "personalIncomeTaxPayableAccountCode",
                    resolvedSettlement.personalIncomeTaxPayableAccountCode().value()),
                BookWorkflowFact.money(
                    "netWages", MonetaryAmount.of(resolvedSettlement.netWages())),
                BookWorkflowFact.money(
                    "employeeSocialContribution",
                    MonetaryAmount.of(resolvedSettlement.employeeSocialContribution())),
                BookWorkflowFact.money(
                    "employerSocialContribution",
                    MonetaryAmount.of(resolvedSettlement.employerSocialContribution())),
                BookWorkflowFact.money(
                    "personalIncomeTax",
                    MonetaryAmount.of(resolvedSettlement.personalIncomeTax())))));
  }
}
