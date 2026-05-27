package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.io.PrintStream;

/** Plan-side portion of the split test-only response writer compatibility chain. */
class CliResponseWriterPlanSupport extends CliResponseWriterReportSupport {
  CliResponseWriterPlanSupport(PrintStream outputStream) {
    super(outputStream);
  }

  void writeLedgerPlanResult(LedgerPlanResult result, PlanResultDetail resultDetail) {
    planWriter.writeLedgerPlanResult(result, resultDetail);
  }

  void writeJson(Object value) {
    outputChannel.writeJson(value);
  }
}
