package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Field-shaped close commands through the initialized SQLite, credential, and output boundaries.
 */
class FinGrindCliPeriodCloseWorkflowTest extends CliWorkflowFixtureSupport {
  @Test
  void closeCommands_publishAnEmptySweepAndAValidatedFutureCloseRejection() {
    Path bookFile = tempDirectory.resolve("period-close").resolve("current.sqlite");
    Path bookKeyFile = writeBookKey(bookFile);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(output),
            fixedClock());

    assertEquals(0, cli.run(jsonArguments(openBookStandardInputArguments(bookFile))));

    output.reset();
    assertEquals(
        0,
        cli.run(
            attestedJsonArguments(
                "interim-result-sweep",
                "--book-file",
                bookFile.toString(),
                "--book-key-file",
                bookKeyFile.toString(),
                "--through",
                "2026-04-07")),
        () -> output.toString(StandardCharsets.UTF_8));
    String sweep = output.toString(StandardCharsets.UTF_8);
    assertTrue(sweep.contains("\"status\":\"ok\""), sweep);
    assertTrue(sweep.contains("\"sweptTotals\":[]"), sweep);

    output.reset();
    assertEquals(
        2,
        cli.run(
            attestedJsonArguments(
                "fiscal-year-close",
                "--book-file",
                bookFile.toString(),
                "--book-key-file",
                bookKeyFile.toString(),
                "--year",
                "2026")),
        () -> output.toString(StandardCharsets.UTF_8));
    assertRejected(output);
  }

  private static void assertRejected(ByteArrayOutputStream output) {
    String json = output.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"rejected\""), json);
    assertTrue(json.contains("\"category\":"), json);
  }
}
