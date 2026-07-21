package dev.erst.fingrind.report.pdf;

import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.BOOK_IDENTITY;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.PDF_REPORT_SERVICE;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.accountActivityRows;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.assertPdfPageCountAtLeast;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.balance;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.evidence;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.extractedText;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.ledgerEntries;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.money;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.render;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.JournalLine.EntrySide;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused PDF rendering tests for account-ledger and period-summary output families. */
class PdfAccountLedgerAndPeriodSummaryReportTest {
  @Test
  void renderAccountLedgerAndPeriodSummaryPaginateLongTables() throws IOException {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            AccountLedgerPagination.firstPage(50),
            List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
            ledgerEntries(72),
            List.of(balance("EUR", "3600.00", "0.00", "3600.00", BalanceSide.DEBIT)));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.ALL_POSTING_KINDS,
            72,
            144,
            72,
            List.of(
                new PeriodCurrencySummary(
                    balance("EUR", "3600.00", "3600.00", "0.00", BalanceSide.ZERO))),
            accountActivityRows(72));
    byte[] accountLedgerPdf = render(PDF_REPORT_SERVICE, accountLedgerReport);
    byte[] periodSummaryPdf = render(PDF_REPORT_SERVICE, periodSummaryReport);
    assertPdfPageCountAtLeast(accountLedgerPdf, 2);
    assertPdfPageCountAtLeast(periodSummaryPdf, 2);
    String accountLedgerText = extractedText(accountLedgerPdf);
    String periodSummaryText = extractedText(periodSummaryPdf);
    assertTrue(accountLedgerText.contains("Acme Studio"));
    assertTrue(accountLedgerText.contains("All posting kinds"));
    assertTrue(accountLedgerText.contains("Account type"));
    assertTrue(accountLedgerText.contains("Normal balance"));
    assertTrue(accountLedgerText.contains("Effective date from"));
    assertTrue(accountLedgerText.contains("Ledger Entries"));
    assertFalse(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("Closing Balances"));
    assertTrue(accountLedgerText.contains("1 / "));
    assertTrue(periodSummaryText.contains("Acme Studio"));
    assertTrue(periodSummaryText.contains("All posting kinds"));
    assertTrue(periodSummaryText.contains("Account Activity"));
    assertTrue(periodSummaryText.contains("Accounts touched"));
    assertTrue(periodSummaryText.contains("Yes"));
    assertTrue(periodSummaryText.contains("1 / "));
  }

  @Test
  void renderAccountLedgerIncludesMeaningfulOpeningBalancesAndReversalSemantics()
      throws IOException {
    PostingFact reversalPosting =
        new PostingFact(
            new PostingId("019e26ff-0000-7000-8000-000000000001"),
            new JournalEntry(
                LocalDate.parse("2026-04-02"),
                List.of(
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.CREDIT, money("EUR", "100.00")),
                    new JournalLine(
                        REVENUE_ACCOUNT.accountCode(), EntrySide.DEBIT, money("EUR", "100.00")))),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("019e26ff-0000-7000-8000-000000000000")),
                new ReversalReason("duplicate-charge")),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            evidence("idem-reversal"),
            new CommittedProvenance(
                new RequestProvenance(
                    new CommandId("command-reversal"),
                    new IdempotencyKey("idem-reversal"),
                    new CausationId("cause-reversal"),
                    Optional.of(new CorrelationId("corr-reversal"))),
                Instant.parse("2026-04-19T10:15:45Z"),
                SourceChannel.CLI));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            AccountLedgerPagination.firstPage(50),
            List.of(balance("EUR", "250.00", "0.00", "250.00", BalanceSide.DEBIT)),
            List.of(
                new AccountLedgerEntry(
                    reversalPosting,
                    balance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT),
                    money("EUR", "150.00"),
                    BalanceSide.DEBIT)),
            List.of(balance("EUR", "250.00", "100.00", "150.00", BalanceSide.DEBIT)));

    String accountLedgerText = extractedText(render(PDF_REPORT_SERVICE, accountLedgerReport));
    String normalizedAccountLedgerText = accountLedgerText.replace('\n', ' ');

    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("250.00"));
    assertTrue(accountLedgerText.contains("Entry"));
    assertTrue(normalizedAccountLedgerText.contains("Counterpart account codes"));
    assertTrue(accountLedgerText.contains("019e26ff-0000-7000"));
    assertTrue(accountLedgerText.contains("000000000001"));
    assertTrue(accountLedgerText.contains("000000000000"));
    assertFalse(accountLedgerText.contains("..."));
  }

  @Test
  void renderAccountLedgerTreatsCreditOnlyOpeningBalancesAsMeaningful() throws IOException {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            AccountLedgerPagination.firstPage(50),
            List.of(balance("EUR", "0.00", "25.00", "25.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(balance("EUR", "0.00", "25.00", "25.00", BalanceSide.CREDIT)));

    String accountLedgerText = extractedText(render(PDF_REPORT_SERVICE, accountLedgerReport));

    assertTrue(accountLedgerText.contains("Opening Balances"));
    assertTrue(accountLedgerText.contains("25.00"));
  }

  @Test
  void renderAccountLedgerPublishesSelfCounterpartWhenPostingTouchesOnlyTheSelectedAccount()
      throws IOException {
    PostingFact selfPosting =
        new PostingFact(
            new PostingId("019e26ff-0000-7000-8000-000000000009"),
            new JournalEntry(
                LocalDate.parse("2026-04-03"),
                List.of(
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.DEBIT, money("EUR", "10.00")),
                    new JournalLine(
                        CASH_ACCOUNT.accountCode(), EntrySide.CREDIT, money("EUR", "10.00")))),
            PostingLineage.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            evidence("idem-self"),
            new CommittedProvenance(
                new RequestProvenance(
                    new CommandId("command-self"),
                    new IdempotencyKey("idem-self"),
                    new CausationId("cause-self"),
                    Optional.empty()),
                Instant.parse("2026-04-19T10:15:45Z"),
                SourceChannel.CLI));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            BOOK_IDENTITY,
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            AccountLedgerPagination.firstPage(50),
            List.of(),
            List.of(
                new AccountLedgerEntry(
                    selfPosting,
                    balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO),
                    money("EUR", "0.00"),
                    BalanceSide.ZERO)),
            List.of(balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO)));

    String accountLedgerText = extractedText(render(PDF_REPORT_SERVICE, accountLedgerReport));

    assertTrue(accountLedgerText.contains("(self)"));
  }
}
