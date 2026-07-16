package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccrualCutoffReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects the durable accrual cut-off schedule into its semantic machine payload. */
final class CliAccrualCutoffReportPayloadMapper {
  private CliAccrualCutoffReportPayloadMapper() {}

  static CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload schedule(
      AccrualCutoffScheduleReport report, Instant generatedAt) {
    return new CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload(
        CliReportPayloadMappingSupport.family(OperationId.ACCRUAL_CUTOFF_SCHEDULE),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.AccrualCutoffScheduleResolvedQuery(
            CliReportPayloadMappingSupport.date(report.effectiveDateAsOf().orElse(null))),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream().map(CliAccrualCutoffReportPayloadMapper::row).toList());
  }

  private static CliAccrualCutoffReportJsonModels.AccrualCutoffScheduleRowPayload row(
      AccrualCutoffScheduleRow row) {
    return new CliAccrualCutoffReportJsonModels.AccrualCutoffScheduleRowPayload(
        row.accrualCutoffId().value(),
        row.kind().wireValue(),
        row.originatedOn().toString(),
        row.cutoffAccountCode().value(),
        row.recognitionAccountCode().value(),
        CliReportPayloadMappingSupport.money(row.originalAmount()),
        CliReportPayloadMappingSupport.money(row.appliedAmount()),
        CliReportPayloadMappingSupport.money(row.remainingAmount()),
        CliReportPayloadMappingSupport.date(row.recognitionStartDate().orElse(null)),
        CliReportPayloadMappingSupport.date(row.recognitionEndDate().orElse(null)),
        CliReportPayloadMappingSupport.date(row.latestApplicationEffectiveDate().orElse(null)));
  }
}
