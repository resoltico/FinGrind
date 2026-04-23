package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for account-oriented read-command validation in {@link CliArguments}. */
class CliAccountQueryArgumentValidationTest extends CliArgumentParsingTestSupport {
  @Test
  void parse_rejectsInvalidAccountListCursorAgainstCursorArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-accounts",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--cursor",
                      "%"
                    }));

    assertEquals("invalid-page-cursor", exception.code());
    assertEquals("--cursor", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Unsupported account page cursor"));
  }

  @Test
  void parse_rejectsDuplicateQueryOptionsAndMissingAccountBalanceAccountCode() {
    CliArgumentsException duplicatePostingAccountCode =
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
                      "--account-code",
                      "1000",
                      "--account-code",
                      "2000"
                    }));
    CliArgumentsException missingBalanceAccountCode =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-balance", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                    }));

    assertEquals("--account-code", duplicatePostingAccountCode.argument());
    assertEquals("Duplicate argument: --account-code", duplicatePostingAccountCode.getMessage());
    assertEquals("--account-code", missingBalanceAccountCode.argument());
    assertEquals("A --account-code argument is required.", missingBalanceAccountCode.getMessage());
  }

  @Test
  void parse_rejectsListAccountsDuplicateAndUnsupportedArguments() {
    CliArgumentsException duplicateLimit =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-accounts",
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
                      "list-accounts",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--cursor",
                      "first",
                      "--cursor",
                      "second"
                    }));
    CliArgumentsException unsupported =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-accounts",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--extra",
                      "value"
                    }));

    assertEquals("--limit", duplicateLimit.argument());
    assertEquals("Duplicate argument: --limit", duplicateLimit.getMessage());
    assertEquals("--cursor", duplicateCursor.argument());
    assertEquals("Duplicate argument: --cursor", duplicateCursor.getMessage());
    assertEquals("--extra", unsupported.argument());
    assertEquals("Unsupported argument: --extra", unsupported.getMessage());
  }

  @Test
  void parse_rejectsAccountBalanceDuplicateAndUnsupportedArguments() {
    CliArgumentsException duplicateAccountCode =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "1000",
                      "--account-code",
                      "2000"
                    }));
    CliArgumentsException duplicateDateFrom =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "1000",
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
                      "account-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "1000",
                      "--effective-date-to",
                      "2026-04-30",
                      "--effective-date-to",
                      "2026-05-01"
                    }));
    CliArgumentsException unsupported =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "1000",
                      "--extra"
                    }));

    assertEquals("--account-code", duplicateAccountCode.argument());
    assertEquals("Duplicate argument: --account-code", duplicateAccountCode.getMessage());
    assertEquals("--effective-date-from", duplicateDateFrom.argument());
    assertEquals("Duplicate argument: --effective-date-from", duplicateDateFrom.getMessage());
    assertEquals("--effective-date-to", duplicateDateTo.argument());
    assertEquals("Duplicate argument: --effective-date-to", duplicateDateTo.getMessage());
    assertEquals("--extra", unsupported.argument());
    assertEquals("Unsupported argument: --extra", unsupported.getMessage());
  }
}
