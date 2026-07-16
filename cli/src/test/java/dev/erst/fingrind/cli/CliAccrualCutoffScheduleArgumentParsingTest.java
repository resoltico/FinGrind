package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks the accrual cut-off schedule query and artifact argument contract. */
class CliAccrualCutoffScheduleArgumentParsingTest {
  private static final Path BOOK_FILE = Path.of("book.sqlite");
  private static final Path BOOK_KEY_FILE = Path.of("book.key");

  @Test
  void parse_acceptsDefaultAndFullySpecifiedScheduleQueries() {
    AccrualCutoffSchedule defaultCommand = parse();
    AccrualCutoffSchedule detailedCommand =
        parse(
            "--as-of",
            "2026-04-30",
            "--output",
            "json",
            "--pdf-out",
            "reports/accrual-cutoff-schedule.pdf");

    assertEquals(Optional.empty(), defaultCommand.query().effectiveDateAsOf());
    assertEquals(OutputMode.TEXT, defaultCommand.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), detailedCommand.query().effectiveDateAsOf());
    assertEquals(OutputMode.JSON, detailedCommand.output().outputMode());
    assertEquals(
        Path.of("reports/accrual-cutoff-schedule.pdf"), detailedCommand.output().pdfOutPath());
  }

  @Test
  void parse_rejectsDuplicateAndUnsupportedScheduleArguments() {
    assertInvalid("--as-of", "2026-04-30", "--as-of", "2026-05-01");
    assertInvalid("--output", "text", "--output", "json");
    assertInvalid("--output", "pdf");
  }

  private static AccrualCutoffSchedule parse(String... reportArguments) {
    String[] arguments = new String[5 + reportArguments.length];
    arguments[0] = "accrual-cutoff-schedule";
    arguments[1] = "--book-file";
    arguments[2] = BOOK_FILE.toString();
    arguments[3] = "--book-key-file";
    arguments[4] = BOOK_KEY_FILE.toString();
    System.arraycopy(reportArguments, 0, arguments, 5, reportArguments.length);
    return assertInstanceOf(AccrualCutoffSchedule.class, CliArguments.parse(arguments));
  }

  private static void assertInvalid(String... reportArguments) {
    assertThrows(CliArgumentsException.class, () -> parse(reportArguments));
  }
}
