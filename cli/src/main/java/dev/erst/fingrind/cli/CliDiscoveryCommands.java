package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Discovery-oriented CLI commands that never require a book session. */
record Help(@Nullable OperationId commandTopic, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  Help {
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeHelp(commandTopic, outputMode);
  }
}

/** Discovery-oriented CLI commands that never require a book session. */
record Version(OutputMode outputMode) implements CliCommand.OutputModeCommand {
  Version {
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeVersion(outputMode);
  }
}

/** Discovery-oriented CLI commands that never require a book session. */
record Capabilities(OutputMode outputMode) implements CliCommand.OutputModeCommand {
  Capabilities {
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeCapabilities(outputMode);
  }
}

/** Requests the canonical posting-request scaffold JSON document. */
record PrintRequestTemplate() implements CliCommand.JsonFailureCommand {
  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeRequestTemplate();
  }
}

/** Requests the canonical AI-agent ledger-plan scaffold JSON document. */
record PrintPlanTemplate() implements CliCommand.JsonFailureCommand {
  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writePlanTemplate();
  }
}
