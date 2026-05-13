package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for posting-oriented read-command validation in {@link CliArguments}. */
class CliPostingQueryArgumentValidationTest extends CliArgumentParsingTestSupport {
  @Test
  void parse_rejectsNonPositivePostingListLimitAgainstLimitArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--limit",
                      "0"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--limit", exception.argument());
    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("--limit must be between"));
  }

  @Test
  void parse_rejectsMissingAndDuplicatePostingIdForGetPosting() {
    CliArgumentsException missing =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "get-posting", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                    }));
    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "get-posting",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--posting-id",
                      "posting-1",
                      "--posting-id",
                      "posting-2"
                    }));

    assertEquals("--posting-id", missing.argument());
    assertEquals("A --posting-id argument is required.", missing.getMessage());
    assertEquals("--posting-id", duplicate.argument());
    assertEquals("Duplicate argument: --posting-id", duplicate.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedGetPostingArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "get-posting",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--extra",
                      "value",
                      "--posting-id",
                      "posting-1"
                    }));

    assertEquals("--extra", exception.argument());
    assertEquals("Unsupported argument: --extra", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedListPostingsArgumentBeforeMissingBookArguments() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"list-postings", "--extra"}));

    assertEquals("--extra", exception.argument());
    assertEquals("Unsupported argument: --extra", exception.getMessage());
  }

  @Test
  void parse_rejectsListPostingsDuplicateAndUnsupportedArguments() {
    CliArgumentsException duplicateDateFrom =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--effective-date-from",
                      "2026-04-01",
                      "--effective-date-from",
                      "2026-04-02"
                    }));
    CliArgumentsException duplicateDateTo =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--effective-date-to",
                      "2026-04-30",
                      "--effective-date-to",
                      "2026-05-01"
                    }));
    CliArgumentsException duplicateLimit =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--limit",
                      "10",
                      "--limit",
                      "20"
                    }));
    CliArgumentsException duplicateCursor =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--cursor",
                      "cursor-1",
                      "--cursor",
                      "cursor-2"
                    }));
    CliArgumentsException unsupportedWithValue =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--extra",
                      "value"
                    }));
    CliArgumentsException unsupportedBeforeOption =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--extra",
                      "--limit",
                      "5"
                    }));
    CliArgumentsException positional =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "unexpected-token"
                    }));

    assertEquals("--effective-date-from", duplicateDateFrom.argument());
    assertEquals("Duplicate argument: --effective-date-from", duplicateDateFrom.getMessage());
    assertEquals("--effective-date-to", duplicateDateTo.argument());
    assertEquals("Duplicate argument: --effective-date-to", duplicateDateTo.getMessage());
    assertEquals("--limit", duplicateLimit.argument());
    assertEquals("Duplicate argument: --limit", duplicateLimit.getMessage());
    assertEquals("--cursor", duplicateCursor.argument());
    assertEquals("Duplicate argument: --cursor", duplicateCursor.getMessage());
    assertEquals("--extra", unsupportedWithValue.argument());
    assertEquals("Unsupported argument: --extra", unsupportedWithValue.getMessage());
    assertEquals("--extra", unsupportedBeforeOption.argument());
    assertEquals("Unsupported argument: --extra", unsupportedBeforeOption.getMessage());
    assertEquals("unexpected-token", positional.argument());
    assertEquals("Unsupported argument: unexpected-token", positional.getMessage());
  }

  @Test
  void parse_rejectsInvalidPostingCursorWithDedicatedContractCode() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-postings",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--cursor",
                      "definitely-not-a-valid-cursor"
                    }));

    assertEquals("invalid-page-cursor", exception.code());
    assertEquals("--cursor", exception.argument());
    assertEquals(
        "Unsupported posting page cursor: definitely-not-a-valid-cursor", exception.getMessage());
  }
}
