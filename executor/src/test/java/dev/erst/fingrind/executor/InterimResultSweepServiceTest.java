package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
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
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
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
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlan;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for interim-result-sweep bookkeeping generation and rejection rules. */
class InterimResultSweepServiceTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final LocalDate OPENING_DATE = LocalDate.parse("2026-04-01");
  private static final LocalDate BOOK_START_DATE = LocalDate.parse("2026-01-01");
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final ReportingPeriod PERIOD = new ReportingPeriod(PERIOD_DATE, PERIOD_DATE);
  private static final ReportingPeriod FULL_PERIOD =
      new ReportingPeriod(BOOK_START_DATE, PERIOD_DATE);

  @Test
  void interimResultSweep_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, PERIOD);

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          outcome);
    }
  }

  @Test
  void interimResultSweep_throughDateOverloadRejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      InterimResultSweepOutcome outcome =
          service(bookSession, FIXED_CLOCK).interimResultSweep(PERIOD_DATE, TEST_AUTHORIZER);

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          outcome);
    }
  }

  @Test
  void interimResultSweep_rejectsMissingRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY);
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE);
      seedProfitAndLossPosting(bookSession);

      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, FULL_PERIOD);

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new CloseTargetAccountCandidateMissing(
                  FinancialPositionLineClassification.RESULT_HOLDING, List.of())),
          outcome);
    }
  }

  @Test
  void interimResultSweep_rejectsInactiveRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      bookSession.deactivateAccount(new AccountCode("3200"));
      seedProfitAndLossPosting(bookSession);

      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, PERIOD);

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new CloseTargetAccountCandidateMissing(
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  List.of(new AccountCode("3200")))),
          outcome);
    }
  }

  @Test
  void interimResultSweep_rejectsAmbiguousRetainedEarningsCandidates() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY);
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings A",
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE);
      seedProfitAndLossPosting(bookSession);

      declareAccount(
          bookSession,
          "3210",
          "Retained Earnings Duplicate",
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));

      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, PERIOD);

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountCandidateAmbiguous(
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  List.of(new AccountCode("3200"), new AccountCode("3210")))),
          outcome);
    }
  }

  @Test
  void interimResultSweep_acceptsOnePolicySelectedRetainedEarningsCandidate() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY);
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings A",
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
      declareAccount(
          bookSession,
          "3210",
          "General Equity",
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY));
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
          assertInstanceOf(
                  InterimResultSweepOutcome.Transferred.class,
                  interimResultSweep(bookSession, FULL_PERIOD))
              .sweptInterimResult();

      assertEquals(new AccountCode("3200"), sweptInterimResult.resultHoldingAccountCode());
    }
  }

  @Test
  void interimResultSweep_allowsFirstCloseToStartBeforeEarliestPosting() {
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

      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, FULL_PERIOD);
      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
          assertInstanceOf(InterimResultSweepOutcome.Transferred.class, outcome)
              .sweptInterimResult();

      assertEquals(FULL_PERIOD, sweptInterimResult.reportingPeriod());
    }
  }

  @Test
  void interimResultSweep_rejectsNonContiguousStartAfterExistingClose() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);
      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          interimResultSweep(bookSession, FULL_PERIOD));

      InterimResultSweepOutcome outcome =
          interimResultSweep(
              bookSession,
              clockAt(PERIOD_DATE.plusDays(2)),
              new ReportingPeriod(PERIOD_DATE.plusDays(2), PERIOD_DATE.plusDays(2)));

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                  PERIOD_DATE.plusDays(1))),
          outcome);
    }
  }

  @Test
  void interimResultSweep_throughDateOverloadDerivesTheContiguousWindow() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(Instant.parse("2026-04-01T10:15:30Z"), bookIdentity(), List.of());
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
          assertInstanceOf(
                  InterimResultSweepOutcome.Transferred.class,
                  service(bookSession, FIXED_CLOCK)
                      .interimResultSweep(PERIOD_DATE, TEST_AUTHORIZER))
              .sweptInterimResult();

      assertEquals(FULL_PERIOD, sweptInterimResult.reportingPeriod());
    }
  }

  @Test
  void interimResultSweep_allowsEmptyDraftWhenNoProfitAndLossAccountsMoved() {
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

      InterimResultSweepOutcome outcome = interimResultSweep(bookSession, PERIOD);
      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
          assertInstanceOf(InterimResultSweepOutcome.Transferred.class, outcome)
              .sweptInterimResult();

      assertEquals(1, sweptInterimResult.sweepOrder());
      assertEquals(List.of(), sweptInterimResult.sweepPostingIds());
    }
  }

  @Test
  void interimResultSweep_generatesOneInterimResultSweepPostingAndIgnoresPriorCloseFacts() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult firstClose =
          assertInstanceOf(
                  InterimResultSweepOutcome.Transferred.class,
                  interimResultSweep(bookSession, FULL_PERIOD))
              .sweptInterimResult();
      CommittedPosting closingPosting =
          bookSession.findPosting(firstClose.sweepPostingIds().getFirst()).orElseThrow();

      assertEquals(PostingKind.INTERIM_RESULT_SWEEP, closingPosting.postingKind());
      assertEquals(
          new JournalEntry(
              PERIOD_DATE,
              List.of(
                  line("4000", JournalLine.EntrySide.DEBIT, "120.00"),
                  line("5000", JournalLine.EntrySide.CREDIT, "45.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "75.00"))),
          closingPosting.journalEntry());

      InterimResultSweepOutcome secondClose =
          interimResultSweep(
              bookSession,
              clockAt(PERIOD_DATE.plusDays(1)),
              new ReportingPeriod(PERIOD_DATE.plusDays(1), PERIOD_DATE.plusDays(1)));

      assertEquals(
          new InterimResultSweepOutcome.Transferred(
              new dev.erst.fingrind.executor.bookkeeping.SweptInterimResult(
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
      interimResultSweep_skipsNonStandardUnknownAndZeroedTemporaryBuckets_andOrdersGeneratedDraftsByCurrency() {
    RecordingCloseBook book = new RecordingCloseBook();
    book.accounts =
        List.of(
            account("1000", "Cash", AccountType.ASSET),
            account(
                "3200",
                "Retained Earnings",
                AccountType.EQUITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
            account("4000", "Revenue", AccountType.REVENUE),
            account("5000", "Expense", AccountType.EXPENSE));
    book.postings =
        List.of(
            posting(
                "existing-close",
                PostingKind.INTERIM_RESULT_SWEEP,
                dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "1.00"),
                    moneyLine("3200", JournalLine.EntrySide.CREDIT, "EUR", "1.00"))),
            posting(
                "eur-revenue-credit",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-revenue-debit",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-unknown",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "9.00"),
                    moneyLine("9999", JournalLine.EntrySide.CREDIT, "EUR", "9.00"))),
            posting(
                "usd-revenue",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
            posting(
                "bhd-expense",
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                PERIOD_DATE,
                List.of(
                    moneyLine("5000", JournalLine.EntrySide.DEBIT, "BHD", "7.000"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"))));

    InterimResultSweepOutcome outcome =
        new InterimResultSweepService(book, book, new SequencePostingIdGenerator(), FIXED_CLOCK)
            .interimResultSweep(PERIOD, TEST_AUTHORIZER);
    dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
        assertInstanceOf(InterimResultSweepOutcome.Transferred.class, outcome).sweptInterimResult();

    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.SweptInterimResult(
            1,
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(
                new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
                new PostingId("01f60e25-bdd4-3408-90ed-384699ceba97"))),
        sweptInterimResult);
    assertEquals(
        new InterimResultSweepDraft(
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(
                RequestFingerprintTestSupport.fingerprintedDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("5000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"),
                            moneyLine("3200", JournalLine.EntrySide.DEBIT, "BHD", "7.000"))),
                    PostingLineageModel.direct(),
                    PostingKind.INTERIM_RESULT_SWEEP,
                    dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                    generatedInterimResultSweepEvidence("BHD"),
                    interimResultSweepProvenance("BHD")),
                RequestFingerprintTestSupport.fingerprintedDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("4000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                            moneyLine("3200", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
                    PostingLineageModel.direct(),
                    PostingKind.INTERIM_RESULT_SWEEP,
                    dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                    generatedInterimResultSweepEvidence("USD"),
                    interimResultSweepProvenance("USD")))),
        book.recordedDraft);
  }

  @Test
  void interimResultSweep_rejectsFutureEffectiveDateTo() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);

      InterimResultSweepOutcome outcome =
          interimResultSweep(
              bookSession,
              new ReportingPeriod(
                  PERIOD_DATE,
                  FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)));

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
                  PERIOD_DATE.plusDays(1))),
          outcome);
    }
  }

  @Test
  void interimResultSweep_rejectsRangesThatCrossTheConfiguredFiscalYearBoundary() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      Clock clock = clockAt(LocalDate.parse("2027-01-20"));

      InterimResultSweepOutcome outcome =
          interimResultSweep(
              bookSession,
              clock,
              new ReportingPeriod(LocalDate.parse("2026-12-15"), LocalDate.parse("2027-01-15")));

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                  LocalDate.parse("2026-12-15"),
                  LocalDate.parse("2027-01-15"),
                  bookIdentity().fiscalYearStart())),
          outcome);
    }
  }

  private static InterimResultSweepService service(InMemoryBookSession bookSession, Clock clock) {
    PostingIdGenerator postingIdGenerator = new SequencePostingIdGenerator();
    return new InterimResultSweepService(bookSession, bookSession, postingIdGenerator, clock);
  }

  private static InterimResultSweepOutcome interimResultSweep(
      InMemoryBookSession bookSession, ReportingPeriod reportingPeriod) {
    return interimResultSweep(bookSession, FIXED_CLOCK, reportingPeriod);
  }

  private static InterimResultSweepOutcome interimResultSweep(
      InMemoryBookSession bookSession, Clock clock, ReportingPeriod reportingPeriod) {
    return service(bookSession, clock).interimResultSweep(reportingPeriod, TEST_AUTHORIZER);
  }

  private static Clock clockAt(LocalDate date) {
    return Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  private static InMemoryBookSession openedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT, bookIdentity(), List.of());
    return bookSession;
  }

  private static void declareRetainedEarningsFixture(InMemoryBookSession bookSession) {
    declareAccount(bookSession, "1000", "Cash", AccountType.ASSET);
    declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY);
    declareAccount(
        bookSession,
        "3200",
        "Retained Earnings",
        AccountType.EQUITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
    declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE);
    declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE);
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            accountType,
            accountTaxonomy,
            FIXED_INSTANT));
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      AccountType accountType) {
    declareAccount(
        bookSession, accountCode, accountName, accountType, accountTaxonomy(accountType));
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
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    return new RegisteredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy,
        true,
        FIXED_INSTANT);
  }

  private static RegisteredAccount account(
      String accountCode, String accountName, AccountType accountType) {
    return account(accountCode, accountName, accountType, accountTaxonomy(accountType));
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
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        postingEvidence(postingId, postingKind),
        new CommittedProvenance(
            new RequestProvenance(
                dev.erst.fingrind.executor.TestCommandIds.fromLabel("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static CommittedProvenance interimResultSweepProvenance(String currencyCode) {
    String closeToken = PERIOD_DATE + ":" + PERIOD_DATE + ":" + FIXED_INSTANT.toEpochMilli();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            dev.erst.fingrind.executor.TestCommandIds.fromLabel(
                "interimResultSweep:" + closeToken + ":" + currencyCode),
            new IdempotencyKey("interimResultSweep:" + closeToken + ":" + currencyCode),
            new CausationId("interimResultSweep:" + closeToken),
            Optional.of(new CorrelationId("interimResultSweep:" + closeToken)));
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
            new PostingId(
                java.util
                    .UUID
                    .nameUUIDFromBytes(
                        ("fingrind-test-postingid:" + postingId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()),
            new JournalEntry(effectiveDate, lines),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            accountingEvidence(idempotencyKey),
            new CommittedProvenance(
                new RequestProvenance(
                    dev.erst.fingrind.executor.TestCommandIds.fromLabel("command-" + postingId),
                    new dev.erst.fingrind.core.IdempotencyKey(idempotencyKey),
                    new dev.erst.fingrind.core.CausationId("cause-" + postingId),
                    Optional.empty()),
                FIXED_INSTANT,
                SourceChannel.CLI));
    assertInstanceOf(PostingCommitResult.Committed.class, bookSession.commit(posting));
  }

  private static dev.erst.fingrind.core.AccountingEvidence postingEvidence(
      String token, PostingKind postingKind) {
    if (postingKind == PostingKind.INTERIM_RESULT_SWEEP) {
      return generatedEvidence(token, "interim-result-sweep-plan");
    }
    return accountingEvidence("idem-" + token);
  }

  private static dev.erst.fingrind.core.AccountingEvidence generatedInterimResultSweepEvidence(
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
                new SourceDocumentId("interimResultSweep:" + closeToken),
                new SourceDocumentType("interim-result-sweep-plan"),
                PERIOD.effectiveDateTo())),
        List.of());
  }

  /** Deterministic posting id generator for generated transfer postings. */
  private static final class SequencePostingIdGenerator implements PostingIdGenerator {
    private static final List<PostingId> GENERATED_POSTING_IDS =
        List.of(
            new PostingId("f69a68be-269e-3c0f-96ac-2e3f7d806a8b"),
            new PostingId("01f60e25-bdd4-3408-90ed-384699ceba97"));
    private int nextValue = 1;

    @Override
    public PostingId nextPostingId() {
      PostingId nextPostingId = GENERATED_POSTING_IDS.get(nextValue - 1);
      nextValue++;
      return nextPostingId;
    }
  }

  /** Recording book double that captures generated close drafts and account/posting inputs. */
  private static final class RecordingCloseBook
      implements BookLifecycleReader, ReportingPeriodCloseStore {
    private List<RegisteredAccount> accounts = List.of();
    private List<CommittedPosting> postings = List.of();
    private InterimResultSweepDraft recordedDraft =
        new InterimResultSweepDraft(
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
    public InterimResultSweepOutcome interimResultSweep(
        ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        java.time.Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
      if (resultHoldingSelection
          instanceof
          dev.erst.fingrind.executor.bookkeeping.RejectedInterimResultTargetSelection rejected) {
        return new InterimResultSweepOutcome.Rejected(rejected.rejection());
      }
      Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
          planner.closeHorizonRejection(
              reportingPeriod, bookIdentity, currentUtcDate, Optional.empty());
      if (closeHorizonRejection.isPresent()) {
        return new InterimResultSweepOutcome.Rejected(closeHorizonRejection.orElseThrow());
      }
      RegisteredAccount resultHoldingAccount =
          ((dev.erst.fingrind.executor.bookkeeping.AcceptedInterimResultTargetSelection)
                  resultHoldingSelection)
              .account();
      InterimResultSweepPlan closePlan =
          planner.closingPostings(
              reportingPeriod, resultHoldingAccount, accounts, postings, sweptAt);
      InterimResultSweepDraft interimResultSweepDraft =
          new InterimResultSweepDraft(
              reportingPeriod,
              resultHoldingAccount.accountCode(),
              closePlan.sweptTotals(),
              sweptAt,
              closePlan.closingPostings());
      recordedDraft = interimResultSweepDraft;
      List<PostingId> generatedPostingIds = new ArrayList<>();
      for (int index = 0; index < interimResultSweepDraft.closingPostings().size(); index++) {
        generatedPostingIds.add(
            dev.erst.fingrind.executor.TestPostingIds.fromLabel("generated-" + (index + 1)));
      }
      return new InterimResultSweepOutcome.Transferred(
          new dev.erst.fingrind.executor.bookkeeping.SweptInterimResult(
              1,
              interimResultSweepDraft.reportingPeriod(),
              interimResultSweepDraft.resultHoldingAccountCode(),
              interimResultSweepDraft.sweptTotals(),
              interimResultSweepDraft.sweptAt(),
              generatedPostingIds));
    }

    @Override
    public InterimResultSweepOutcome interimResultSweep(
        LocalDate throughEffectiveDate,
        LocalDate bookStartDate,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        java.time.Instant sweptAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      return interimResultSweep(
          planner.reportingPeriod(
              throughEffectiveDate, bookStartDate, bookIdentity, Optional.empty()),
          bookIdentity,
          planner,
          currentUtcDate,
          sweptAt,
          postingIdGenerator,
          attestationAuthorizer);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome fiscalYearClose(
        ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner planner,
        LocalDate currentUtcDate,
        java.time.Instant closedAt,
        PostingIdGenerator postingIdGenerator,
        dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer attestationAuthorizer) {
      throw new UnsupportedOperationException(
          "Fiscal-year close is not exercised by this interim result sweep test double.");
    }
  }
}
