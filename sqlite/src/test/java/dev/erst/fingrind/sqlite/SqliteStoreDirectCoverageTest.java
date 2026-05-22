package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
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
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
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

/** Focused integration coverage for direct SQLite read and close-period session seams. */
class SqliteStoreDirectCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-19T10:15:30Z");

  @Test
  void storeQuerySurface_reportsAccountsPostingRangeAndOpenCloseHorizon() {
    Path bookPath = tempDirectory.resolve("store-query-surface.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-2",
              "idem-2",
              Optional.of(new dev.erst.fingrind.core.ReversalReference(new PostingId("posting-1"))),
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
      assertEquals(Optional.empty(), postingFactStore.closedThroughEffectiveDate());
      assertTrue(postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")).isPresent());
      assertTrue(postingFactStore.findPosting(new PostingId("posting-1")).isPresent());
      assertTrue(postingFactStore.findReversalFor(new PostingId("posting-1")).isPresent());
    }
  }

  @Test
  void transactionValidationBook_reportsSuccessfulQueriesAcrossTheFullValidationSurface() {
    Path bookPath = tempDirectory.resolve("validation-surface-success.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
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
      assertTrue(validationBook.findPosting(new PostingId("posting-1")).isPresent());
      assertEquals(Optional.empty(), validationBook.findReversalFor(new PostingId("posting-1")));
      assertEquals(1, validationBook.postings(EffectiveDateRange.unbounded()).size());
      assertEquals(Optional.of(EFFECTIVE_DATE), validationBook.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), validationBook.closedThroughEffectiveDate());
    }
  }

  @Test
  void closePeriod_persistsClosedPeriodAuditAndCloseHorizon() {
    Path bookPath = tempDirectory.resolve("close-period-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new dev.erst.fingrind.executor.bookkeeping.RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new AccountCode("3200"),
              new AccountName("Retained Earnings"),
              AccountType.EQUITY,
              AccountRole.ORDINARY,
              financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
              FIXED_INSTANT));
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      PeriodCloseOutcome.Closed closed =
          assertInstanceOf(
              PeriodCloseOutcome.Closed.class,
              postingFactStore.closePeriod(
                  new PeriodCloseDraft(
                      new ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                      new AccountCode("3200"),
                      List.of(),
                      FIXED_INSTANT,
                      List.of(
                          new PostingDraft(
                              new JournalEntry(
                                  EFFECTIVE_DATE,
                                  List.of(
                                      line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                                      line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
                              PostingKind.PERIOD_CLOSE,
                              generatedEvidence("period-close-1", "period-close-plan"),
                              periodCloseProvenance("EUR")))),
                  () -> new PostingId("period-close-1")));

      assertEquals(1, closed.closedPeriod().closeOrder());
      assertEquals(
          List.of(new PostingId("period-close-1")), closed.closedPeriod().closingPostingIds());
      assertEquals(Optional.of(EFFECTIVE_DATE), postingFactStore.closedThroughEffectiveDate());
      assertEquals(2, postingFactStore.postings(EffectiveDateRange.unbounded()).size());
      assertEquals(
          "PERIOD_CLOSED:1",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select event_kind || ':' || cast(period_close_order as text)
              from audit_event
              where event_kind = 'PERIOD_CLOSED'
              order by audit_event_order desc
              limit 1
              """));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from period_close_posting where period_close_order = 1"));
    }
  }

  @Test
  void accountTotals_surfaceHonorsDateRangesAndPostingCoverage() {
    Path bookPath = tempDirectory.resolve("account-totals-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new AccountCode("3200"),
              new AccountName("Retained Earnings"),
              AccountType.EQUITY,
              AccountRole.ORDINARY,
              financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
              FIXED_INSTANT));

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
          PeriodCloseOutcome.Closed.class,
          postingFactStore.closePeriod(
              new PeriodCloseDraft(
                  new ReportingPeriod(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
                  new AccountCode("3200"),
                  List.of(),
                  FIXED_INSTANT,
                  List.of(
                      new PostingDraft(
                          new JournalEntry(
                              LocalDate.parse("2026-04-07"),
                              List.of(
                                  line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                                  line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
                          dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
                          PostingKind.PERIOD_CLOSE,
                          generatedEvidence("period-close-1", "period-close-plan"),
                          periodCloseProvenance("EUR")))),
              () -> new PostingId("period-close-1")));
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
      RegisteredAccount retainedEarningsAccount =
          postingFactStore.findAccount(new AccountCode("3200")).orElseThrow();

      assertEquals(
          List.of(
              new AccountCurrencyTotals(cashAccount, CurrencyUnit.of("EUR"), 1400L, 0L),
              new AccountCurrencyTotals(revenueAccount, CurrencyUnit.of("EUR"), 1000L, 1400L),
              new AccountCurrencyTotals(
                  retainedEarningsAccount, CurrencyUnit.of("EUR"), 0L, 1000L)),
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
              new AccountCurrencyTotals(
                  retainedEarningsAccount, CurrencyUnit.of("EUR"), 0L, 1000L)),
          postingFactStore.accountTotals(
              EffectiveDateRange.of(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")),
              PostingCoverage.ALL_POSTING_KINDS));
    }
  }

  @Test
  void readModels_excludePeriodClosePostingsWhenNonClosingCoverageIsRequested() {
    Path bookPath = tempDirectory.resolve("non-closing-read-coverage.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("3200"),
                  new AccountName("Retained Earnings"),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
                  true,
                  FIXED_INSTANT)),
          postingFactStore.declareAccount(
              new AccountCode("3200"),
              new AccountName("Retained Earnings"),
              AccountType.EQUITY,
              AccountRole.ORDINARY,
              financialPositionTaxonomy(FinancialPositionLineClassification.ACCUMULATED_RESULT),
              FIXED_INSTANT));

      CommittedPosting operatingPosting =
          postingFact(
              "posting-1",
              "idem-1",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting periodClosePosting =
          new CommittedPosting(
              new PostingId("period-close-1"),
              new JournalEntry(
                  LocalDate.parse("2026-04-07"),
                  List.of(
                      line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                      line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
              dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
              PostingKind.PERIOD_CLOSE,
              generatedEvidence("period-close-1", "period-close-plan"),
              periodCloseProvenance("EUR"));
      commitPosting(postingFactStore, operatingPosting);
      commitPosting(postingFactStore, periodClosePosting);

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
                  LocalDate.parse("2026-04-07"),
                  LocalDate.parse("2026-04-07"),
                  PostingCoverage.NON_CLOSING_POSTINGS),
              revenueAccount);
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, accountLedger.postingCoverage());
      assertEquals(1, accountLedger.entries().size());
      assertEquals(
          new PostingId("posting-1"), accountLedger.entries().getFirst().posting().postingId());

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
              SqliteStatementQueries.findOneAccount(database, new AccountCode("1000"))
                  .orElseThrow();

          assertEquals(
              List.of("EUR"),
              new SqlitePostingReader()
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

  private static CommittedProvenance periodCloseProvenance(String currencyCode) {
    String closeToken = EFFECTIVE_DATE + ":" + EFFECTIVE_DATE + ":" + FIXED_INSTANT.toEpochMilli();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("system:periodClose"),
            ActorType.SYSTEM,
            new CommandId("periodClose:" + closeToken + ":" + currencyCode),
            new IdempotencyKey("periodClose:" + closeToken + ":" + currencyCode),
            new CausationId("periodClose:" + closeToken),
            Optional.of(new CorrelationId("periodClose:" + closeToken)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.SYSTEM);
  }
}
