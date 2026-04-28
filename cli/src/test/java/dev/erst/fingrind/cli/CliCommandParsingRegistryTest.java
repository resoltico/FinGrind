package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliCommandParsingRegistry}. */
class CliCommandParsingRegistryTest {
  @Test
  void parse_dispatchesDiscoveryOperationsToTheirRegisteredParser() {
    assertInstanceOf(
        Help.class, CliCommandParsingRegistry.parse(OperationId.HELP, List.of("help")));
  }
}
