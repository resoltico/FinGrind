package dev.erst.fingrind.contract.runtime;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Joins the responsibility-owned descriptor metadata behind the public descriptor enum. */
final class ContractErrorDescriptorCatalog {
  private static final Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition>
      DEFINITIONS = definitions();

  private ContractErrorDescriptorCatalog() {}

  static ContractErrorDescriptorDefinition definitionFor(ContractErrors.Descriptor descriptor) {
    return Objects.requireNonNull(
        DEFINITIONS.get(Objects.requireNonNull(descriptor, "descriptor")),
        "complete descriptor catalog");
  }

  private static Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions() {
    ConcurrentMap<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions =
        new ConcurrentHashMap<>();
    ContractInvocationErrorDescriptors.addTo(definitions);
    ContractRuntimeErrorDescriptors.addTo(definitions);
    ContractArtifactErrorDescriptors.addTo(definitions);
    ContractProtectedBookErrorDescriptors.addTo(definitions);
    ContractAttestationErrorDescriptors.addTo(definitions);
    return immutableCompleteDefinitions(definitions);
  }

  /** Freezes descriptor metadata only after every public deterministic error has an owner. */
  static Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition>
      immutableCompleteDefinitions(
          Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> frozenDefinitions =
        Map.copyOf(Objects.requireNonNull(definitions, "definitions"));
    Set<ContractErrors.Descriptor> missingDescriptors =
        EnumSet.allOf(ContractErrors.Descriptor.class);
    missingDescriptors.removeAll(frozenDefinitions.keySet());
    if (!missingDescriptors.isEmpty()) {
      throw new IllegalStateException(
          "No deterministic error metadata is registered for " + missingDescriptors);
    }
    return frozenDefinitions;
  }
}
