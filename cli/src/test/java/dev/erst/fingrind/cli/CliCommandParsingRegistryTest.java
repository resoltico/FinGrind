package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
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
}
