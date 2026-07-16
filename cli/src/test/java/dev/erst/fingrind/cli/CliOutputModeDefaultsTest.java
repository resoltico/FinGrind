package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliOutputModeDefaults}. */
class CliOutputModeDefaultsTest {
  @Test
  void outputModeDefaults_coverSelectableAndDiscoveryBranches() {
    assertEquals(
        CliOutputModeDefaults.resolved(null, CliOutputModeDefaults.OutputSurface.SELECTABLE),
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.SELECTABLE).mode());
    assertEquals(
        CliOutputModeDefaults.resolved(null, CliOutputModeDefaults.OutputSurface.DISCOVERY),
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.DISCOVERY).mode());
    assertEquals(
        OutputMode.TEXT,
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.SELECTABLE).mode());
    assertEquals(
        OutputMode.TEXT,
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.DISCOVERY).mode());
    assertEquals(
        OutputMode.TEXT,
        CliOutputModeDefaults.resolved(
            OutputMode.TEXT, CliOutputModeDefaults.OutputSurface.DISCOVERY));
    assertEquals(
        OutputMode.CSV,
        CliOutputModeDefaults.resolved(
            OutputMode.CSV, CliOutputModeDefaults.OutputSurface.SELECTABLE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliOutputModeDefaults.OutputDefault(OutputMode.CSV));
  }
}
