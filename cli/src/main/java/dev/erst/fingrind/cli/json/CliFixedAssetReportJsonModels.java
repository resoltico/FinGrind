package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for the fixed-asset register. */
public interface CliFixedAssetReportJsonModels {
  record FixedAssetRegisterPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.FixedAssetRegisterResolvedQuery resolvedQuery,
      String generatedAt,
      List<FixedAssetRegisterRowPayload> rows)
      implements CliReportJsonModels.ReportPayload {
    public FixedAssetRegisterPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record FixedAssetRegisterRowPayload(
      String fixedAssetId,
      String capitalizedOn,
      String assetAccountCode,
      String accumulatedDepreciationAccountCode,
      CliReportValueJsonModels.MoneyPayload cost,
      CliReportValueJsonModels.MoneyPayload accumulatedDepreciation,
      CliReportValueJsonModels.MoneyPayload carryingAmount,
      String inServiceDate,
      int usefulLifeMonths,
      CliReportValueJsonModels.MoneyPayload residualValue,
      int depreciationPeriodsApplied,
      @Nullable String latestLifecycleEffectiveDate,
      @Nullable String disposedOn) {
    public FixedAssetRegisterRowPayload {
      fixedAssetId = requireText(fixedAssetId, "fixedAssetId");
      capitalizedOn = requireText(capitalizedOn, "capitalizedOn");
      assetAccountCode = requireText(assetAccountCode, "assetAccountCode");
      accumulatedDepreciationAccountCode =
          requireText(accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      Objects.requireNonNull(cost, "cost");
      Objects.requireNonNull(accumulatedDepreciation, "accumulatedDepreciation");
      Objects.requireNonNull(carryingAmount, "carryingAmount");
      inServiceDate = requireText(inServiceDate, "inServiceDate");
      Objects.requireNonNull(residualValue, "residualValue");
      latestLifecycleEffectiveDate =
          requireOptionalText(latestLifecycleEffectiveDate, "latestLifecycleEffectiveDate");
      disposedOn = requireOptionalText(disposedOn, "disposedOn");
    }
  }
}
