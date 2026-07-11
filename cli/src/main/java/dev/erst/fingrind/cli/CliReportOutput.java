package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared report-presentation settings for one successful report command. */
record CliReportOutput(OutputMode outputMode, @Nullable Path pdfOutPath) {
  CliReportOutput {
    Objects.requireNonNull(outputMode, "outputMode");
  }
}
