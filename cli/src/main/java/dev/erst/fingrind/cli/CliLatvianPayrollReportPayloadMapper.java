package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliLatvianPayrollReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollSettlementStatus;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects the durable Latvian payroll register into its semantic machine payload. */
final class CliLatvianPayrollReportPayloadMapper {
  private CliLatvianPayrollReportPayloadMapper() {}

  static CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload register(
      LatvianPayrollRegisterReport report, Instant generatedAt) {
    return new CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload(
        CliReportPayloadMappingSupport.family(OperationId.LATVIAN_PAYROLL_REGISTER),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.LatvianPayrollRegisterResolvedQuery(),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream().map(CliLatvianPayrollReportPayloadMapper::row).toList());
  }

  private static CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterRowPayload row(
      LatvianPayrollRegisterRow row) {
    return new CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterRowPayload(
        row.payrollRunId().value(),
        row.employeeReference().value(),
        row.payrollMonth().wireValue(),
        row.originPostingId().value(),
        row.effectiveDate().toString(),
        row.active() ? "active" : "reversed",
        row.reversalPostingId().map(postingId -> postingId.value()).orElse(null),
        CliReportPayloadMappingSupport.money(row.grossWages()),
        CliReportPayloadMappingSupport.money(row.employeeSocialContribution()),
        CliReportPayloadMappingSupport.money(row.employerSocialContribution()),
        CliReportPayloadMappingSupport.money(row.nonTaxableMinimum()),
        CliReportPayloadMappingSupport.money(row.personalIncomeTax()),
        CliReportPayloadMappingSupport.money(row.netWages()),
        CliReportPayloadMappingSupport.money(row.totalEmployerCost()),
        CliReportPayloadMappingSupport.money(row.stateRemittance()),
        row.settlements().stream().map(CliLatvianPayrollReportPayloadMapper::settlement).toList());
  }

  private static CliLatvianPayrollReportJsonModels.LatvianPayrollSettlementStatusPayload settlement(
      LatvianPayrollSettlementStatus settlement) {
    return new CliLatvianPayrollReportJsonModels.LatvianPayrollSettlementStatusPayload(
        settlement.settlementKind().wireValue(),
        settlement.postingId().value(),
        settlement.effectiveDate().toString(),
        settlement.active() ? "active" : "reversed",
        settlement.reversalPostingId().map(postingId -> postingId.value()).orElse(null));
  }
}
