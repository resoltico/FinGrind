package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliCommandParsingRegistry}. */
class CliCommandParsingRegistryTest {
  @Test
  void parse_dispatchesDiscoveryOperationsToTheirRegisteredParser() {
    assertInstanceOf(
        Help.class, CliCommandParsingRegistry.parse(OperationId.HELP, List.of("help")));
  }

  @Test
  void requiredParser_rejectsMissingOperationRegistrations() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliCommandParsingRegistry.requiredParser(OperationId.HELP, Map.of()));

    org.junit.jupiter.api.Assertions.assertEquals(
        "No CLI parser registered for help", failure.getMessage());
  }

  @Test
  void registry_coversEveryProtocolCatalogOperation() {
    assertEquals(
        ProtocolCatalog.operations().stream()
            .map(operation -> operation.id())
            .collect(java.util.stream.Collectors.toSet()),
        CliCommandParsingRegistry.registeredOperationIds());
  }

  @Test
  void validatedParsers_rejectsRegistryDriftFromTheProtocolCatalog() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> CliCommandParsingRegistry.validatedParsers(Map.of()));

    assertEquals(
        "CLI parser registry must match ProtocolCatalog operations. Missing: "
            + ProtocolCatalog.operations().stream()
                .map(operation -> operation.id())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
            + "; unexpected: []",
        failure.getMessage());
  }
}
