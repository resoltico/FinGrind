package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;

/** Mutation CLI commands that validate or commit request-backed changes. */
record ExecutePlan(BookAccess bookAccess, Path requestFile)
    implements CliCommand.JsonFailureCommand {
  ExecutePlan {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(requestFile, "requestFile");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .mutation()
        .runExecutePlanCommand(bookAccess, requestFile);
  }
}

/** Mutation CLI commands that validate or commit request-backed changes. */
record PreflightEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  PreflightEntry {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(requestFile, "requestFile");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .mutation()
        .runPreflightEntryCommand(bookAccess, requestFile, outputMode);
  }
}

/** Mutation CLI commands that validate or commit request-backed changes. */
record PostEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  PostEntry {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(requestFile, "requestFile");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .mutation()
        .runPostEntryCommand(bookAccess, requestFile, outputMode);
  }
}
