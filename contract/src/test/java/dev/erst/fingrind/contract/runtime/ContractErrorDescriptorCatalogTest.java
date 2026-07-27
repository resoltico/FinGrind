package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

/** Tests the startup invariant that every declared deterministic error has one metadata owner. */
class ContractErrorDescriptorCatalogTest {
  @Test
  void immutableCompleteDefinitions_freezesACompleteCatalogAndRejectsAnUnownedDescriptor() {
    Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions =
        registeredDefinitions();

    Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> frozenDefinitions =
        ContractErrorDescriptorCatalog.immutableCompleteDefinitions(definitions);
    definitions.clear();

    assertEquals(
        "unknown-command",
        Objects.requireNonNull(
                frozenDefinitions.get(ContractErrors.Descriptor.UNKNOWN_COMMAND),
                "unknown command metadata")
            .code());
    assertThrows(UnsupportedOperationException.class, frozenDefinitions::clear);

    Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> missingDefinition =
        registeredDefinitions();
    missingDefinition.remove(ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> ContractErrorDescriptorCatalog.immutableCompleteDefinitions(missingDefinition));

    assertEquals(
        "No deterministic error metadata is registered for [OPEN_BOOK_COMPLETION_UNCERTAIN]",
        failure.getMessage());
  }

  private static Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition>
      registeredDefinitions() {
    ConcurrentMap<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions =
        new ConcurrentHashMap<>();
    for (ContractErrors.Descriptor descriptor : ContractErrors.Descriptor.values()) {
      definitions.put(descriptor, ContractErrorDescriptorCatalog.definitionFor(descriptor));
    }
    return definitions;
  }
}
