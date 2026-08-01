package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.io.PrintStream;

/** Focused test fixture for ledger-plan response projections. */
final class CliPlanResponseWriterFixture {
  private final CliPlanResponseWriter writer;

  CliPlanResponseWriterFixture(PrintStream outputStream) {
    writer = new CliPlanResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  CliPlanResponseWriterFixture(PrintStream outputStream, PrintStream diagnosticsStream) {
    writer =
        new CliPlanResponseWriter(
            CliTestOutputChannels.forStreams(outputStream, diagnosticsStream));
  }

  void writeLedgerPlanResult(
      LedgerPlanResult result, OutputMode outputMode, PlanResultDetail resultDetail) {
    writer.writeLedgerPlanResult(result, outputMode, resultDetail);
  }
}
