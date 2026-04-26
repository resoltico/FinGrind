package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Descriptor for the grouped command catalog published by the CLI capabilities contract. */
public record CommandCatalogDescriptor(
    List<CommandDescriptor> discovery,
    List<CommandDescriptor> administration,
    List<CommandDescriptor> query,
    List<CommandDescriptor> write)
    implements ContractDiscoveryDescriptor {
  /** Validates one command-catalog descriptor payload. */
  public CommandCatalogDescriptor {
    discovery = ContractDescriptorValidation.copyList(discovery, "discovery");
    administration = ContractDescriptorValidation.copyList(administration, "administration");
    query = ContractDescriptorValidation.copyList(query, "query");
    write = ContractDescriptorValidation.copyList(write, "write");
    requireUniqueCommandIds(
        Stream.of(discovery, administration, query, write).flatMap(List::stream).toList());
  }

  /** Returns every published command descriptor in stable capabilities order. */
  public List<CommandDescriptor> allCommands() {
    return Stream.of(discovery, administration, query, write).flatMap(List::stream).toList();
  }

  private static void requireUniqueCommandIds(List<CommandDescriptor> commands) {
    Set<OperationId> seen = EnumSet.noneOf(OperationId.class);
    commands.stream()
        .map(CommandDescriptor::name)
        .filter(operationId -> !seen.add(operationId))
        .findFirst()
        .ifPresent(
            operationId -> {
              throw new IllegalArgumentException(
                  "Duplicate command descriptor in capabilities catalog: "
                      + operationId.wireName());
            });
  }
}
