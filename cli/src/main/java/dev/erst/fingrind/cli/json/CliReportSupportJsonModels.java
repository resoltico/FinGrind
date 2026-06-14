package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared report-oriented JSON records emitted by the CLI transport layer. */
public interface CliReportSupportJsonModels extends CliBookQueryJsonModels {

  record ReportContextPayload(
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      String postingCoverage,
      @Nullable String comparativeReferenceEffectiveDateFrom,
      @Nullable String comparativeReferenceEffectiveDateTo) {
    public ReportContextPayload {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
      comparativeReferenceEffectiveDateFrom =
          requireOptionalText(
              comparativeReferenceEffectiveDateFrom, "comparativeReferenceEffectiveDateFrom");
      comparativeReferenceEffectiveDateTo =
          requireOptionalText(
              comparativeReferenceEffectiveDateTo, "comparativeReferenceEffectiveDateTo");
    }
  }
}
