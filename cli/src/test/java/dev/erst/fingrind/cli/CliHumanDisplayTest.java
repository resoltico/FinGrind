package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused regression tests for human-facing CLI display helpers. */
class CliHumanDisplayTest {
  @Test
  void path_and_date_labels_coverCurrentDirectoryAndOpenRangeBranches() {
    assertEquals(".", CliHumanDisplay.path(Path.of(".")));
    assertEquals("." + java.io.File.separator + "tmp", CliHumanDisplay.path(Path.of("tmp")));
    assertEquals("book start", CliQueryOutputFormatter.lowerDateBoundaryLabel(null));
    assertEquals(
        "latest committed posting date", CliQueryOutputFormatter.upperDateBoundaryLabel(null));
  }

  @Test
  void wireLabel_handlesOrdinaryAndBlankSeparatedTokens() {
    assertEquals("Current Asset", CliHumanDisplay.wireLabel("CURRENT_ASSET"));
    assertEquals("Current Asset", CliHumanDisplay.wireLabel("CURRENT__ASSET"));
  }
}
