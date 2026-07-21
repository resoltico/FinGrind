package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused integration coverage for direct SQLite read and interim-result-sweep session seams. */
class SqliteStoreDirectCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-19T10:15:30Z");

  @Test
  void storeQuerySurface_reportsAccountsPostingRangeAndOpenCloseHorizon() {
    Path bookPath = tempDirectory.resolve("store-query-surface.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-2",
              "idem-2",
              Optional.of(
                  new dev.erst.fingrind.core.ReversalReference(
                      new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
              Optional.of(new dev.erst.fingrind.core.ReversalReason("full reversal"))));

      assertInstanceOf(BookLifecycleInspection.Initialized.class, postingFactStore.inspectBook());
      assertEquals(
          List.of(new AccountCode("1000"), new AccountCode("2000")),
          postingFactStore.allAccounts().stream().map(account -> account.accountCode()).toList());
      assertEquals(
          Set.of(new AccountCode("1000"), new AccountCode("2000")),
          postingFactStore
              .findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000")))
              .keySet());
      assertEquals(2, postingFactStore.postings(EffectiveDateRange.unbounded()).size());
      assertEquals(Optional.of(EFFECTIVE_DATE), postingFactStore.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), postingFactStore.transferredThroughEffectiveDate());
      assertTrue(postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")).isPresent());
      assertTrue(
          postingFactStore
              .findPosting(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))
              .isPresent());
      assertTrue(
          postingFactStore
              .findReversalFor(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))
              .isPresent());
    }
  }

  @Test
  void transactionValidationBook_reportsSuccessfulQueriesAcrossTheFullValidationSurface() {
    Path bookPath = tempDirectory.resolve("validation-surface-success.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              requireStoreDatabase(postingFactStore), postingFactStore.postingReader());

      assertInstanceOf(BookLifecycleInspection.Initialized.class, validationBook.inspectBook());
      assertEquals(
          Set.of(new AccountCode("1000"), new AccountCode("2000")),
          validationBook
              .findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000")))
              .keySet());
      assertTrue(validationBook.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(validationBook.findExistingPosting(new IdempotencyKey("idem-1")).isPresent());
      assertTrue(
          validationBook
              .findPosting(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))
              .isPresent());
      assertEquals(
          Optional.empty(),
          validationBook.findReversalFor(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
      assertEquals(1, validationBook.postings(EffectiveDateRange.unbounded()).size());
      assertEquals(Optional.of(EFFECTIVE_DATE), validationBook.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), validationBook.transferredThroughEffectiveDate());
    }
  }

  @Test
  void interimResultSweep_persistsSweptInterimResultAuditAndCloseHorizon() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new dev.erst.fingrind.executor.bookkeeping.RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
              FIXED_INSTANT,
              SqliteAttestationTestSupport.authorizer()));
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      InterimResultSweepOutcome.Transferred closed =
          assertInstanceOf(
              InterimResultSweepOutcome.Transferred.class,
              postingFactStore.interimResultSweep(
                  new InterimResultSweepDraft(
                      new ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                      new AccountCode("3200"),
                      List.of(),
                      FIXED_INSTANT,
                      List.of(
                          postingDraft(
                              new JournalEntry(
                                  EFFECTIVE_DATE,
                                  List.of(
                                      line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                                      line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
                              PostingKind.INTERIM_RESULT_SWEEP,
                              dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                              generatedEvidence(
                                  "interim-result-sweep-1", "interim-result-sweep-plan"),
                              interimResultSweepProvenance("EUR")))),
                  () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
                  SqliteAttestationTestSupport.authorizer()));

      assertEquals(1, closed.sweptInterimResult().sweepOrder());
      assertEquals(
          List.of(new PostingId("0485e481-7f56-30fd-92e2-92a099a486af")),
          closed.sweptInterimResult().sweepPostingIds());
      assertEquals(Optional.of(EFFECTIVE_DATE), postingFactStore.transferredThroughEffectiveDate());
      assertEquals(2, postingFactStore.postings(EffectiveDateRange.unbounded()).size());
      assertEquals(
          "INTERIM_RESULT_SWEPT:1",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select event_kind || ':' || cast(close_operation_order as text)
              from audit_event
              where event_kind = 'INTERIM_RESULT_SWEPT'
              order by audit_event_order desc
              limit 1
              """));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from interim_result_sweep_posting where interim_result_sweep_order = 1"));
    }
  }

  @Test
  void interimResultSweep_highLevelRejectsMissingResultHoldingAccountWithoutPersistingCloseFacts() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-high-level-missing-result.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-1",
              "idem-1",
              EFFECTIVE_DATE,
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00"))));

      InterimResultSweepOutcome.Rejected rejected =
          assertInstanceOf(
              InterimResultSweepOutcome.Rejected.class,
              new InterimResultSweepService(
                      reportingPeriodCloseSession,
                      reportingPeriodCloseSession,
                      () -> new PostingId("1153abd3-5eb5-3203-9e2f-4900e0e136c3"),
                      java.time.Clock.fixed(FIXED_INSTANT, java.time.ZoneOffset.UTC))
                  .interimResultSweep(
                      new ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                      SqliteAttestationTestSupport.authorizer()));

      assertInstanceOf(CloseTargetAccountCandidateMissing.class, rejected.rejection());
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from interim_result_sweep"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where event_kind = 'INTERIM_RESULT_SWEPT'"));
    }
  }

  @Test
  void interimResultSweep_highLevelPersistsAtomicCloseAndRejectsNoncontiguousFollowUpPeriods() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-high-level-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
              FIXED_INSTANT,
              SqliteAttestationTestSupport.authorizer()));
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-1",
              "idem-1",
              EFFECTIVE_DATE,
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00"))));

      InterimResultSweepOutcome.Transferred transferred =
          assertInstanceOf(
              InterimResultSweepOutcome.Transferred.class,
              new InterimResultSweepService(
                      reportingPeriodCloseSession,
                      reportingPeriodCloseSession,
                      () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
                      java.time.Clock.fixed(FIXED_INSTANT, java.time.ZoneOffset.UTC))
                  .interimResultSweep(
                      new ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                      SqliteAttestationTestSupport.authorizer()));

      assertEquals(1, transferred.sweptInterimResult().sweepOrder());
      assertEquals(Optional.of(EFFECTIVE_DATE), postingFactStore.transferredThroughEffectiveDate());

      InterimResultSweepOutcome.Rejected rejected =
          assertInstanceOf(
              InterimResultSweepOutcome.Rejected.class,
              new InterimResultSweepService(
                      reportingPeriodCloseSession,
                      reportingPeriodCloseSession,
                      () -> new PostingId("a442c8b7-c4ab-3ba4-8841-8d0da2cd6c78"),
                      java.time.Clock.fixed(FIXED_INSTANT.plusSeconds(1), java.time.ZoneOffset.UTC))
                  .interimResultSweep(
                      new ReportingPeriod(
                          LocalDate.parse("2026-04-09"), LocalDate.parse("2026-04-09")),
                      SqliteAttestationTestSupport.authorizer()));

      assertEquals(
          new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
              LocalDate.parse("2026-04-08")),
          rejected.rejection());
    }
  }

  @Test
  void accountTotals_surfaceHonorsDateRangesAndPostingCoverage() {
    Path bookPath = tempDirectory.resolve("account-totals-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
              FIXED_INSTANT,
              SqliteAttestationTestSupport.authorizer()));

      commitPosting(
          postingFactStore,
          postingFact(
              "posting-1",
              "idem-1",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00"))));
      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          postingFactStore.interimResultSweep(
              new InterimResultSweepDraft(
                  new ReportingPeriod(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
                  new AccountCode("3200"),
                  List.of(),
                  FIXED_INSTANT,
                  List.of(
                      postingDraft(
                          new JournalEntry(
                              LocalDate.parse("2026-04-07"),
                              List.of(
                                  line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                                  line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                          dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
                          PostingKind.INTERIM_RESULT_SWEEP,
                          dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
                          generatedEvidence("interim-result-sweep-1", "interim-result-sweep-plan"),
                          interimResultSweepProvenance("EUR")))),
              () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
              SqliteAttestationTestSupport.authorizer()));
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-2",
              "idem-2",
              LocalDate.parse("2026-04-08"),
              Instant.parse("2026-04-08T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "4.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "4.00"))));

      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      RegisteredAccount resultHoldingAccount =
          postingFactStore.findAccount(new AccountCode("3200")).orElseThrow();

      assertEquals(
          List.of(
              new AccountCurrencyTotals(cashAccount, CurrencyUnit.of("EUR"), 1400L, 0L),
              new AccountCurrencyTotals(revenueAccount, CurrencyUnit.of("EUR"), 1000L, 1400L),
              new AccountCurrencyTotals(resultHoldingAccount, CurrencyUnit.of("EUR"), 0L, 1000L)),
          postingFactStore.accountTotals(
              EffectiveDateRange.of(null, null), PostingCoverage.ALL_POSTING_KINDS));
      assertEquals(
          List.of(
              new AccountCurrencyTotals(cashAccount, CurrencyUnit.of("EUR"), 1000L, 0L),
              new AccountCurrencyTotals(revenueAccount, CurrencyUnit.of("EUR"), 0L, 1000L)),
          postingFactStore.accountTotals(
              EffectiveDateRange.of(null, LocalDate.parse("2026-04-07")),
              PostingCoverage.NON_CLOSING_POSTINGS));
      assertEquals(
          List.of(
              new AccountCurrencyTotals(cashAccount, CurrencyUnit.of("EUR"), 400L, 0L),
              new AccountCurrencyTotals(revenueAccount, CurrencyUnit.of("EUR"), 0L, 400L)),
          postingFactStore.accountTotals(
              EffectiveDateRange.of(LocalDate.parse("2026-04-08"), null),
              PostingCoverage.ALL_POSTING_KINDS));
      assertEquals(
          List.of(
              new AccountCurrencyTotals(cashAccount, CurrencyUnit.of("EUR"), 1000L, 0L),
              new AccountCurrencyTotals(revenueAccount, CurrencyUnit.of("EUR"), 1000L, 1000L),
              new AccountCurrencyTotals(resultHoldingAccount, CurrencyUnit.of("EUR"), 0L, 1000L)),
          postingFactStore.accountTotals(
              EffectiveDateRange.of(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
              PostingCoverage.ALL_POSTING_KINDS));
    }
  }

  @Test
  void readModels_excludeInterimResultSweepPostingsWhenNonClosingCoverageIsRequested() {
    Path bookPath = tempDirectory.resolve("non-closing-read-coverage.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
              FIXED_INSTANT,
              SqliteAttestationTestSupport.authorizer()));

      CommittedPosting operatingPosting =
          postingFact(
              "posting-1",
              "idem-1",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
      PostingDraft interimResultSweepDraft =
          postingDraft(
              new JournalEntry(
                  LocalDate.parse("2026-04-07"),
                  List.of(
                      line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                      line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
              PostingKind.INTERIM_RESULT_SWEEP,
              dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
              generatedEvidence("interim-result-sweep-1", "interim-result-sweep-plan"),
              interimResultSweepProvenance("EUR"));
      commitPosting(postingFactStore, operatingPosting);
      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          postingFactStore.interimResultSweep(
              new InterimResultSweepDraft(
                  new ReportingPeriod(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
                  new AccountCode("3200"),
                  List.of(),
                  FIXED_INSTANT,
                  List.of(interimResultSweepDraft)),
              () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
              SqliteAttestationTestSupport.authorizer()));

      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();

      var accountBalance =
          postingFactStore
              .accountBalance(
                  AccountBalanceCriteria.unbounded(
                      new AccountCode("2000"), PostingCoverage.NON_CLOSING_POSTINGS))
              .orElseThrow();
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, accountBalance.postingCoverage());
      assertEquals(
          List.of(
              CurrencyBalance.ofTotals(Money.parse("EUR", "0.00"), Money.parse("EUR", "10.00"))),
          accountBalance.balances());

      var accountLedger =
          postingFactStore.accountLedger(
              new AccountLedgerCriteria(
                  new AccountCode("2000"),
                  dev.erst.fingrind.core.EffectiveDateRange.of(
                      LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
                  PostingCoverage.NON_CLOSING_POSTINGS,
                  50,
                  Optional.empty()),
              revenueAccount);
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, accountLedger.postingCoverage());
      assertEquals(1, accountLedger.entries().size());
      assertEquals(
          new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
          accountLedger.entries().getFirst().posting().postingId());

      var periodSummary =
          postingFactStore.periodSummary(
              new PeriodSummaryCriteria(
                  LocalDate.parse("2026-04-07"),
                  LocalDate.parse("2026-04-07"),
                  PostingCoverage.NON_CLOSING_POSTINGS));
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, periodSummary.postingCoverage());
      assertEquals(1, periodSummary.postingCount());
      assertEquals(2, periodSummary.postingLineCount());
      assertEquals(2, periodSummary.accountsTouched());
    }
  }

  @Test
  void commit_rejectsGeneratedClosePostingsOutsideTheirReportingPeriodWorkflow() {
    Path bookPath = tempDirectory.resolve("generated-close-direct-commit.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      PostingDraft generatedCloseDraft =
          postingDraft(
              new JournalEntry(
                  EFFECTIVE_DATE,
                  List.of(
                      line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                      line("1000", JournalLine.EntrySide.CREDIT, "10.00"))),
              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
              PostingKind.INTERIM_RESULT_SWEEP,
              dev.erst.fingrind.core.PostingOriginKind.INTERIM_RESULT_SWEEP,
              generatedEvidence("interim-result-sweep-direct", "interim-result-sweep-plan"),
              interimResultSweepProvenance("EUR"));

      IllegalArgumentException rejection =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  postingFactStore.commit(
                      generatedCloseDraft,
                      () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
                      SqliteAttestationTestSupport.authorizer()));

      assertEquals(
          "Generated close postings must be committed through their reporting-period close workflow.",
          rejection.getMessage());
      assertEquals(
          0, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
    }
  }

  @Test
  void postingReader_accountBalanceReturnsOnlyTheFunctionalCurrencyBucket() {
    Path bookPath =
        tempDirectory.resolve("posting-reader-account-balance-functional-currency.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertPostingFactRow(database, "posting-eur", "idem-eur");
          insertJournalLineRow(database, "posting-eur", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-eur", 1, "2000", "CREDIT", "EUR", 1000);

          RegisteredAccount cashAccount =
              SqliteAccountStatementQueries.findOneAccount(database, new AccountCode("1000"))
                  .orElseThrow();

          assertEquals(
              List.of("EUR"),
              new SqlitePostingBalanceReader()
                      .accountBalance(
                          database,
                          AccountBalanceCriteria.unbounded(new AccountCode("1000")),
                          cashAccount)
                      .balances()
                      .stream()
                      .map(balance -> balance.debitTotal().currencyUnit().code())
                      .toList());
        });
  }

  private static CommittedProvenance interimResultSweepProvenance(String currencyCode) {
    String closeToken = EFFECTIVE_DATE + ":" + EFFECTIVE_DATE + ":" + FIXED_INSTANT.toEpochMilli();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            SqliteTestCommandIds.fromLabel("interimResultSweep:" + closeToken + ":" + currencyCode),
            new IdempotencyKey("interimResultSweep:" + closeToken + ":" + currencyCode),
            new CausationId("interimResultSweep:" + closeToken),
            Optional.of(new CorrelationId("interimResultSweep:" + closeToken)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.SYSTEM);
  }
}
