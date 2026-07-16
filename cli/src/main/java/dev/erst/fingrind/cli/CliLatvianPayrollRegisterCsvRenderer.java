package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliLatvianPayrollReportJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders the Latvian payroll register as one tabular row per retained settlement lineage item. */
final class CliLatvianPayrollRegisterCsvRenderer {
  private static final List<String> HEADERS =
      List.of(
          "exportFamily",
          "payrollRunId",
          "employeeReference",
          "payrollMonth",
          "originPostingId",
          "effectiveDate",
          "runStatus",
          "runReversalPostingId",
          "grossWagesCurrencyCode",
          "grossWagesMinorUnits",
          "employeeSocialContributionCurrencyCode",
          "employeeSocialContributionMinorUnits",
          "employerSocialContributionCurrencyCode",
          "employerSocialContributionMinorUnits",
          "nonTaxableMinimumCurrencyCode",
          "nonTaxableMinimumMinorUnits",
          "personalIncomeTaxCurrencyCode",
          "personalIncomeTaxMinorUnits",
          "netWagesCurrencyCode",
          "netWagesMinorUnits",
          "totalEmployerCostCurrencyCode",
          "totalEmployerCostMinorUnits",
          "stateRemittanceCurrencyCode",
          "stateRemittanceMinorUnits",
          "settlementKind",
          "settlementPostingId",
          "settlementEffectiveDate",
          "settlementStatus",
          "settlementReversalPostingId",
          "message");

  private CliLatvianPayrollRegisterCsvRenderer() {}

  static String render(CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload report) {
    List<List<String>> rows = new ArrayList<>();
    if (report.rows().isEmpty()) {
      rows.add(emptyScopeRow(report.family()));
    } else {
      for (CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterRowPayload row : report.rows()) {
        if (row.settlements().isEmpty()) {
          rows.add(row(report.family(), row, null));
        } else {
          row.settlements().forEach(settlement -> rows.add(row(report.family(), row, settlement)));
        }
      }
    }
    return CliTextFormat.renderCsv(HEADERS, rows);
  }

  private static List<String> emptyScopeRow(String family) {
    List<String> row = new ArrayList<>(HEADERS.size());
    row.add(family);
    while (row.size() < HEADERS.size() - 1) {
      row.add("");
    }
    row.add("No payroll runs matched the selected scope.");
    return List.copyOf(row);
  }

  private static List<String> row(
      String family,
      CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterRowPayload row,
      CliLatvianPayrollReportJsonModels.@Nullable LatvianPayrollSettlementStatusPayload
          settlement) {
    return List.of(
        family,
        row.payrollRunId(),
        row.employeeReference(),
        row.payrollMonth(),
        row.originPostingId(),
        row.effectiveDate(),
        row.runStatus(),
        nullToEmpty(row.runReversalPostingId()),
        row.grossWages().currencyCode(),
        row.grossWages().minorUnits(),
        row.employeeSocialContribution().currencyCode(),
        row.employeeSocialContribution().minorUnits(),
        row.employerSocialContribution().currencyCode(),
        row.employerSocialContribution().minorUnits(),
        row.nonTaxableMinimum().currencyCode(),
        row.nonTaxableMinimum().minorUnits(),
        row.personalIncomeTax().currencyCode(),
        row.personalIncomeTax().minorUnits(),
        row.netWages().currencyCode(),
        row.netWages().minorUnits(),
        row.totalEmployerCost().currencyCode(),
        row.totalEmployerCost().minorUnits(),
        row.stateRemittance().currencyCode(),
        row.stateRemittance().minorUnits(),
        settlement == null ? "" : settlement.settlementKind(),
        settlement == null ? "" : settlement.postingId(),
        settlement == null ? "" : settlement.effectiveDate(),
        settlement == null ? "unsettled" : settlement.status(),
        settlement == null ? "" : nullToEmpty(settlement.reversalPostingId()),
        "");
  }

  private static String nullToEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
