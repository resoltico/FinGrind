package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused regression tests for operator-facing CLI display helpers. */
class CliTextDisplayTest {
  @Test
  void path_and_date_labels_coverNormalizedPath_redactedHints_andOpenRangeBranches() {
    assertEquals(
        ".../fingrind/book.sqlite", CliTextDisplay.path(Path.of("tmp/../fingrind/book.sqlite")));
    assertEquals(
        Path.of("/").toAbsolutePath().normalize().toString().replace('\\', '/'),
        CliTextDisplay.path(Path.of("/")));
    assertEquals("book.sqlite", CliTextDisplay.path(Path.of("/book.sqlite")));
    assertEquals("tmp/book.sqlite", CliTextDisplay.path(Path.of("/tmp/book.sqlite")));
    assertEquals(
        "<redacted>/book.sqlite",
        CliTextDisplay.path(new PublicPathHint("<redacted>/book.sqlite")));
    assertEquals("book start", CliQueryOutputFormatter.lowerDateBoundaryLabel(null));
    assertEquals(
        "latest committed posting date", CliQueryOutputFormatter.upperDateBoundaryLabel(null));
    assertEquals(
        "tmp/book.sqlite", CliQueryOutputFormatter.absolutePath(Path.of("/tmp/book.sqlite")));
  }

  @Test
  void wireLabel_handlesOrdinaryAndBlankSeparatedTokens() {
    assertEquals("Current Asset", CliTextDisplay.wireLabel("CURRENT_ASSET"));
    assertEquals("Current Asset", CliTextDisplay.wireLabel("CURRENT__ASSET"));
  }
}
