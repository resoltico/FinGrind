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
    assertEquals("book start", CliQueryTextFormatAccess.lowerDateBoundaryLabel(null));
    assertEquals(
        "current book horizon (latest effective date in the selected book)",
        CliQueryTextFormatAccess.upperDateBoundaryLabel(null));
    assertEquals(
        "no postings in selected book",
        CliQueryTextFormatAccess.upperDateBoundaryLabel(null, null));
    assertEquals(
        "2026-05-14 (latest effective date in the selected book)",
        CliQueryTextFormatAccess.upperDateBoundaryLabel(
            null, java.time.LocalDate.parse("2026-05-14")));
    assertEquals("no-postings", CliQueryTextFormatAccess.upperDateBoundaryMeaning(null, null));
    assertEquals(
        "latest-posting-effective-date",
        CliQueryTextFormatAccess.upperDateBoundaryMeaning(
            null, java.time.LocalDate.parse("2026-05-14")));
    assertEquals(
        "selected-date",
        CliQueryTextFormatAccess.upperDateBoundaryMeaning(
            java.time.LocalDate.parse("2026-05-31"), java.time.LocalDate.parse("2026-05-14")));
    assertEquals(
        "tmp/book.sqlite", CliQueryTextFormatAccess.absolutePath(Path.of("/tmp/book.sqlite")));
  }

  @Test
  void wireLabel_handlesOrdinaryAndBlankSeparatedTokens() {
    assertEquals("Current Asset", CliTextDisplay.wireLabel("CURRENT_ASSET"));
    assertEquals("Current Asset", CliTextDisplay.wireLabel("CURRENT__ASSET"));
  }

  @Test
  void humanDisplay_truncatesLongOpaqueReferencesAndPreservesShortValues() {
    assertEquals("posting", CliHumanDisplay.opaqueReference("posting"));
    assertEquals("12345678", CliHumanDisplay.opaqueReference("123456789"));
  }
}
