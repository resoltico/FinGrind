package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects Financing and Realized Foreign Exchange registers to semantic machine payloads. */
final class CliLifecycleContextReportPayloadMapper {
  private CliLifecycleContextReportPayloadMapper() {}

  static CliLifecycleContextReportJsonModels.FinancingRegisterPayload financing(
      FinancingRegisterReport report, Instant generatedAt) {
    return new CliLifecycleContextReportJsonModels.FinancingRegisterPayload(
        CliReportPayloadMappingSupport.family(OperationId.FINANCING_REGISTER),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.FinancingRegisterResolvedQuery(),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream().map(CliLifecycleContextReportPayloadMapper::financingRow).toList());
  }

  static CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterPayload
      realizedForeignExchange(RealizedForeignExchangeRegisterReport report, Instant generatedAt) {
    return new CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterPayload(
        CliReportPayloadMappingSupport.family(OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.RealizedForeignExchangeRegisterResolvedQuery(),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream()
            .map(CliLifecycleContextReportPayloadMapper::realizedForeignExchangeRow)
            .toList());
  }

  private static CliLifecycleContextReportJsonModels.FinancingRegisterRowPayload financingRow(
      FinancingRegisterRow row) {
    return new CliLifecycleContextReportJsonModels.FinancingRegisterRowPayload(
        row.financingArrangementId().value(),
        row.originatedOn().toString(),
        row.lifecycleHorizon().toString(),
        row.principalLiabilityAccountCode().value(),
        row.interestPayableAccountCode().value(),
        CliReportPayloadMappingSupport.money(row.originalPrincipal()),
        CliReportPayloadMappingSupport.money(row.principalRepaid()),
        CliReportPayloadMappingSupport.money(row.principalOutstanding()),
        CliReportPayloadMappingSupport.money(row.interestAccrued()),
        CliReportPayloadMappingSupport.money(row.interestPaid()),
        CliReportPayloadMappingSupport.money(row.interestOutstanding()));
  }

  private static CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterRowPayload
      realizedForeignExchangeRow(RealizedForeignExchangeRegisterRow row) {
    return new CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterRowPayload(
        row.foreignCurrencyObligationId().value(),
        row.originatedOn().toString(),
        row.lifecycleHorizon().toString(),
        row.receivableAccountCode().value(),
        CliReportPayloadMappingSupport.money(row.transactionAmount()),
        CliReportPayloadMappingSupport.money(row.functionalCarryingAmount()),
        CliReportPayloadMappingSupport.date(row.settledOn().orElse(null)),
        row.functionalSettlementAmount().map(CliReportPayloadMappingSupport::money).orElse(null),
        row.realizedGainOrLossAmount().map(CliReportPayloadMappingSupport::money).orElse(null),
        row.realizedGain().orElse(null));
  }
}
