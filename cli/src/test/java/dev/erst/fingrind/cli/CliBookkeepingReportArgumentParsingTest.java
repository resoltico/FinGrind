package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for bookkeeping-report CLI argument parsing. */
class CliBookkeepingReportArgumentParsingTest {
  @Test
  void parse_assignsDefaultAndExplicitOutputModesForBookkeepingReports() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    TrialBalance defaultTrialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    TrialBalance trialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    AccountLedger defaultAccountLedger =
        assertInstanceOf(
            AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    AccountLedger accountLedger =
        assertInstanceOf(
            AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "text"
                }));
    PeriodSummary periodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    PeriodSummary defaultPeriodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30"
                }));

    assertEquals(OutputMode.TEXT, defaultTrialBalance.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), trialBalance.query().effectiveDateAsOf());
    assertEquals(OutputMode.TEXT, defaultAccountLedger.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), accountLedger.query().effectiveDateFrom());
    assertEquals(OutputMode.TEXT, defaultPeriodSummary.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), periodSummary.query().effectiveDateTo());
  }

  @Test
  void parse_rejectsInvalidTrialBalanceArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "pdf"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--effective-date-as-of",
                  "2026-05-01"
                }));
  }

  @Test
  void parse_rejectsInvalidAccountLedgerArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-from",
                  "2026-04-02"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
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
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--limit",
                  "10",
                  "--limit",
                  "20"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
  }

  @Test
  void parse_rejectsInvalidPeriodSummaryArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-start",
                  "2026-04-02",
                  "--period-end",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--period-end",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
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
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-start",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--period-end",
                  "2026-04-30"
                }));
  }
}
