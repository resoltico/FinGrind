package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Derives the operator-facing PDF-capable report summary from command descriptors. */
final class CliDiscoveryPdfCapabilitySummary {
  private static final String PDF_FORMAT = "pdf";
  private static final String PDF_OPTION = "--pdf-out <path>";

  private CliDiscoveryPdfCapabilitySummary() {}

  static String render(CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return render(capabilitiesDescriptor.commands().query());
  }

  static String render(List<CommandDescriptor> queryReportCommands) {
    Objects.requireNonNull(queryReportCommands, "queryReportCommands");
    List<String> operationIds = new ArrayList<>();
    Set<OperationId> seenOperationIds = EnumSet.noneOf(OperationId.class);
    for (CommandDescriptor command : queryReportCommands) {
      OperationId operationId = Objects.requireNonNull(command, "query report command").name();
      if (!seenOperationIds.add(operationId)) {
        throw new IllegalArgumentException(
            "Duplicate query report descriptor in PDF capability discovery: "
                + operationId.wireName());
      }
      if (command.artifactOutputs().stream()
          .anyMatch(artifact -> PDF_FORMAT.equals(artifact.format()))) {
        operationIds.add(operationId.wireName());
      }
    }
    return switch (operationIds.size()) {
      case 0 -> "No report commands can emit pdf via " + PDF_OPTION + ".";
      case 1 -> operationIds.getFirst() + " can emit pdf via " + PDF_OPTION + ".";
      case 2 ->
          operationIds.get(0)
              + " and "
              + operationIds.get(1)
              + " can emit pdf via "
              + PDF_OPTION
              + ".";
      default ->
          String.join(", ", operationIds.subList(0, operationIds.size() - 1))
              + ", and "
              + operationIds.getLast()
              + " can emit pdf via "
              + PDF_OPTION
              + ".";
    };
  }
}
