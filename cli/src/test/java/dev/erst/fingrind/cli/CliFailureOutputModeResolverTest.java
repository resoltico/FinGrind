package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.junit.jupiter.api.Test;

/** Locks the pre-parse failure-output resolution branches to the public 2B contract. */
class CliFailureOutputModeResolverTest {
  @Test
  void noArgs_defaultsToTheDiscoverySurfaceMode() {
    assertEquals(
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.DISCOVERY).mode(),
        CliFailureOutputModeResolver.resolve(new String[] {}));
  }

  @Test
  void unknownCommandFallsBackToJson() {
    assertEquals(OutputMode.JSON, CliFailureOutputModeResolver.resolve(new String[] {"no-such"}));
  }

  @Test
  void resolvedSupportedModesHonorExplicitAndDefaultSelections() {
    assertEquals(
        OutputMode.TEXT,
        CliFailureOutputModeResolver.resolve(new String[] {"generate-book-key-file"}));
    assertEquals(
        OutputMode.TEXT,
        CliFailureOutputModeResolver.resolve(
            new String[] {"generate-book-key-file", "--output", "text"}));
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(
            new String[] {"generate-book-key-file", "--output", "json"}));
  }

  @Test
  void outputSelectionDefectsFallBackToJson() {
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(
            new String[] {"generate-book-key-file", "--output", "text", "--output", "json"}));
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(new String[] {"generate-book-key-file", "--output"}));
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(
            new String[] {"generate-book-key-file", "--output", "not-a-mode"}));
  }

  @Test
  void unsupportedRequestedModeFallsBackToJson() {
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(new String[] {"get-posting", "--output", "csv"}));
  }

  @Test
  void operationsWithoutSelectableModesFallBackToJson() {
    assertEquals(
        OutputMode.JSON,
        CliFailureOutputModeResolver.resolve(new String[] {"print-plan-template"}));
  }

  @Test
  void commandSpecificHelpResolvesThroughTheHelpOperation() {
    assertEquals(
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.DISCOVERY).mode(),
        CliFailureOutputModeResolver.resolve(new String[] {"trial-balance", "--help"}));
    assertEquals(
        CliOutputModeDefaults.outputDefault(CliOutputModeDefaults.OutputSurface.DISCOVERY).mode(),
        CliFailureOutputModeResolver.resolve(new String[] {"trial-balance", "-h"}));
  }
}
