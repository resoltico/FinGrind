package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliReadQueryArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsListAccountsForValidBookOnlyCommand() {
    CliCommand.ListAccounts command =
        assertInstanceOf(
            CliCommand.ListAccounts.class,
            CliArguments.parse(
                new String[] {
                  "list-accounts", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(new ListAccountsQuery(50, Optional.empty()), command.query());
  }

  @Test
  void parse_returnsListAccountsWithCursorOption() {
    AccountPageCursor cursor = new AccountPageCursor(new AccountCode("1000"));
    CliCommand.ListAccounts command =
        assertInstanceOf(
            CliCommand.ListAccounts.class,
            CliArguments.parse(
                new String[] {
                  "list-accounts",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--limit",
                  "25",
                  "--cursor",
                  cursor.wireValue()
                }));

    assertEquals(new ListAccountsQuery(25, Optional.of(cursor)), command.query());
  }

  @Test
  void parse_returnsInspectBookAndGetPostingForValidBookOnlyCommands() {
    CliCommand.InspectBook inspectBook =
        assertInstanceOf(
            CliCommand.InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));
    CliCommand.GetPosting getPosting =
        assertInstanceOf(
            CliCommand.GetPosting.class,
            CliArguments.parse(
                new String[] {
                  "get-posting",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--posting-id",
                  "posting-1"
                }));

    assertEquals(Path.of("book.sqlite"), inspectBook.bookAccess().bookFilePath());
    assertEquals(new PostingId("posting-1"), getPosting.postingId());
  }

  @Test
  void parse_returnsListPostingsWithFiltersAndPagingOptions() {
    PostingPageCursor cursor =
        new PostingPageCursor(
            LocalDate.parse("2026-04-15"),
            Instant.parse("2026-04-15T10:15:30Z"),
            new PostingId("posting-20"));
    CliCommand.ListPostings command =
        assertInstanceOf(
            CliCommand.ListPostings.class,
            CliArguments.parse(
                new String[] {
                  "list-postings",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--limit",
                  "10",
                  "--cursor",
                  cursor.wireValue()
                }));

    assertEquals(
        new ListPostingsQuery(
            Optional.of(new AccountCode("1000")),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            10,
            Optional.of(cursor)),
        command.query());
  }

  @Test
  void parse_returnsAccountBalanceWithDateFilters() {
    CliCommand.AccountBalance command =
        assertInstanceOf(
            CliCommand.AccountBalance.class,
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
                  "--effective-date-to",
                  "2026-04-30"
                }));

    assertEquals(
        new AccountBalanceQuery(
            new AccountCode("1000"), LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        command.query());
  }

  @Test
  void parse_returnsAccountBalanceWithPdfOutput() {
    CliCommand.AccountBalance command =
        assertInstanceOf(
            CliCommand.AccountBalance.class,
            CliArguments.parse(
                new String[] {
                  "account-balance",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--account-code",
                  "1000",
                  "--pdf-out",
                  "reports/balance.pdf",
                  "--output",
                  "human"
                }));

    assertEquals(OutputMode.HUMAN, command.output().outputMode());
    assertEquals(Path.of("reports/balance.pdf"), command.output().pdfOutPath());
  }

  @Test
  void parse_supportsOutputModesForReadAndReportCommands() {
    CliCommand.InspectBook inspectBook =
        assertInstanceOf(
            CliCommand.InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--output",
                  "human"
                }));
    CliCommand.TrialBalance trialBalance =
        assertInstanceOf(
            CliCommand.TrialBalance.class,
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
                  "reports/trial-balance.pdf",
                  "--output",
                  "csv"
                }));
    CliCommand.AccountLedger accountLedger =
        assertInstanceOf(
            CliCommand.AccountLedger.class,
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
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  "reports/cash-ledger.pdf",
                  "--output",
                  "human"
                }));
    CliCommand.PeriodSummary periodSummary =
        assertInstanceOf(
            CliCommand.PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  "reports/april-summary.pdf",
                  "--output",
                  "csv"
                }));

    assertEquals(OutputMode.HUMAN, inspectBook.outputMode());
    assertEquals(
        new TrialBalanceQuery(Optional.of(LocalDate.parse("2026-04-30"))), trialBalance.query());
    assertEquals(OutputMode.CSV, trialBalance.output().outputMode());
    assertEquals(Path.of("reports/trial-balance.pdf"), trialBalance.output().pdfOutPath());
    assertEquals(
        new AccountLedgerQuery(
            new AccountCode("1000"), LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        accountLedger.query());
    assertEquals(OutputMode.HUMAN, accountLedger.output().outputMode());
    assertEquals(Path.of("reports/cash-ledger.pdf"), accountLedger.output().pdfOutPath());
    assertEquals(
        new PeriodSummaryQuery(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        periodSummary.query());
    assertEquals(OutputMode.CSV, periodSummary.output().outputMode());
    assertEquals(Path.of("reports/april-summary.pdf"), periodSummary.output().pdfOutPath());
  }

  @Test
  void parse_returnsListPostingsWithDefaultPagingWhenOmitted() {
    CliCommand.ListPostings command =
        assertInstanceOf(
            CliCommand.ListPostings.class,
            CliArguments.parse(
                new String[] {
                  "list-postings", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(
        new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty()), command.query());
  }
}
