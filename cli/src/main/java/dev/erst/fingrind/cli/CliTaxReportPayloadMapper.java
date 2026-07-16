package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliTaxReportJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import java.time.Instant;

/** Projects tax obligations into their semantic machine payload. */
final class CliTaxReportPayloadMapper {
  private CliTaxReportPayloadMapper() {}

  static CliTaxReportJsonModels.TaxObligationPayload taxObligation(
      TaxObligationReport report, Instant generatedAt) {
    return new CliTaxReportJsonModels.TaxObligationPayload(
        CliReportPayloadMappingSupport.family(OperationId.TAX_OBLIGATION),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.TaxObligationResolvedQuery(
            report.registration().taxRegistrationId().value(),
            report.reportingPeriod().effectiveDateFrom().toString(),
            report.reportingPeriod().effectiveDateTo().toString()),
        CliReportPayloadMappingSupport.instant(generatedAt),
        new CliTaxReportJsonModels.TaxRegistrationPayload(
            report.registration().taxRegistrationId().value(),
            report.registration().taxRegistrationName().value(),
            report.registration().jurisdiction().value(),
            report.registration().registrationNumber() == null
                ? null
                : report.registration().registrationNumber().value(),
            report.registration().obligationFrequency().name()),
        report.dueDate().toString(),
        report.codeSummaries().stream()
            .map(
                row ->
                    new CliTaxReportJsonModels.TaxObligationRowPayload(
                        row.taxCode().value(),
                        row.taxCodeName().value(),
                        row.applicationKind().name(),
                        row.postingCount(),
                        CliReportPayloadMappingSupport.money(row.taxableAmount()),
                        CliReportPayloadMappingSupport.money(row.taxAmount()),
                        CliReportPayloadMappingSupport.money(row.grossAmount())))
            .toList(),
        new CliTaxReportJsonModels.TaxObligationTotalsPayload(
            CliReportPayloadMappingSupport.money(report.outputTax()),
            CliReportPayloadMappingSupport.money(report.recoverableInputTax()),
            CliReportPayloadMappingSupport.money(report.nonrecoverableInputTax()),
            CliReportPayloadMappingSupport.money(report.netPayable()),
            CliReportPayloadMappingSupport.money(report.netReceivable())));
  }
}
