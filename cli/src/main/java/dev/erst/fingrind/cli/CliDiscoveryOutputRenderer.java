package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in human-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private CliDiscoveryOutputRenderer() {}

  static String renderHelpHuman(ContractDiscovery.HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", helpDescriptor.application()),
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())));
    String commands =
        CliTextFormat.renderTable(
            List.of("Command", "Outputs", "Summary"),
            helpDescriptor.commands().stream()
                .map(
                    command ->
                        List.of(
                            command.name(),
                            command.outputModes().isEmpty()
                                ? "(json)"
                                : String.join(", ", command.outputModes()),
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

  static String renderCapabilitiesHuman(
      ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    ContractDiscovery.StorageSurfaceDescriptor storageDescriptor = capabilitiesDescriptor.storage();
    ContractDiscovery.CommandCatalogDescriptor commandCatalog = capabilitiesDescriptor.commands();
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", capabilitiesDescriptor.application()),
                List.of("Version", capabilitiesDescriptor.version()),
                List.of("Book boundary", storageDescriptor.bookBoundary()),
                List.of("Storage", String.join(", ", storageDescriptor.engines())),
                List.of("Timestamp", capabilitiesDescriptor.timestamp())));
    String commands =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", String.join(", ", commandCatalog.discovery())),
                List.of("Administration", String.join(", ", commandCatalog.administration())),
                List.of("Query", String.join(", ", commandCatalog.query())),
                List.of("Write", String.join(", ", commandCatalog.write()))));
    String outputModes =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Query/report stdout",
                    String.join(", ", capabilitiesDescriptor.requestInput().queryOutputModes())),
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics())));
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header, section("Command Groups", commands), section("Read Surface", outputModes)));
  }

  static String renderVersionHuman(ContractDiscovery.VersionDescriptor versionDescriptor) {
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
}
