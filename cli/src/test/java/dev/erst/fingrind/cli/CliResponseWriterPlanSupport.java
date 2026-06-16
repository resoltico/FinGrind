package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.io.PrintStream;

/** Plan-side portion of the split test-only response writer compatibility chain. */
class CliResponseWriterPlanSupport extends CliResponseWriterReportSupport {
  CliResponseWriterPlanSupport(PrintStream outputStream) {
    super(outputStream);
  }

  CliResponseWriterPlanSupport(PrintStream outputStream, PrintStream diagnosticsStream) {
    super(outputStream, diagnosticsStream);
  }

  void writeLedgerPlanResult(
      LedgerPlanResult result, OutputMode outputMode, PlanResultDetail resultDetail) {
    planWriter.writeLedgerPlanResult(result, outputMode, resultDetail);
  }

  void writeJson(Object value) {
    outputChannel.writeJson(value);
  }
}
