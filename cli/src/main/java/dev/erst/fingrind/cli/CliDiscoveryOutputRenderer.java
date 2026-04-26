package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.CommandDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.VersionDescriptor;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in human-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private CliDiscoveryOutputRenderer() {}

  static String renderHelpHuman(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
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
            : String.join(System.lineSeparator(), helpDescriptor.quickStart());
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
