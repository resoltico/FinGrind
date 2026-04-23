package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for shared read-command query validation in {@link CliArguments}. */
class CliReadCommonArgumentValidationTest extends CliArgumentParsingTestSupport {
  @Test
  void parse_rejectsReversedEffectiveDateRangesForReadCommands() {
    CliArgumentsException accountLedgerException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-ledger",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "1000",
                      "--effective-date-from",
                      "2026-04-30",
                      "--effective-date-to",
                      "2026-04-01"
                    }));
    CliArgumentsException periodSummaryException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "period-summary",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--effective-date-from",
                      "2026-04-30",
                      "--effective-date-to",
                      "2026-04-01"
                    }));

    assertEquals("invalid-request", accountLedgerException.code());
    assertEquals("--effective-date-from", accountLedgerException.argument());
    assertEquals(
        "effectiveDateFrom must be on or before effectiveDateTo.",
        accountLedgerException.getMessage());
    assertEquals("invalid-request", periodSummaryException.code());
    assertEquals("--effective-date-from", periodSummaryException.argument());
    assertEquals(
        "effectiveDateFrom must be on or before effectiveDateTo.",
        periodSummaryException.getMessage());
  }

  @Test
  void parse_rejectsBlankAccountCodeAgainstAccountCodeArgumentForReadCommands() {
    CliArgumentsException listPostingsException =
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
                      "",
                      "--effective-date-to",
                      "2026-04-30"
                    }));
    CliArgumentsException accountBalanceException =
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
                      "",
                      "--effective-date-to",
                      "2026-04-30"
                    }));
    CliArgumentsException accountLedgerException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "account-ledger",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--account-code",
                      "",
                      "--effective-date-to",
                      "2026-04-30"
                    }));

    assertEquals("--account-code", listPostingsException.argument());
    assertEquals("--account-code", accountBalanceException.argument());
    assertEquals("--account-code", accountLedgerException.argument());
  }

  @Test
  void parse_rejectsDuplicateAndInvalidPdfOutputPaths() {
    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "trial-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--pdf-out",
                      "one.pdf",
                      "--pdf-out",
                      "two.pdf"
                    }));

    assertEquals("--pdf-out", duplicate.failure().argument());
    assertTrue(duplicate.failure().message().contains("Duplicate argument"));

    CliArgumentsException invalidPath =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "trial-balance",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--effective-date-to",
                      "2026-04-30",
                      "--pdf-out",
                      "\u0000bad"
                    }));

    assertEquals("--pdf-out", invalidPath.failure().argument());
    assertTrue(invalidPath.failure().message().contains("valid filesystem path"));
    assertTrue(invalidPath.failure().message().contains("\u0000bad"));
  }

  @Test
  void parse_rejectsInvalidQueryOptionValues() {
    CliArgumentsException invalidLimit =
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
                      "nope"
                    }));
    CliArgumentsException invalidDate =
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
                      "not-a-date"
                    }));

    assertEquals("--limit", invalidLimit.argument());
    assertInstanceOf(NumberFormatException.class, invalidLimit.getCause());
    assertEquals("--effective-date-from", invalidDate.argument());
    assertInstanceOf(java.time.DateTimeException.class, invalidDate.getCause());
  }
}
