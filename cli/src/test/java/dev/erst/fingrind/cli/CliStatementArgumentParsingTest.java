package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for statement-oriented CLI argument parsing. */
class CliStatementArgumentParsingTest {
  @Test
  void parse_rejectsCsvStdoutWhenPdfArtifactRequestedForPrimaryStatements() {
    for (String[] arguments :
        List.of(
            new String[] {
              "financial-position",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--effective-date-as-of",
              "2026-04-30",
              "--output",
              "csv",
              "--pdf-out",
              "reports/financial-position.pdf"
            },
            new String[] {
              "income-statement",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--period-start",
              "2026-04-01",
              "--period-end",
              "2026-04-30",
              "--output",
              "csv",
              "--pdf-out",
              "reports/income-statement.pdf"
            },
            new String[] {
              "changes-in-equity",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--period-start",
              "2026-04-01",
              "--period-end",
              "2026-04-30",
              "--output",
              "csv",
              "--pdf-out",
              "reports/changes-in-equity.pdf"
            })) {
      CliArgumentsException unsupported =
          assertThrows(CliArgumentsException.class, () -> CliArguments.parse(arguments));
      assertEquals("--output", unsupported.argument());
    }
  }
}
