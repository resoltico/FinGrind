package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.PlanTemplateTopic;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.BookTemplateId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Discovery-oriented CLI commands that never require a book session. */
record Help(
    @Nullable OperationId commandTopic,
    OutputMode outputMode,
    DiscoveryDetail detail,
    @Nullable OperationCategory category,
    boolean terseTopLevel)
    implements CliCommand.OutputModeCommand {
  Help {
    Objects.requireNonNull(outputMode, "outputMode");
    Objects.requireNonNull(detail, "detail");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeHelp(commandTopic, outputMode, detail, category, terseTopLevel);
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
record Capabilities(
    OutputMode outputMode, DiscoveryDetail detail, CliDiscoverySelections selections)
    implements CliCommand.OutputModeCommand {
  Capabilities {
    Objects.requireNonNull(outputMode, "outputMode");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(selections, "selections");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeCapabilities(outputMode, detail, selections);
  }
}

/** Requests one canonical request scaffold JSON document. */
record EnvironmentCommand(OutputMode outputMode) implements CliCommand.OutputModeCommand {
  EnvironmentCommand {
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeEnvironment(outputMode);
  }
}

/** Requests one canonical request scaffold JSON document. */
record PrintRequestTemplate(
    @Nullable OperationId commandTopic, @Nullable BookTemplateId bookTemplateId)
    implements CliCommand {
  PrintRequestTemplate() {
    this(null, null);
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writeRequestTemplate(commandTopic, bookTemplateId);
  }
}

/** Requests a topic-specific AI-agent ledger-plan scaffold JSON document. */
record PrintPlanTemplate(PlanTemplateTopic topic) implements CliCommand {
  PrintPlanTemplate() {
    this(PlanTemplateTopic.GENERAL);
  }

  PrintPlanTemplate {
    Objects.requireNonNull(topic, "topic");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .discovery()
        .writePlanTemplate(topic);
  }
}
