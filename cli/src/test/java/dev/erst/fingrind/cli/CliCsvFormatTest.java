package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused tests for canonical CSV rendering and parsing helpers. */
class CliCsvFormatTest {
  @Test
  void parseRow_handlesEmptyQuotedAndEscapedCells() {
    assertEquals(List.of(""), CliCsvFormat.parseRow(""));
    assertEquals(
        List.of("alpha\"beta", "gamma,delta", "line\nbreak"),
        CliCsvFormat.parseRow("\"alpha\"\"beta\",\"gamma,delta\",\"line\nbreak\""));
  }

  @Test
  void renderCsv_quotesCellsThatNeedEscaping() {
    String rendered =
        CliCsvFormat.renderCsv(
            List.of("first", "second"),
            List.of(List.of("alpha\"beta", "gamma,delta"), List.of("plain", "line\nbreak")));

    assertEquals(
        """
        first,second
        "alpha""beta","gamma,delta"
        plain,"line
        break"
        """
            .stripTrailing(),
        rendered);
  }

  @Test
  void renderCsv_prefixesSpreadsheetFormulaCellsAsText() {
    String rendered =
        CliCsvFormat.renderCsv(
            List.of("name"), List.of(List.of("=1+1"), List.of("  +1"), List.of("Cash")));

    assertEquals(
        """
        name
        '=1+1
        '  +1
        Cash
        """
            .stripTrailing(),
        rendered);
  }
}
