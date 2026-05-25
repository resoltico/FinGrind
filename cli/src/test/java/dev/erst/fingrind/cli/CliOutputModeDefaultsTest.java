package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliOutputModeDefaults}. */
class CliOutputModeDefaultsTest {
  @Test
  void outputModeDefaults_coverInteractiveAndRedirectedBranches() {
    assertEquals(OutputMode.TEXT, CliOutputModeDefaults.defaultSelectableOutputMode(true));
    assertEquals(OutputMode.JSON, CliOutputModeDefaults.defaultSelectableOutputMode(false));
    assertEquals(OutputMode.TEXT, CliOutputModeDefaults.defaultDiscoveryOutputMode(true));
    assertEquals(OutputMode.JSON, CliOutputModeDefaults.defaultDiscoveryOutputMode(false));
    assertEquals(
        CliOutputModeDefaults.resolved(null), CliOutputModeDefaults.defaultSelectableOutputMode());
    assertEquals(
        CliOutputModeDefaults.resolvedDiscovery(null),
        CliOutputModeDefaults.defaultDiscoveryOutputMode());
    assertEquals(OutputMode.TEXT, CliOutputModeDefaults.resolved(null, true));
    assertEquals(OutputMode.JSON, CliOutputModeDefaults.resolved(null, false));
    assertEquals(OutputMode.TEXT, CliOutputModeDefaults.resolvedDiscovery(OutputMode.TEXT));
    assertEquals(OutputMode.CSV, CliOutputModeDefaults.resolved(OutputMode.CSV, true));
  }
}
