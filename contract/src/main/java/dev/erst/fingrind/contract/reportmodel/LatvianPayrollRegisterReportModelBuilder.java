package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollSettlementStatus;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the shared report model for immutable Latvian payroll calculations and settlements. */
public final class LatvianPayrollRegisterReportModelBuilder
    implements ReportModelBuilder<LatvianPayrollRegisterReport> {
  private static final String REPORT_FAMILY = OperationId.LATVIAN_PAYROLL_REGISTER.wireName();
  private static final List<String> CSV_HEADERS =
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

  /** Shared reusable builder instance. */
  public static final LatvianPayrollRegisterReportModelBuilder INSTANCE =
      new LatvianPayrollRegisterReportModelBuilder();

  private LatvianPayrollRegisterReportModelBuilder() {}

  @Override
  public ReportModel build(LatvianPayrollRegisterReport report) {
    return buildModel(report);
  }

  /** Builds one Latvian payroll-register report model. */
  public static ReportModel buildModel(LatvianPayrollRegisterReport report) {
    return new ReportModel(
        REPORT_FAMILY,
        ReportModelSupport.reportTitle(OperationId.LATVIAN_PAYROLL_REGISTER),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            null,
            null,
            null,
            null,
            EffectiveDateRange.unbounded(),
            List.of(
                new ReportVerdict(
                    "Register truth",
                    "Immutable payroll calculations and complete settlement posting lineage"))),
        List.of(new ReportVerdict("Payroll runs", Integer.toString(report.rows().size()))),
        List.of(
            runSection(report),
            calculationSection(report),
            totalsSection(report),
            settlementSection(report)),
        csvProjection(report));
  }

  private static ReportSection runSection(LatvianPayrollRegisterReport report) {
    return ReportModelSupport.section(
        "latvian-payroll-runs",
        "Latvian payroll runs",
        report.rows().isEmpty()
            ? List.of(new ReportVerdict("Outcome", ReportModelNarrative.noMatches("payroll runs")))
            : List.of(),
        List.of(
            ReportModelSupport.leftColumn("payrollRunId", "Payroll run"),
            ReportModelSupport.leftColumn("employeeReference", "Employee reference"),
            ReportModelSupport.leftColumn("payrollMonth", "Payroll month"),
            ReportModelSupport.leftColumn("effectiveDate", "Run date"),
            ReportModelSupport.leftColumn("runStatus", "Run status")),
        report.rows().stream().map(LatvianPayrollRegisterReportModelBuilder::runRow).toList(),
        List.of());
  }

  private static ReportSection calculationSection(LatvianPayrollRegisterReport report) {
    return ReportModelSupport.section(
        "latvian-payroll-calculations",
        "Payroll calculation components",
        List.of(),
        List.of(
            ReportModelSupport.leftColumn("payrollRunId", "Payroll run"),
            ReportModelSupport.rightColumn("grossWages", "Gross wages"),
            ReportModelSupport.rightColumn("employeeSocialContribution", "Employee social"),
            ReportModelSupport.rightColumn("employerSocialContribution", "Employer social")),
        report.rows().stream()
            .map(LatvianPayrollRegisterReportModelBuilder::calculationRow)
            .toList(),
        List.of());
  }

  private static ReportSection totalsSection(LatvianPayrollRegisterReport report) {
    return ReportModelSupport.section(
        "latvian-payroll-totals",
        "Payroll withholding and totals",
        List.of(),
        List.of(
            ReportModelSupport.leftColumn("payrollRunId", "Payroll run"),
            ReportModelSupport.rightColumn("nonTaxableMinimum", "Non-taxable minimum"),
            ReportModelSupport.rightColumn("personalIncomeTax", "Personal income tax"),
            ReportModelSupport.rightColumn("netWages", "Net wages"),
            ReportModelSupport.rightColumn("stateRemittance", "State remittance"),
            ReportModelSupport.rightColumn("totalEmployerCost", "Employer cost")),
        report.rows().stream().map(LatvianPayrollRegisterReportModelBuilder::totalsRow).toList(),
        List.of());
  }

  private static ReportSection settlementSection(LatvianPayrollRegisterReport report) {
    return ReportModelSupport.section(
        "latvian-payroll-settlements",
        "Payroll settlement lineage",
        List.of(),
        List.of(
            ReportModelSupport.leftColumn("payrollRunId", "Payroll run"),
            ReportModelSupport.leftColumn("settlementKind", "Settlement kind"),
            ReportModelSupport.leftColumn("postingId", "Posting"),
            ReportModelSupport.leftColumn("effectiveDate", "Settlement date"),
            ReportModelSupport.leftColumn("status", "Settlement status")),
        report.rows().stream().flatMap(row -> settlementRows(row).stream()).toList(),
        List.of());
  }

  private static ReportRow runRow(LatvianPayrollRegisterRow row) {
    return ReportModelSupport.row(
        row.payrollRunId().value() + ":run",
        row.payrollRunId().value(),
        row.employeeReference().value(),
        row.payrollMonth().wireValue(),
        row.effectiveDate().toString(),
        row.active() ? "Active" : "Reversed");
  }

  private static ReportRow calculationRow(LatvianPayrollRegisterRow row) {
    return ReportModelSupport.row(
        row.payrollRunId().value() + ":calculation",
        row.payrollRunId().value(),
        ReportModelDisplay.displayAmount(row.grossWages()),
        ReportModelDisplay.displayAmount(row.employeeSocialContribution()),
        ReportModelDisplay.displayAmount(row.employerSocialContribution()));
  }

  private static ReportRow totalsRow(LatvianPayrollRegisterRow row) {
    return ReportModelSupport.row(
        row.payrollRunId().value() + ":totals",
        row.payrollRunId().value(),
        ReportModelDisplay.displayAmount(row.nonTaxableMinimum()),
        ReportModelDisplay.displayAmount(row.personalIncomeTax()),
        ReportModelDisplay.displayAmount(row.netWages()),
        ReportModelDisplay.displayAmount(row.stateRemittance()),
        ReportModelDisplay.displayAmount(row.totalEmployerCost()));
  }

  private static List<ReportRow> settlementRows(LatvianPayrollRegisterRow row) {
    if (row.settlements().isEmpty()) {
      return List.of(
          ReportModelSupport.row(
              row.payrollRunId().value() + ":unsettled",
              row.payrollRunId().value(),
              "Not settled",
              "",
              "",
              "Unsettled"));
    }
    return row.settlements().stream()
        .map(
            settlement ->
                ReportModelSupport.row(
                    row.payrollRunId().value() + ":" + settlement.postingId().value(),
                    row.payrollRunId().value(),
                    settlement.settlementKind().wireValue(),
                    settlement.postingId().value(),
                    settlement.effectiveDate().toString(),
                    settlement.active() ? "Active" : "Reversed"))
        .toList();
  }

  private static ReportCsvProjection csvProjection(LatvianPayrollRegisterReport report) {
    if (report.rows().isEmpty()) {
      return new ReportCsvProjection(
          CSV_HEADERS,
          List.of(
              List.of(
                  REPORT_FAMILY,
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  ReportModelNarrative.noMatches("payroll runs"))));
    }
    List<List<String>> rows = new ArrayList<>();
    report.rows().forEach(row -> rows.addAll(csvRows(row)));
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static List<List<String>> csvRows(LatvianPayrollRegisterRow row) {
    if (row.settlements().isEmpty()) {
      return List.of(csvRow(row, null));
    }
    return row.settlements().stream().map(settlement -> csvRow(row, settlement)).toList();
  }

  private static List<String> csvRow(
      LatvianPayrollRegisterRow row, @Nullable LatvianPayrollSettlementStatus settlement) {
    return List.of(
        REPORT_FAMILY,
        row.payrollRunId().value(),
        row.employeeReference().value(),
        row.payrollMonth().wireValue(),
        row.originPostingId().value(),
        row.effectiveDate().toString(),
        row.active() ? "active" : "reversed",
        row.reversalPostingId().map(PostingId::value).orElse(""),
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
        settlement == null ? "" : settlement.settlementKind().wireValue(),
        settlement == null ? "" : settlement.postingId().value(),
        settlement == null ? "" : settlement.effectiveDate().toString(),
        settlement == null ? "unsettled" : settlement.active() ? "active" : "reversed",
        settlement == null ? "" : settlement.reversalPostingId().map(PostingId::value).orElse(""),
        "");
  }
}
