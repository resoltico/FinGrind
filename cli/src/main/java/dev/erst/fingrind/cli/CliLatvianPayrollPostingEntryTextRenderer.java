package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import java.util.List;

/** Appends Latvian payroll facts to a posting-entry text detail view. */
final class CliLatvianPayrollPostingEntryTextRenderer {
  private CliLatvianPayrollPostingEntryTextRenderer() {}

  static void appendRows(List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.latvianMonthlyPayroll() != null) {
      appendMonthlyPayrollRows(summaryRows, entry.latvianMonthlyPayroll());
    }
    if (entry.latvianPayrollSettlement() != null) {
      appendSettlementRows(summaryRows, entry.latvianPayrollSettlement());
    }
  }

  private static void appendMonthlyPayrollRows(
      List<List<String>> summaryRows, CliPostingEntryPayload.LatvianMonthlyPayrollPayload payroll) {
    summaryRows.add(List.of("Payroll run id", payroll.payrollRunId()));
    summaryRows.add(List.of("Employee reference", payroll.employeeReference()));
    summaryRows.add(List.of("Payroll month", payroll.payrollMonth()));
    summaryRows.add(
        List.of(
            "Payroll tax book held at employer", payroll.taxBookHeldAtEmployer() ? "Yes" : "No"));
    summaryRows.add(
        List.of("Eligible dependant count", Integer.toString(payroll.dependantCount())));
    summaryRows.add(List.of("Wage expense account", payroll.wageExpenseAccountCode()));
    summaryRows.add(
        List.of(
            "Employer social-contribution expense account",
            payroll.employerSocialContributionExpenseAccountCode()));
    summaryRows.add(List.of("Net-wages payable account", payroll.netWagesPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Employee social-contribution payable account",
            payroll.employeeSocialContributionPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Employer social-contribution payable account",
            payroll.employerSocialContributionPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Personal-income-tax payable account", payroll.personalIncomeTaxPayableAccountCode()));
    summaryRows.add(
        List.of("Gross wages", CliTextFormat.displayMoney(payroll.grossWages().toMoney())));
    if (payroll.resolvedCalculation() == null) {
      return;
    }
    var calculation = payroll.resolvedCalculation();
    summaryRows.add(
        List.of(
            "Resolved employee social contribution",
            CliTextFormat.displayMoney(calculation.employeeSocialContribution().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved employer social contribution",
            CliTextFormat.displayMoney(calculation.employerSocialContribution().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved monthly non-taxable minimum",
            CliTextFormat.displayMoney(calculation.monthlyNonTaxableMinimum().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved personal income tax",
            CliTextFormat.displayMoney(calculation.personalIncomeTax().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved net wages", CliTextFormat.displayMoney(calculation.netWages().toMoney())));
  }

  private static void appendSettlementRows(
      List<List<String>> summaryRows,
      CliPostingEntryPayload.LatvianPayrollSettlementPayload settlement) {
    summaryRows.add(
        List.of("Payroll settlement kind", CliTextDisplay.wireLabel(settlement.settlementKind())));
    summaryRows.add(List.of("Payroll run id", settlement.payrollRunId()));
    if (settlement.resolvedSettlement() == null) {
      return;
    }
    var resolvedSettlement = settlement.resolvedSettlement();
    summaryRows.add(
        List.of(
            "Resolved net-wages payable account", resolvedSettlement.netWagesPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Resolved employee social-contribution payable account",
            resolvedSettlement.employeeSocialContributionPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Resolved employer social-contribution payable account",
            resolvedSettlement.employerSocialContributionPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Resolved personal-income-tax payable account",
            resolvedSettlement.personalIncomeTaxPayableAccountCode()));
    summaryRows.add(
        List.of(
            "Resolved net wages",
            CliTextFormat.displayMoney(resolvedSettlement.netWages().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved employee social contribution",
            CliTextFormat.displayMoney(resolvedSettlement.employeeSocialContribution().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved employer social contribution",
            CliTextFormat.displayMoney(resolvedSettlement.employerSocialContribution().toMoney())));
    summaryRows.add(
        List.of(
            "Resolved personal income tax",
            CliTextFormat.displayMoney(resolvedSettlement.personalIncomeTax().toMoney())));
  }
}
