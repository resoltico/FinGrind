package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliFixedAssetReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects the durable fixed-asset register into its semantic machine payload. */
final class CliFixedAssetReportPayloadMapper {
  private CliFixedAssetReportPayloadMapper() {}

  static CliFixedAssetReportJsonModels.FixedAssetRegisterPayload register(
      FixedAssetRegisterReport report, Instant generatedAt) {
    return new CliFixedAssetReportJsonModels.FixedAssetRegisterPayload(
        CliReportPayloadMappingSupport.family(OperationId.FIXED_ASSET_REGISTER),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.FixedAssetRegisterResolvedQuery(
            CliReportPayloadMappingSupport.date(report.effectiveDateAsOf().orElse(null))),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream().map(CliFixedAssetReportPayloadMapper::row).toList());
  }

  private static CliFixedAssetReportJsonModels.FixedAssetRegisterRowPayload row(
      FixedAssetRegisterRow r) {
    return new CliFixedAssetReportJsonModels.FixedAssetRegisterRowPayload(
        r.fixedAssetId().value(),
        r.capitalizedOn().toString(),
        r.assetAccountCode().value(),
        r.accumulatedDepreciationAccountCode().value(),
        CliReportPayloadMappingSupport.money(r.cost()),
        CliReportPayloadMappingSupport.money(r.accumulatedDepreciation()),
        CliReportPayloadMappingSupport.money(r.carryingAmount()),
        r.depreciationSchedule().inServiceDate().toString(),
        r.depreciationSchedule().usefulLifeMonths(),
        CliReportPayloadMappingSupport.money(r.depreciationSchedule().residualValue()),
        r.depreciationPeriodsApplied(),
        CliReportPayloadMappingSupport.date(r.latestLifecycleEffectiveDate().orElse(null)),
        CliReportPayloadMappingSupport.date(r.disposedOn().orElse(null)));
  }
}
