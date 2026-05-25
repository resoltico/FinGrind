package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for transfer-period-result bookkeeping generation and rejection rules. */
class PeriodResultTransferServiceTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final LocalDate OPENING_DATE = LocalDate.parse("2026-04-01");
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final ReportingPeriod PERIOD = new ReportingPeriod(PERIOD_DATE, PERIOD_DATE);
  private static final ReportingPeriod FULL_PERIOD = new ReportingPeriod(OPENING_DATE, PERIOD_DATE);

  @Test
  void transferPeriodResult_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, PERIOD);

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_rejectsMissingRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
      seedProfitAndLossPosting(bookSession);

      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, FULL_PERIOD);

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
                  FinancialPositionLineClassification.RESULT_HOLDING, List.of())),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_rejectsInactiveRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      bookSession.deactivateAccount(new AccountCode("3200"));
      seedProfitAndLossPosting(bookSession);

      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, PERIOD);

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  List.of(new AccountCode("3200")))),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_rejectsAmbiguousRetainedEarningsCandidates() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings A",
          AccountType.EQUITY,
          AccountRole.ORDINARY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
      seedProfitAndLossPosting(bookSession);

      declareAccount(
          bookSession,
          "3210",
          "Retained Earnings Duplicate",
          AccountType.EQUITY,
          AccountRole.ORDINARY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));

      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, PERIOD);

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  List.of(new AccountCode("3200"), new AccountCode("3210")))),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_acceptsOnePolicySelectedRetainedEarningsCandidate() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings A",
          AccountType.EQUITY,
          AccountRole.ORDINARY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
      declareAccount(
          bookSession,
          "3210",
          "General Equity",
          AccountType.EQUITY,
          AccountRole.ORDINARY,
          financialPositionTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY));
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult transferredPeriodResult =
          assertInstanceOf(
                  PeriodResultTransferOutcome.Transferred.class,
                  transferPeriodResult(bookSession, FULL_PERIOD))
              .transferredPeriodResult();

      assertEquals(new AccountCode("3200"), transferredPeriodResult.resultHoldingAccountCode());
    }
  }

  @Test
  void transferPeriodResult_allowsFirstCloseToStartBeforeEarliestPosting() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      commitPosting(
          bookSession,
          "posting-sale",
          "idem-sale",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "50.00"),
              line("4000", JournalLine.EntrySide.CREDIT, "50.00")));

      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, FULL_PERIOD);
      dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult transferredPeriodResult =
          assertInstanceOf(PeriodResultTransferOutcome.Transferred.class, outcome)
              .transferredPeriodResult();

      assertEquals(FULL_PERIOD, transferredPeriodResult.reportingPeriod());
    }
  }

  @Test
  void transferPeriodResult_rejectsNonContiguousStartAfterExistingClose() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);
      assertInstanceOf(
          PeriodResultTransferOutcome.Transferred.class,
          transferPeriodResult(bookSession, FULL_PERIOD));

      PeriodResultTransferOutcome outcome =
          transferPeriodResult(
              bookSession,
              clockAt(PERIOD_DATE.plusDays(2)),
              new ReportingPeriod(PERIOD_DATE.plusDays(2), PERIOD_DATE.plusDays(2)));

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.PeriodResultTransferMustStartAt(
                  PERIOD_DATE.plusDays(1))),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_allowsEmptyDraftWhenNoProfitAndLossAccountsMoved() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      commitPosting(
          bookSession,
          "posting-open",
          "idem-open",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "50.00"),
              line("3000", JournalLine.EntrySide.CREDIT, "50.00")));

      PeriodResultTransferOutcome outcome = transferPeriodResult(bookSession, PERIOD);
      dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult transferredPeriodResult =
          assertInstanceOf(PeriodResultTransferOutcome.Transferred.class, outcome)
              .transferredPeriodResult();

      assertEquals(1, transferredPeriodResult.transferOrder());
      assertEquals(List.of(), transferredPeriodResult.transferPostingIds());
    }
  }

  @Test
  void transferPeriodResult_generatesOnePeriodResultTransferPostingAndIgnoresPriorCloseFacts() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult firstClose =
          assertInstanceOf(
                  PeriodResultTransferOutcome.Transferred.class,
                  transferPeriodResult(bookSession, FULL_PERIOD))
              .transferredPeriodResult();
      CommittedPosting closingPosting =
          bookSession.findPosting(firstClose.transferPostingIds().getFirst()).orElseThrow();

      assertEquals(PostingKind.PERIOD_RESULT_TRANSFER, closingPosting.postingKind());
      assertEquals(
          new JournalEntry(
              PERIOD_DATE,
              List.of(
                  line("4000", JournalLine.EntrySide.DEBIT, "120.00"),
                  line("5000", JournalLine.EntrySide.CREDIT, "45.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "75.00"))),
          closingPosting.journalEntry());

      PeriodResultTransferOutcome secondClose =
          transferPeriodResult(
              bookSession,
              clockAt(PERIOD_DATE.plusDays(1)),
              new ReportingPeriod(PERIOD_DATE.plusDays(1), PERIOD_DATE.plusDays(1)));

      assertEquals(
          new PeriodResultTransferOutcome.Transferred(
              new dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult(
                  2,
                  new ReportingPeriod(PERIOD_DATE.plusDays(1), PERIOD_DATE.plusDays(1)),
                  new AccountCode("3200"),
                  List.of(),
                  clockAt(PERIOD_DATE.plusDays(1)).instant(),
                  List.of())),
          secondClose);
    }
  }

  @Test
  void
      transferPeriodResult_skipsNonStandardUnknownAndZeroedTemporaryBuckets_andOrdersGeneratedDraftsByCurrency() {
    RecordingCloseBook book = new RecordingCloseBook();
    book.accounts =
        List.of(
            account("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY),
            account(
                "3200",
                "Retained Earnings",
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
            account("4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY),
            account("5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY));
    book.postings =
        List.of(
            posting(
                "existing-close",
                PostingKind.PERIOD_RESULT_TRANSFER,
                dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "1.00"),
                    moneyLine("3200", JournalLine.EntrySide.CREDIT, "EUR", "1.00"))),
            posting(
                "eur-revenue-credit",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-revenue-debit",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-unknown",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "9.00"),
                    moneyLine("9999", JournalLine.EntrySide.CREDIT, "EUR", "9.00"))),
            posting(
                "usd-revenue",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
            posting(
                "bhd-expense",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                PERIOD_DATE,
                List.of(
                    moneyLine("5000", JournalLine.EntrySide.DEBIT, "BHD", "7.000"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"))));

    PeriodResultTransferOutcome outcome =
        new PeriodResultTransferService(
                book, book, book, book, new SequencePostingIdGenerator(), FIXED_CLOCK)
            .transferPeriodResult(PERIOD);
    dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult transferredPeriodResult =
        assertInstanceOf(PeriodResultTransferOutcome.Transferred.class, outcome)
            .transferredPeriodResult();

    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult(
            1,
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(new PostingId("generated-1"), new PostingId("generated-2"))),
        transferredPeriodResult);
    assertEquals(
        new PeriodResultTransferDraft(
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(
                new PostingDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("5000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"),
                            moneyLine("3200", JournalLine.EntrySide.DEBIT, "BHD", "7.000"))),
                    PostingLineageModel.direct(),
                    PostingKind.PERIOD_RESULT_TRANSFER,
                    dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
                    generatedPeriodResultTransferEvidence("BHD"),
                    periodResultTransferProvenance("BHD")),
                new PostingDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("4000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                            moneyLine("3200", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
                    PostingLineageModel.direct(),
                    PostingKind.PERIOD_RESULT_TRANSFER,
                    dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
                    generatedPeriodResultTransferEvidence("USD"),
                    periodResultTransferProvenance("USD")))),
        book.recordedDraft);
  }

  @Test
  void transferPeriodResult_rejectsFutureEffectiveDateTo() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);

      PeriodResultTransferOutcome outcome =
          transferPeriodResult(
              bookSession,
              new ReportingPeriod(
                  PERIOD_DATE,
                  FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)));

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.PeriodResultTransferFutureDate(
                  PERIOD_DATE.plusDays(1))),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_rejectsRangesThatCrossTheConfiguredFiscalYearBoundary() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      Clock clock = clockAt(LocalDate.parse("2027-01-20"));

      PeriodResultTransferOutcome outcome =
          transferPeriodResult(
              bookSession,
              clock,
              new ReportingPeriod(LocalDate.parse("2026-12-15"), LocalDate.parse("2027-01-15")));

      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
                  LocalDate.parse("2026-12-15"),
                  LocalDate.parse("2027-01-15"),
                  bookIdentity().fiscalYearStart())),
          outcome);
    }
  }

  @Test
  void transferPeriodResult_offsetsContraRevenueAndContraExpenseIntoBalancedRetainedEarnings() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
      declareAccount(
          bookSession, "1090", "Purchase Returns", AccountType.ASSET, AccountRole.CONTRA);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings",
          AccountType.EQUITY,
          AccountRole.ORDINARY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
      declareAccount(
          bookSession, "4000", "Sales Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
      declareAccount(
          bookSession, "4090", "Sales Discounts", AccountType.REVENUE, AccountRole.CONTRA);
      declareAccount(
          bookSession, "5000", "Operating Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
      declareAccount(
          bookSession, "5090", "Purchase Returns", AccountType.EXPENSE, AccountRole.CONTRA);
      commitPosting(
          bookSession,
          "posting-open",
          "idem-open",
          OPENING_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "100.00"),
              line("3000", JournalLine.EntrySide.CREDIT, "100.00")));
      commitPosting(
          bookSession,
          "posting-sale",
          "idem-sale",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "120.00"),
              line("4000", JournalLine.EntrySide.CREDIT, "120.00")));
      commitPosting(
          bookSession,
          "posting-sales-discount",
          "idem-sales-discount",
          PERIOD_DATE,
          List.of(
              line("4090", JournalLine.EntrySide.DEBIT, "20.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "20.00")));
      commitPosting(
          bookSession,
          "posting-expense",
          "idem-expense",
          PERIOD_DATE,
          List.of(
              line("5000", JournalLine.EntrySide.DEBIT, "30.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "30.00")));
      commitPosting(
          bookSession,
          "posting-purchase-return",
          "idem-purchase-return",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "5.00"),
              line("5090", JournalLine.EntrySide.CREDIT, "5.00")));

      dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult transferredPeriodResult =
          assertInstanceOf(
                  PeriodResultTransferOutcome.Transferred.class,
                  transferPeriodResult(bookSession, FULL_PERIOD))
              .transferredPeriodResult();
      CommittedPosting closingPosting =
          bookSession
              .findPosting(transferredPeriodResult.transferPostingIds().getFirst())
              .orElseThrow();

      assertEquals(
          new JournalEntry(
              PERIOD_DATE,
              List.of(
                  line("4000", JournalLine.EntrySide.DEBIT, "120.00"),
                  line("4090", JournalLine.EntrySide.CREDIT, "20.00"),
                  line("5000", JournalLine.EntrySide.CREDIT, "30.00"),
                  line("5090", JournalLine.EntrySide.DEBIT, "5.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "75.00"))),
          closingPosting.journalEntry());
      assertEquals(
          List.of(
              CurrencyBalance.ofTotals(Money.parse("EUR", "0.00"), Money.parse("EUR", "75.00"))),
          transferredPeriodResult.transferredTotals());
    }
  }

  private static PeriodResultTransferService service(InMemoryBookSession bookSession, Clock clock) {
    PostingIdGenerator postingIdGenerator = new SequencePostingIdGenerator();
    return new PeriodResultTransferService(
        bookSession, bookSession, bookSession, bookSession, postingIdGenerator, clock);
  }

  private static PeriodResultTransferOutcome transferPeriodResult(
      InMemoryBookSession bookSession, ReportingPeriod reportingPeriod) {
    return transferPeriodResult(bookSession, FIXED_CLOCK, reportingPeriod);
  }

  private static PeriodResultTransferOutcome transferPeriodResult(
      InMemoryBookSession bookSession, Clock clock, ReportingPeriod reportingPeriod) {
    return service(bookSession, clock).transferPeriodResult(reportingPeriod);
  }

  private static Clock clockAt(LocalDate date) {
    return Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  private static InMemoryBookSession openedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT, bookIdentity());
    return bookSession;
  }

  private static void declareRetainedEarningsFixture(InMemoryBookSession bookSession) {
    declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
    declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
    declareAccount(
        bookSession,
        "3200",
        "Retained Earnings",
        AccountType.EQUITY,
        AccountRole.ORDINARY,
        financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
    declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
    declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole) {
    declareAccount(
        bookSession,
        accountCode,
        accountName,
        accountType,
        accountRole,
        accountTaxonomy(accountType));
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            accountType,
            accountRole,
            accountTaxonomy,
            FIXED_INSTANT));
  }

  private static void seedProfitAndLossPosting(InMemoryBookSession bookSession) {
    commitPosting(
        bookSession,
        "posting-open",
        "idem-open",
        OPENING_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "100.00"),
            line("3000", JournalLine.EntrySide.CREDIT, "100.00")));
    commitPosting(
        bookSession,
        "posting-sale",
        "idem-sale",
        PERIOD_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "120.00"),
            line("4000", JournalLine.EntrySide.CREDIT, "120.00")));
    commitPosting(
        bookSession,
        "posting-expense",
        "idem-expense",
        PERIOD_DATE,
        List.of(
            line("5000", JournalLine.EntrySide.DEBIT, "45.00"),
            line("1000", JournalLine.EntrySide.CREDIT, "45.00")));
  }

  private static RegisteredAccount account(
      String accountCode, String accountName, AccountType accountType, AccountRole accountRole) {
    return account(
        accountCode, accountName, accountType, accountRole, accountTaxonomy(accountType));
  }

  private static RegisteredAccount account(
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    return new RegisteredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountRole,
        accountTaxonomy,
        true,
        FIXED_INSTANT);
  }

  private static JournalLine moneyLine(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse(currencyCode, amount));
  }

  private static CommittedPosting posting(
      String postingId,
      PostingKind postingKind,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        postingEvidence(postingId, postingKind),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-" + postingId),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static CommittedProvenance periodResultTransferProvenance(String currencyCode) {
    String closeToken = PERIOD_DATE + ":" + PERIOD_DATE + ":" + FIXED_INSTANT.toEpochMilli();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("system:periodResultTransfer"),
            ActorType.SYSTEM,
            new CommandId("periodResultTransfer:" + closeToken + ":" + currencyCode),
            new IdempotencyKey("periodResultTransfer:" + closeToken + ":" + currencyCode),
            new CausationId("periodResultTransfer:" + closeToken),
            Optional.of(new CorrelationId("periodResultTransfer:" + closeToken)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.SYSTEM);
  }

  private static void commitPosting(
      InMemoryBookSession bookSession,
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(postingId),
            new JournalEntry(effectiveDate, lines),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
            accountingEvidence(idempotencyKey),
            new CommittedProvenance(
                new RequestProvenance(
                    new dev.erst.fingrind.core.ActorId("actor-" + postingId),
                    dev.erst.fingrind.core.ActorType.AGENT,
                    new dev.erst.fingrind.core.CommandId("command-" + postingId),
                    new dev.erst.fingrind.core.IdempotencyKey(idempotencyKey),
                    new dev.erst.fingrind.core.CausationId("cause-" + postingId),
                    Optional.empty()),
                FIXED_INSTANT,
                SourceChannel.CLI));
    assertInstanceOf(PostingCommitResult.Committed.class, bookSession.commit(posting));
  }

  private static dev.erst.fingrind.core.AccountingEvidence postingEvidence(
      String token, PostingKind postingKind) {
    if (postingKind == PostingKind.PERIOD_RESULT_TRANSFER) {
      return generatedEvidence(token, "period-result-transfer-plan");
    }
    return accountingEvidence("idem-" + token);
  }

  private static dev.erst.fingrind.core.AccountingEvidence generatedPeriodResultTransferEvidence(
      String currencyCode) {
    String closeToken =
        "%s:%s:%s:%d"
            .formatted(
                PERIOD.effectiveDateFrom(),
                PERIOD.effectiveDateTo(),
                currencyCode,
                FIXED_INSTANT.toEpochMilli());
    return new dev.erst.fingrind.core.AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("periodResultTransfer:" + closeToken),
                new SourceDocumentType("period-result-transfer-plan"),
                PERIOD.effectiveDateTo(),
                FIXED_INSTANT,
                new StorageLocator("system://period-result-transfer/" + closeToken),
                new ContentSha256(sha256Hex(closeToken)))),
        List.of());
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
    }
  }

  /** Deterministic posting id generator for generated transfer postings. */
  private static final class SequencePostingIdGenerator implements PostingIdGenerator {
    private int nextValue = 1;

    @Override
    public PostingId nextPostingId() {
      int currentValue = nextValue;
      nextValue = currentValue + 1;
      return new PostingId("period-result-transfer-" + currentValue);
    }
  }

  /** Recording book double that captures generated close drafts and account/posting inputs. */
  private static final class RecordingCloseBook
      implements BookLifecycleReader,
          AccountCatalogStore,
          PostingRangeStore,
          PeriodResultTransferStore {
    private List<RegisteredAccount> accounts = List.of();
    private List<CommittedPosting> postings = List.of();
    private PeriodResultTransferDraft recordedDraft =
        new PeriodResultTransferDraft(
            PERIOD, new AccountCode("3200"), List.of(), FIXED_INSTANT, List.of());

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(
          BookFormatContract.APPLICATION_ID,
          BookFormatContract.FORMAT_VERSION,
          BookFormatContract.FORMAT_VERSION,
          FIXED_INSTANT);
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return accounts;
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      throw unsupported();
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return postings.stream()
          .filter(posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
          .toList();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return postings.stream()
          .map(posting -> posting.journalEntry().effectiveDate())
          .min(LocalDate::compareTo);
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public PeriodResultTransferOutcome transferPeriodResult(
        PeriodResultTransferDraft periodResultTransferDraft,
        PostingIdGenerator postingIdGenerator) {
      recordedDraft = periodResultTransferDraft;
      List<PostingId> generatedPostingIds = new ArrayList<>();
      for (int index = 0; index < periodResultTransferDraft.closingPostings().size(); index++) {
        generatedPostingIds.add(new PostingId("generated-" + (index + 1)));
      }
      return new PeriodResultTransferOutcome.Transferred(
          new dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult(
              1,
              periodResultTransferDraft.reportingPeriod(),
              periodResultTransferDraft.resultHoldingAccountCode(),
              periodResultTransferDraft.transferredTotals(),
              periodResultTransferDraft.transferredAt(),
              generatedPostingIds));
    }

    private static AssertionError unsupported() {
      return new AssertionError("This close-service test double does not support that seam.");
    }
  }
}
