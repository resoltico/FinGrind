package dev.erst.fingrind.cli;

import java.util.Objects;

/** Output-channel owner composed from focused report-family publishing capabilities. */
final class CliReportResponseWriter
    implements CliBookkeepingReportResultWriter,
        CliStatementReportResultWriter,
        CliOperationalReportResultWriter,
        CliTaxReportResultWriter {
  private final CliOutputChannel outputChannel;

  CliReportResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  @Override
  public CliOutputChannel reportOutputChannel() {
    return outputChannel;
  }
}
