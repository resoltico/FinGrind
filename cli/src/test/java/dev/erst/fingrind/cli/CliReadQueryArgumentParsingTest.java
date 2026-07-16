package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliReadQueryArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsListAccountsForValidBookOnlyCommand() {
    ListAccounts command =
        assertInstanceOf(
            ListAccounts.class,
            CliArguments.parse(
                new String[] {
                  "list-accounts", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(new ListAccountsQuery(50, Optional.empty()), command.query());
  }

  @Test
  void parse_accountLedgerAcceptsAValidatedKeysetWindowWhileAccountBalanceRejectsIt() {
    AccountLedgerPageCursor cursor =
        new AccountLedgerPageCursor(
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T12:00:00Z"),
            new PostingId("posting-1"));
    AccountLedger command =
        assertInstanceOf(
            AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--account-code",
                  "1000",
                  "--limit",
                  "1",
                  "--cursor",
                  cursor.wireValue()
                }));

    assertEquals(1, command.query().limit());
    assertEquals(Optional.of(cursor), command.query().cursor());
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
                  "--limit",
                  "1"
                }));
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
                  "--cursor",
                  "not-a-cursor"
                }));
  }

  @Test
  void parse_returnsListAccountsWithCursorOption() {
    AccountPageCursor cursor = new AccountPageCursor(new AccountCode("1000"));
    ListAccounts command =
        assertInstanceOf(
            ListAccounts.class,
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
  void parse_rejectsInvalidTaxRegistrationCursor() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "list-tax-registrations",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--cursor",
                      "not-a-valid-cursor"
                    }));

    assertEquals("--cursor", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Unsupported tax registration page cursor"));
    assertEquals(CliOperationText.listTaxRegistrationsCursorRepairHint(), exception.hint());
  }

  @Test
  void parse_returnsInspectBookAndGetPostingForValidBookOnlyCommands() {
    InspectBook inspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));
    GetPosting getPosting =
        assertInstanceOf(
            GetPosting.class,
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
    ListPostings command =
        assertInstanceOf(
            ListPostings.class,
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
  void parse_acceptsOneSidedEffectiveDateRangesForPostingAndAccountQueries() {
    ListPostings listPostings =
        assertInstanceOf(
            ListPostings.class,
            CliArguments.parse(
                new String[] {
                  "list-postings",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01"
                }));
    AccountBalance accountBalance =
        assertInstanceOf(
            AccountBalance.class,
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
                  "2026-04-01"
                }));

    assertEquals(
        new ListPostingsQuery(
            Optional.empty(), LocalDate.parse("2026-04-01"), null, 50, Optional.empty()),
        listPostings.query());
    assertEquals(
        new AccountBalanceQuery(new AccountCode("1000"), LocalDate.parse("2026-04-01"), null),
        accountBalance.query());
  }

  @Test
  void parse_returnsAccountBalanceWithDateFilters() {
    AccountBalance command =
        assertInstanceOf(
            AccountBalance.class,
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
    AccountBalance command =
        assertInstanceOf(
            AccountBalance.class,
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
                  "text"
                }));

    assertEquals(OutputMode.TEXT, command.output().outputMode());
    assertEquals(Path.of("reports/balance.pdf"), command.output().pdfOutPath());
  }

  @Test
  void parse_supportsOutputModesForReadAndReportCommands() {
    InspectBook inspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--output",
                  "text"
                }));
    TrialBalance trialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--pdf-out",
                  "reports/trial-balance.pdf",
                  "--output",
                  "json"
                }));
    AccountLedger accountLedger =
        assertInstanceOf(
            AccountLedger.class,
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
                  "text"
                }));
    PeriodSummary periodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--pdf-out",
                  "reports/april-summary.pdf",
                  "--output",
                  "json"
                }));

    assertEquals(OutputMode.TEXT, inspectBook.outputMode());
    assertEquals(
        new TrialBalanceQuery(
            Optional.of(LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            ComparativeSelection.none()),
        trialBalance.query());
    assertEquals(OutputMode.JSON, trialBalance.output().outputMode());
    assertEquals(Path.of("reports/trial-balance.pdf"), trialBalance.output().pdfOutPath());
    assertEquals(
        new AccountLedgerQuery(
            new AccountCode("1000"),
            dev.erst.fingrind.core.EffectiveDateRange.of(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            50,
            Optional.empty()),
        accountLedger.query());
    assertEquals(OutputMode.TEXT, accountLedger.output().outputMode());
    assertEquals(Path.of("reports/cash-ledger.pdf"), accountLedger.output().pdfOutPath());
    assertEquals(
        new PeriodSummaryQuery(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        periodSummary.query());
    assertEquals(OutputMode.JSON, periodSummary.output().outputMode());
    assertEquals(Path.of("reports/april-summary.pdf"), periodSummary.output().pdfOutPath());
  }

  @Test
  void parse_taxObligationRequiresAllRequiredArguments() {
    CliArgumentsException missingTaxRegistrationId =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "tax-obligation",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--period-start",
                      "2026-04-01",
                      "--period-end",
                      "2026-04-30"
                    }));
    CliArgumentsException missingPeriodStart =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "tax-obligation",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--tax-registration-id",
                      "vat-lv",
                      "--period-end",
                      "2026-04-30"
                    }));
    CliArgumentsException missingPeriodEnd =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "tax-obligation",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--tax-registration-id",
                      "vat-lv",
                      "--period-start",
                      "2026-04-01"
                    }));

    assertEquals("--tax-registration-id", missingTaxRegistrationId.argument());
    assertTrue(
        Objects.requireNonNull(missingTaxRegistrationId.getMessage())
            .contains("A --tax-registration-id argument is required."));
    assertEquals("--period-start", missingPeriodStart.argument());
    assertTrue(
        Objects.requireNonNull(missingPeriodStart.getMessage())
            .contains("A --period-start argument is required."));
    assertEquals("--period-end", missingPeriodEnd.argument());
    assertTrue(
        Objects.requireNonNull(missingPeriodEnd.getMessage())
            .contains("A --period-end argument is required."));
  }

  @Test
  void parse_taxObligationAcceptsPdfArtifactOutput() {
    TaxObligation command =
        assertInstanceOf(
            TaxObligation.class,
            CliArguments.parse(
                new String[] {
                  "tax-obligation",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--tax-registration-id",
                  "vat-lv",
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--output",
                  "text",
                  "--pdf-out",
                  "reports/tax-obligation.pdf"
                }));

    assertEquals(
        new dev.erst.fingrind.contract.tax.TaxObligationQuery(
            new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30")),
        command.query());
    assertEquals(OutputMode.TEXT, command.output().outputMode());
    assertEquals(Path.of("reports/tax-obligation.pdf"), command.output().pdfOutPath());
  }

  @Test
  void parse_rejectsCsvStdoutWhenPdfArtifactRequestedForReadQueryReports() {
    for (String[] arguments :
        List.of(
            new String[] {
              "tax-obligation",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--tax-registration-id",
              "vat-lv",
              "--period-start",
              "2026-04-01",
              "--period-end",
              "2026-04-30",
              "--output",
              "csv",
              "--pdf-out",
              "reports/tax-obligation.pdf"
            },
            new String[] {
              "account-balance",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--account-code",
              "1000",
              "--output",
              "csv",
              "--pdf-out",
              "reports/account-balance.pdf"
            },
            new String[] {
              "trial-balance",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output",
              "csv",
              "--pdf-out",
              "reports/trial-balance.pdf"
            },
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
              "--output",
              "csv",
              "--pdf-out",
              "reports/account-ledger.pdf"
            },
            new String[] {
              "period-summary",
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
              "reports/period-summary.pdf"
            })) {
      CliArgumentsException unsupported =
          assertThrows(CliArgumentsException.class, () -> CliArguments.parse(arguments));
      assertEquals("--output", unsupported.argument());
    }
  }

  @Test
  void parse_trialBalanceAcceptsExplicitPostingCoverage() {
    TrialBalance command =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--posting-coverage",
                  "non-closing-postings"
                }));

    assertEquals(
        new TrialBalanceQuery(
            Optional.of(LocalDate.parse("2026-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            ComparativeSelection.none()),
        command.query());
  }

  @Test
  void parse_accountStyleAndSummaryReportsAcceptExplicitPostingCoverage() {
    AccountBalance accountBalance =
        assertInstanceOf(
            AccountBalance.class,
            CliArguments.parse(
                new String[] {
                  "account-balance",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--account-code",
                  "1000",
                  "--posting-coverage",
                  "non-closing-postings"
                }));
    AccountLedger accountLedger =
        assertInstanceOf(
            AccountLedger.class,
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
                  "--posting-coverage",
                  "non-closing-postings"
                }));
    PeriodSummary periodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--posting-coverage",
                  "non-closing-postings"
                }));

    assertEquals(
        new AccountBalanceQuery(
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.NON_CLOSING_POSTINGS),
        assertInstanceOf(
                AccountBalance.class,
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
                      "2026-04-30",
                      "--posting-coverage",
                      "non-closing-postings"
                    }))
            .query());
    assertEquals(
        new AccountBalanceQuery(
            new AccountCode("1000"), null, null, PostingCoverage.NON_CLOSING_POSTINGS),
        accountBalance.query());
    assertEquals(
        new AccountLedgerQuery(
            new AccountCode("1000"),
            dev.erst.fingrind.core.EffectiveDateRange.of(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            50,
            Optional.empty()),
        accountLedger.query());
    assertEquals(
        new PeriodSummaryQuery(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.NON_CLOSING_POSTINGS),
        periodSummary.query());
  }

  @Test
  void parse_supportsSelectableOutputModesForBookQueries() {
    GetPosting getPosting =
        assertInstanceOf(
            GetPosting.class,
            CliArguments.parse(
                new String[] {
                  "get-posting",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--posting-id",
                  "posting-1",
                  "--output",
                  "text"
                }));
    ListAccounts listAccounts =
        assertInstanceOf(
            ListAccounts.class,
            CliArguments.parse(
                new String[] {
                  "list-accounts",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--output",
                  "csv"
                }));
    ListPostings listPostings =
        assertInstanceOf(
            ListPostings.class,
            CliArguments.parse(
                new String[] {
                  "list-postings",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--output",
                  "text"
                }));

    assertEquals(OutputMode.TEXT, getPosting.outputMode());
    assertEquals(OutputMode.CSV, listAccounts.outputMode());
    assertEquals(OutputMode.TEXT, listPostings.outputMode());
  }

  @Test
  void parse_returnsListPostingsWithDefaultPagingWhenOmitted() {
    ListPostings command =
        assertInstanceOf(
            ListPostings.class,
            CliArguments.parse(
                new String[] {
                  "list-postings", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(
        new ListPostingsQuery(Optional.empty(), null, null, 50, Optional.empty()), command.query());
  }
}
