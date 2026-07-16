package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Publishes tax obligation report results through the shared CLI output channel. */
@FunctionalInterface
interface CliTaxReportResultWriter extends CliReportOutputChannelOwner {
  /** Publishes one tax-obligation result. */
  default void writeTaxObligationResult(
      TaxObligationResult result,
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath,
      Instant generatedAt) {
    CliReportResultPublishingSupport.writeTaxObligation(
        reportOutputChannel(), result, outputMode, exportedArtifactPath, generatedAt);
  }
}
