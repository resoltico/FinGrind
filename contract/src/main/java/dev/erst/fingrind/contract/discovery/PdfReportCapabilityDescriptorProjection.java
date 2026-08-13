package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Projects descriptor-advertised PDF-capable report names without consulting the catalog. */
public final class PdfReportCapabilityDescriptorProjection {
  private PdfReportCapabilityDescriptorProjection() {}

  /** Returns PDF-capable query-report wire names in the supplied descriptor order. */
  public static List<String> pdfReportOperationWireNames(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return pdfReportOperationWireNames(capabilitiesDescriptor.commands().query());
  }

  /** Returns PDF-capable query-report wire names in the supplied command-descriptor order. */
  public static List<String> pdfReportOperationWireNames(
      List<CommandDescriptor> queryReportCommands) {
    Objects.requireNonNull(queryReportCommands, "queryReportCommands");
    List<String> operationWireNames = new ArrayList<>();
    Set<OperationId> seenOperationIds = EnumSet.noneOf(OperationId.class);
    for (CommandDescriptor command : queryReportCommands) {
      CommandDescriptor descriptor = Objects.requireNonNull(command, "query report command");
      OperationId operationId = descriptor.name();
      if (!seenOperationIds.add(operationId)) {
        throw new IllegalArgumentException(
            "Duplicate query report descriptor in PDF capability discovery: "
                + operationId.wireName());
      }
      if (descriptor.artifactOutputs().stream()
          .anyMatch(artifact -> ProtocolArtifactOutput.pdfFormat().equals(artifact.format()))) {
        operationWireNames.add(operationId.wireName());
      }
    }
    return List.copyOf(operationWireNames);
  }
}
