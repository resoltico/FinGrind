package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for book-inspection CLI argument parsing. */
class CliBookInspectionArgumentParsingTest {
  @Test
  void parse_defaultsAndAcceptsExplicitTextOutput() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    InspectBook defaultInspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    InspectBook explicitInspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "text"
                }));

    assertEquals(OutputMode.TEXT, defaultInspectBook.outputMode());
    assertEquals(OutputMode.TEXT, explicitInspectBook.outputMode());
  }

  @Test
  void parse_rejectsUnsupportedAndConflictingInspectionArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "csv"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "text",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--limit",
                  "10"
                }));
  }
}
