package dev.erst.fingrind.cli;

import java.io.PrintStream;
import java.util.Objects;

/** Shared delegate assembly for the split test-only response writer compatibility chain. */
class CliResponseWriterBaseSupport {
  protected final CliOutputChannel outputChannel;
  protected final CliFailureResponseWriter failureWriter;
  protected final CliDiscoveryResponseWriter discoveryWriter;
  protected final CliMutationResponseWriter mutationWriter;
  protected final CliBookReadResponseWriter bookReadWriter;
  protected final CliReportResponseWriter reportWriter;
  protected final CliPlanResponseWriter planWriter;

  CliResponseWriterBaseSupport(PrintStream outputStream) {
    this.outputChannel = new CliOutputChannel(Objects.requireNonNull(outputStream, "outputStream"));
    this.failureWriter = new CliFailureResponseWriter(outputChannel);
    this.discoveryWriter = new CliDiscoveryResponseWriter(outputChannel);
    this.mutationWriter = new CliMutationResponseWriter(outputChannel);
    this.bookReadWriter = new CliBookReadResponseWriter(outputChannel);
    this.reportWriter = new CliReportResponseWriter(outputChannel);
    this.planWriter = new CliPlanResponseWriter(outputChannel);
  }
}
