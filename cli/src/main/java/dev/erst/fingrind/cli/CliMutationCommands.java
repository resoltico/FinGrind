package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;

/** Mutation CLI commands that validate or commit request-backed changes. */
final class ExecutePlan extends CliBookRequestOutputModeCommand {
  private final PlanResultDetail resultDetail;

  ExecutePlan(
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode,
      PlanResultDetail resultDetail) {
    super(bookAccess, requestFile, outputMode);
    this.resultDetail = Objects.requireNonNull(resultDetail, "resultDetail");
  }

  PlanResultDetail resultDetail() {
    return resultDetail;
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .mutation()
        .runExecutePlanCommand(bookAccess, requestFile, outputMode, resultDetail);
  }
}

/** Mutation CLI commands that validate or commit request-backed changes. */
final class PreflightEntry extends CliBookRequestOutputModeCommand {
  PreflightEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .mutation()
        .runPreflightEntryCommand(bookAccess, requestFile, outputMode);
  }
}

/** Mutation CLI commands that validate or commit request-backed changes. */
final class PostEntry extends CliBookRequestOutputModeCommand {
  PostEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext.mutation().runPostEntryCommand(bookAccess, requestFile, outputMode);
  }
}

/** Mutation CLI command that commits one typed business-entry request. */
final class RecordEntry extends CliBookRequestOutputModeCommand {
  private final OperationId operationId;

  RecordEntry(
      BookAccess bookAccess, Path requestFile, OutputMode outputMode, OperationId operationId) {
    super(bookAccess, requestFile, outputMode);
    this.operationId = Objects.requireNonNull(operationId, "operationId");
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .mutation()
        .runRecordEntryCommand(bookAccess, requestFile, outputMode, operationId);
  }
}
