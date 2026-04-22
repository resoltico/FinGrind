package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;

/** Executes posting and ledger-plan commands that consume request payloads. */
final class CliMutationCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;

  CliMutationCommandExecutor(
      CliRequestReader requestReader,
      CliResponseWriter responseWriter,
      CliBookWorkflow bookWorkflow) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
  }

  int runExecutePlanCommand(BookAccess bookAccess, Path requestFile) {
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.executePlan(bookAccess, plan),
        OutputMode.JSON,
        responseWriter::writeLedgerPlanResult,
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runPreflightEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.preflight(bookAccess, command),
        outputMode,
        result -> responseWriter.writePostEntryResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runPostEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.commit(bookAccess, command),
        outputMode,
        result -> responseWriter.writePostEntryResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }
}
