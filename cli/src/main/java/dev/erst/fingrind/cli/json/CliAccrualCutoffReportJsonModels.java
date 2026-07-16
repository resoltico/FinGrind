package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for the accrual cut-off schedule. */
public interface CliAccrualCutoffReportJsonModels {
  record AccrualCutoffSchedulePayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.AccrualCutoffScheduleResolvedQuery resolvedQuery,
      String generatedAt,
      List<AccrualCutoffScheduleRowPayload> rows)
      implements CliReportJsonModels.ReportPayload {
    public AccrualCutoffSchedulePayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record AccrualCutoffScheduleRowPayload(
      String accrualCutoffId,
      String kind,
      String originatedOn,
      String cutoffAccountCode,
      String recognitionAccountCode,
      CliReportValueJsonModels.MoneyPayload originalAmount,
      CliReportValueJsonModels.MoneyPayload appliedAmount,
      CliReportValueJsonModels.MoneyPayload remainingAmount,
      @Nullable String recognitionStartDate,
      @Nullable String recognitionEndDate,
      @Nullable String latestApplicationEffectiveDate) {
    public AccrualCutoffScheduleRowPayload {
      accrualCutoffId = requireText(accrualCutoffId, "accrualCutoffId");
      kind = requireText(kind, "kind");
      originatedOn = requireText(originatedOn, "originatedOn");
      cutoffAccountCode = requireText(cutoffAccountCode, "cutoffAccountCode");
      recognitionAccountCode = requireText(recognitionAccountCode, "recognitionAccountCode");
      Objects.requireNonNull(originalAmount, "originalAmount");
      Objects.requireNonNull(appliedAmount, "appliedAmount");
      Objects.requireNonNull(remainingAmount, "remainingAmount");
      recognitionStartDate = requireOptionalText(recognitionStartDate, "recognitionStartDate");
      recognitionEndDate = requireOptionalText(recognitionEndDate, "recognitionEndDate");
      latestApplicationEffectiveDate =
          requireOptionalText(latestApplicationEffectiveDate, "latestApplicationEffectiveDate");
    }
  }
}
