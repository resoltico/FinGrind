package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused regression tests for human-facing CLI display helpers. */
class CliHumanDisplayTest {
  @Test
  void path_and_date_labels_coverNormalizedPath_redactedHints_andOpenRangeBranches() {
    assertEquals(
        Path.of("tmp/../fingrind/book.sqlite").toAbsolutePath().normalize().toString(),
        CliHumanDisplay.path(Path.of("tmp/../fingrind/book.sqlite")));
    assertEquals(
        "<redacted>/book.sqlite",
        CliHumanDisplay.path(new PublicPathHint("<redacted>/book.sqlite")));
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
