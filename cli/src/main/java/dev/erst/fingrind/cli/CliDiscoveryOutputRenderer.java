package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.CommandDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.VersionDescriptor;
import dev.erst.fingrind.contract.WorkflowDescriptor;
import dev.erst.fingrind.contract.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in human-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private CliDiscoveryOutputRenderer() {}

  static String renderHelpHuman(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpHuman(helpDescriptor);
    }
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", helpDescriptor.application()),
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())));
    String commands =
        CliTextFormat.renderTable(
            List.of("Command", "Stdout", "Summary"),
            helpDescriptor.commands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.stdoutContractSummary(),
                            command.summary()))
                .toList());
    String quickStart =
        helpDescriptor.quickStart().isEmpty()
            ? "(none)"
            : helpDescriptor.quickStart().stream()
                .map(CliDiscoveryOutputRenderer::renderQuickStartWorkflow)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    String exitCodes =
        CliTextFormat.renderTable(
            List.of("Code", "Meaning"),
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            0);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Commands", commands),
            section("Quick Start", quickStart),
            section("Exit Codes", exitCodes)));
  }

  private static String renderCommandHelpHuman(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", helpDescriptor.application()),
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())));
    String commandDetails =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Command", command.name().wireName()),
                List.of("Summary", command.summary()),
                List.of("Stdout", command.stdoutContractSummary()),
                List.of("Artifacts", artifactSummary(command)),
                List.of(
                    "Aliases",
                    command.aliases().isEmpty()
                        ? "(none)"
                        : String.join(", ", command.aliases()))));
    String usage =
        helpDescriptor.usage().isEmpty()
            ? "(none)"
            : String.join(System.lineSeparator(), helpDescriptor.usage());
    String options =
        command.options().isEmpty()
            ? "(none)"
            : String.join(System.lineSeparator(), command.options());
    String examples =
        operation.examples().isEmpty()
            ? "(none)"
            : operation.examples().stream()
                .map(CliInvocationText::rewriteInvocationPrefix)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    String exitCodes =
        CliTextFormat.renderTable(
            List.of("Code", "Meaning"),
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            0);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Command", commandDetails),
            section("Usage", usage),
            section("Options", options),
            section("Examples", examples),
            section("Exit Codes", exitCodes)));
  }

  static String renderCapabilitiesHuman(CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    StorageSurfaceDescriptor storageDescriptor = capabilitiesDescriptor.storage();
    CommandCatalogDescriptor commandCatalog = capabilitiesDescriptor.commands();
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", capabilitiesDescriptor.application()),
                List.of("Version", capabilitiesDescriptor.version()),
                List.of("Book boundary", storageDescriptor.bookBoundary()),
                List.of(
                    "Storage",
                    String.join(
                        ", ", storageDescriptor.engines().stream().map(Object::toString).toList())),
                List.of("Timestamp", capabilitiesDescriptor.timestamp())));
    String commands =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))));
    String commandContracts =
        CliTextFormat.renderTable(
            List.of("Command", "Stdout", "Artifacts"),
            commandCatalog.allCommands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.stdoutContractSummary(),
                            artifactSummary(command)))
                .toList());
    String requestInput =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Selectable stdout flag", capabilitiesDescriptor.requestInput().outputOption()),
                List.of("Book file flag", capabilitiesDescriptor.requestInput().bookFileOption()),
                List.of(
                    "Request file flag", capabilitiesDescriptor.requestInput().requestFileOption()),
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics())));
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header,
            section("Command Groups", commands),
            section("Command Contracts", commandContracts),
            section("Request Input", requestInput)));
  }

  static String renderVersionHuman(VersionDescriptor versionDescriptor) {
    Objects.requireNonNull(versionDescriptor, "versionDescriptor");
    return CliTextFormat.renderTitledBlock(
        versionDescriptor.application(),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", versionDescriptor.version()),
                List.of("Description", versionDescriptor.description()))));
  }

  private static String joinSections(String... sections) {
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static boolean isCommandScoped(HelpDescriptor helpDescriptor) {
    return helpDescriptor.commands().size() == 1 && helpDescriptor.quickStart().isEmpty();
  }

  private static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  private static String joinCommandNames(List<CommandDescriptor> commands) {
    return String.join(", ", commands.stream().map(command -> command.name().wireName()).toList());
  }

  private static String renderQuickStartWorkflow(WorkflowDescriptor workflow) {
    String title =
        switch (workflow.surface()) {
          case BUNDLE_POSIX_SHELL -> "Self-Contained Bundle (POSIX Shell)";
          case BUNDLE_WINDOWS_POWERSHELL -> "Self-Contained Bundle (Windows PowerShell)";
          case SOURCE_CHECKOUT_POSIX_SHELL -> "Source Checkout Launcher (POSIX Shell)";
          case SOURCE_CHECKOUT_WINDOWS_POWERSHELL ->
              "Source Checkout Launcher (Windows PowerShell)";
          case DIRECT_JAVA_POSIX_SHELL -> "Developer Raw JAR (POSIX Shell)";
          case DIRECT_JAVA_WINDOWS_POWERSHELL -> "Developer Raw JAR (Windows PowerShell)";
          case CONTAINER_DOCKER -> "Container Image (Docker CLI)";
        };
    return title
        + System.lineSeparator()
        + workflow.steps().stream()
            .map(CliDiscoveryOutputRenderer::renderQuickStartStep)
            .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
  }

  private static String renderQuickStartStep(WorkflowStepDescriptor step) {
    return switch (step.kind()) {
      case COMMAND -> requireText(step);
      case EDIT ->
          "Write: "
              + requirePath(step)
              + System.lineSeparator()
              + indent(requireContent(step), "  ");
      case NOTE -> "Note: " + requireText(step);
    };
  }

  private static String indent(String text, String prefix) {
    return text.lines()
        .map(line -> prefix + line)
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
  }

  private static String requireText(WorkflowStepDescriptor step) {
    return java.util.Objects.requireNonNull(step.text(), "Workflow text is missing.");
  }

  private static String requirePath(WorkflowStepDescriptor step) {
    return java.util.Objects.requireNonNull(step.path(), "Workflow path is missing.");
  }

  private static String requireContent(WorkflowStepDescriptor step) {
    return java.util.Objects.requireNonNull(step.content(), "Workflow content is missing.");
  }

  private static String artifactSummary(CommandDescriptor command) {
    if (command.artifactOutputs().isEmpty()) {
      return "(none)";
    }
    return String.join(
        ", ",
        command.artifactOutputs().stream()
            .map(artifact -> artifact.format() + " via " + artifact.option())
            .toList());
  }
}
