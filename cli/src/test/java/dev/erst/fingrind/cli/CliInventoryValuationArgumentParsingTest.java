package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks inventory valuation to its dedicated query and artifact argument contract. */
class CliInventoryValuationArgumentParsingTest {
  private static final Path BOOK_FILE = Path.of("book.sqlite");
  private static final Path BOOK_KEY_FILE = Path.of("book.key");

  @Test
  void parse_acceptsDefaultAndFullySpecifiedInventoryValuationQueries() {
    InventoryValuation defaultCommand = parse();
    InventoryValuation detailedCommand =
        parse(
            "--as-of",
            "2026-04-30",
            "--movements",
            "--output",
            "json",
            "--pdf-out",
            "reports/inventory.pdf");

    assertEquals(Optional.empty(), defaultCommand.query().effectiveDateAsOf());
    assertFalse(defaultCommand.query().includeMovements());
    assertEquals(OutputMode.TEXT, defaultCommand.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), detailedCommand.query().effectiveDateAsOf());
    assertTrue(detailedCommand.query().includeMovements());
    assertEquals(OutputMode.JSON, detailedCommand.output().outputMode());
    assertEquals(Path.of("reports/inventory.pdf"), detailedCommand.output().pdfOutPath());
  }

  @Test
  void parse_rejectsDuplicateAndUnsupportedInventoryValuationArguments() {
    assertInvalid("--as-of", "2026-04-30", "--as-of", "2026-05-01");
    assertInvalid("--movements", "--movements");
    assertInvalid("--output", "text", "--output", "json");
    assertInvalid("--output", "pdf");
  }

  private static InventoryValuation parse(String... reportArguments) {
    String[] arguments = new String[5 + reportArguments.length];
    arguments[0] = "inventory-valuation";
    arguments[1] = "--book-file";
    arguments[2] = BOOK_FILE.toString();
    arguments[3] = "--book-key-file";
    arguments[4] = BOOK_KEY_FILE.toString();
    System.arraycopy(reportArguments, 0, arguments, 5, reportArguments.length);
    return assertInstanceOf(InventoryValuation.class, CliArguments.parse(arguments));
  }

  private static void assertInvalid(String... reportArguments) {
    assertThrows(CliArgumentsException.class, () -> parse(reportArguments));
  }
}
