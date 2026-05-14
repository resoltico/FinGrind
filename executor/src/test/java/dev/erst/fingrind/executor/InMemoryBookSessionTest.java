package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.allPostingKinds;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.openedBook;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerEntryView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InMemoryBookSession}. */
class InMemoryBookSessionTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void openBook_marksSessionInitializedAndRejectsSecondOpen() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertFalse(bookSession.inspectBook().initialized());
      assertEquals(openedBook(FIXED_INSTANT), bookSession.openBook(FIXED_INSTANT, bookIdentity()));
      assertTrue(bookSession.inspectBook().initialized());
      assertEquals(
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookAlreadyInitialized()),
          bookSession.openBook(FIXED_INSTANT, bookIdentity()));
    }
  }

  @Test
  void declareAccount_requiresInitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              accountRole(AccountType.ASSET, NormalBalance.DEBIT),
              FIXED_INSTANT));
    }
  }

  @Test
  void declareAccount_storesAndListsAccountSnapshots() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());

      AccountDeclarationOutcome result =
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              accountRole(AccountType.ASSET, NormalBalance.DEBIT),
              FIXED_INSTANT);

      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  FIXED_INSTANT)),
          result);
      assertEquals(
          new AccountRegistryPage(
              List.of(
                  registeredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      AccountType.ASSET,
                      NormalBalance.DEBIT,
                      true,
                      FIXED_INSTANT)),
              50,
              Optional.empty()),
          bookSession.listAccounts(new AccountRegistryQuery(50, Optional.empty())));
    }
  }

  @Test
  void listAccounts_sortsAndPaginatesFromStableCursorBoundaries() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      RegisteredAccount cash =
          declareAccount(
              bookSession,
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              NormalBalance.DEBIT);
      RegisteredAccount revenue =
          declareAccount(
              bookSession,
              new AccountCode("2000"),
              new AccountName("Revenue"),
              AccountType.REVENUE,
              NormalBalance.CREDIT);
      RegisteredAccount receivable =
          declareAccount(
              bookSession,
              new AccountCode("3000"),
              new AccountName("Receivable"),
              AccountType.ASSET,
              NormalBalance.DEBIT);

      AccountRegistryPage firstPage =
          bookSession.listAccounts(new AccountRegistryQuery(2, Optional.empty()));
      AccountRegistryPage secondPage =
          bookSession.listAccounts(new AccountRegistryQuery(2, firstPage.nextCursor()));

      assertEquals(
          new AccountRegistryPage(
              List.of(cash, revenue),
              2,
              Optional.of(new AccountRegistryCursor(revenue.accountCode()))),
          firstPage);
      assertEquals(new AccountRegistryPage(List.of(receivable), 2, Optional.empty()), secondPage);
    }
  }

  @Test
  void declareAccount_reactivatesExistingAccountUsingThePersistedRedeclarationTimestamp() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountRole(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_INSTANT);
      bookSession.deactivateAccount(new AccountCode("1000"));

      AccountDeclarationOutcome result =
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash main"),
              AccountType.ASSET,
              accountRole(AccountType.ASSET, NormalBalance.DEBIT),
              Instant.parse("2026-04-08T11:00:00Z"));

      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T11:00:00Z"))),
          result);
    }
  }

  @Test
  void declareAccount_rejectsAccountRoleConflict() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountRole(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_INSTANT);

      AccountDeclarationOutcome result =
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              accountRole(AccountType.ASSET, NormalBalance.CREDIT),
              FIXED_INSTANT);

      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.AccountRoleConflict(
                  new AccountCode("1000"), AccountRole.ORDINARY, AccountRole.CONTRA)),
          result);
    }
  }

  @Test
  void commit_requiresInitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new PostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized()),
          bookSession.commit(postingFact("idem-1")));
    }
  }

  @Test
  void commit_rejectsUnknownAndInactiveAccounts() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      assertEquals(
          new PostingCommitResult.Rejected(
              new BookkeepingPostingRejection.AccountStateViolations(
                  List.of(
                      new BookkeepingPostingRejection.UnknownAccount(new AccountCode("1000")),
                      new BookkeepingPostingRejection.UnknownAccount(new AccountCode("2000"))))),
          bookSession.commit(postingFact("idem-1")));

      declareDefaultAccounts(bookSession);
      bookSession.deactivateAccount(new AccountCode("1000"));

      assertEquals(
          new PostingCommitResult.Rejected(
              new BookkeepingPostingRejection.AccountStateViolations(
                  List.of(
                      new BookkeepingPostingRejection.InactiveAccount(new AccountCode("1000"))))),
          bookSession.commit(postingFact("idem-2")));
    }
  }

  @Test
  void commit_storesPostingAndDuplicateOutcomesAfterInitialization() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      declareDefaultAccounts(bookSession);
      CommittedPosting originalPosting = postingFact("idem-original");
      CommittedPosting firstReversal = reversalFact("idem-reversal-1", "posting-idem-original");
      CommittedPosting secondReversal = reversalFact("idem-reversal-2", "posting-idem-original");

      assertEquals(
          new PostingCommitResult.Committed(originalPosting), bookSession.commit(originalPosting));
      assertEquals(
          Optional.of(originalPosting),
          bookSession.findExistingPosting(new IdempotencyKey("idem-original")));
      assertEquals(
          Optional.of(originalPosting),
          bookSession.findPosting(new PostingId("posting-idem-original")));
      assertEquals(
          new PostingCommitResult.Rejected(
              new BookkeepingPostingRejection.DuplicateIdempotencyKey()),
          bookSession.commit(postingFact("idem-original")));

      assertEquals(
          new PostingCommitResult.Committed(firstReversal), bookSession.commit(firstReversal));
      assertEquals(
          Optional.of(firstReversal),
          bookSession.findReversalFor(new PostingId("posting-idem-original")));
      assertEquals(
          new PostingCommitResult.Rejected(
              new BookkeepingPostingRejection.ReversalAlreadyExists(
                  new PostingId("posting-idem-original"))),
          bookSession.commit(secondReversal));
      assertEquals(
          Optional.empty(), bookSession.findExistingPosting(new IdempotencyKey("idem-reversal-2")));
      assertEquals(Optional.empty(), bookSession.findPosting(secondReversal.postingId()));
    }
  }

  @Test
  void listPostings_appliesFiltersAndStableReverseChronologicalPagination() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      declareDefaultAccounts(bookSession);
      declareAccount(
          bookSession,
          new AccountCode("3000"),
          new AccountName("Bank"),
          AccountType.ASSET,
          NormalBalance.DEBIT);

      CommittedPosting olderPosting =
          postingFact(
              "posting-a",
              "idem-a",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T09:00:00Z"),
              List.of(
                  line("1000", "EUR", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", "EUR", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting sameMomentLowerId =
          postingFact(
              "posting-y",
              "idem-y",
              LocalDate.parse("2026-04-08"),
              Instant.parse("2026-04-08T11:00:00Z"),
              List.of(
                  line("1000", "EUR", JournalLine.EntrySide.DEBIT, "7.00"),
                  line("2000", "EUR", JournalLine.EntrySide.CREDIT, "7.00")));
      CommittedPosting sameMomentHigherId =
          postingFact(
              "posting-z",
              "idem-z",
              LocalDate.parse("2026-04-08"),
              Instant.parse("2026-04-08T11:00:00Z"),
              List.of(
                  line("3000", "EUR", JournalLine.EntrySide.DEBIT, "5.00"),
                  line("2000", "EUR", JournalLine.EntrySide.CREDIT, "5.00")));
      bookSession.commit(olderPosting);
      bookSession.commit(sameMomentLowerId);
      bookSession.commit(sameMomentHigherId);

      PostingHistoryPage firstPage =
          bookSession.listPostings(
              new PostingHistoryQuery(Optional.empty(), null, null, 1, Optional.empty()));
      PostingHistoryPage secondPage =
          bookSession.listPostings(
              new PostingHistoryQuery(Optional.empty(), null, null, 5, firstPage.nextCursor()));
      PostingHistoryPage cashOnlyOnLatestDate =
          bookSession.listPostings(
              new PostingHistoryQuery(
                  Optional.of(new AccountCode("1000")),
                  LocalDate.parse("2026-04-08"),
                  LocalDate.parse("2026-04-08"),
                  5,
                  Optional.empty()));
      PostingHistoryPage boundedOlderPage =
          bookSession.listPostings(
              new PostingHistoryQuery(
                  Optional.empty(), null, LocalDate.parse("2026-04-07"), 5, Optional.empty()));

      assertEquals(
          new PostingHistoryPage(
              List.of(sameMomentHigherId),
              1,
              Optional.of(PostingHistoryCursor.fromPosting(sameMomentHigherId))),
          firstPage);
      assertEquals(
          new PostingHistoryPage(List.of(sameMomentLowerId, olderPosting), 5, Optional.empty()),
          secondPage);
      assertEquals(
          new PostingHistoryPage(List.of(sameMomentLowerId), 5, Optional.empty()),
          cashOnlyOnLatestDate);
      assertEquals(
          new PostingHistoryPage(List.of(olderPosting), 5, Optional.empty()), boundedOlderPage);
    }
  }

  @Test
  void readModels_computeOpeningBalancesStableCurrencyOrderingAndSortedReports() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      declareDefaultAccounts(bookSession);

      CommittedPosting openingEur =
          postingFact(
              "posting-opening-eur",
              "idem-opening-eur",
              LocalDate.parse("2026-04-05"),
              Instant.parse("2026-04-05T10:00:00Z"),
              List.of(
                  line("1000", "EUR", JournalLine.EntrySide.DEBIT, "3.00"),
                  line("2000", "EUR", JournalLine.EntrySide.CREDIT, "3.00")));
      CommittedPosting periodEur =
          postingFact(
              "posting-period-eur",
              "idem-period-eur",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T09:00:00Z"),
              List.of(
                  line("1000", "EUR", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", "EUR", JournalLine.EntrySide.CREDIT, "10.00")));
      bookSession.commit(openingEur);
      bookSession.commit(periodEur);

      RegisteredAccount cashAccount =
          bookSession.findAccount(new AccountCode("1000")).orElseThrow();
      EffectiveDateRange reportDate =
          EffectiveDateRange.of(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"));
      List<CurrencyBalance> openingBalances =
          List.of(currencyBalance("EUR", "3.00", "0.00", "3.00", BalanceSide.DEBIT));
      List<CurrencyBalance> closingBalances =
          List.of(currencyBalance("EUR", "13.00", "0.00", "13.00", BalanceSide.DEBIT));

      assertEquals(
          Optional.of(
              new AccountBalanceView(
                  cashAccount,
                  EffectiveDateRange.of(
                      LocalDate.parse("2026-04-05"), LocalDate.parse("2026-04-07")),
                  closingBalances)),
          bookSession.accountBalance(
              new AccountBalanceCriteria(
                  cashAccount.accountCode(),
                  LocalDate.parse("2026-04-05"),
                  LocalDate.parse("2026-04-07"))));
      assertEquals(
          new TrialBalanceView(
              bookIdentity(),
              Optional.of(LocalDate.parse("2026-04-07")),
              EffectiveDateRange.of(null, LocalDate.parse("2025-04-07")),
              allPostingKinds(),
              List.of(
                  new TrialBalanceRowView(
                      cashAccount,
                      currencyBalance("EUR", "13.00", "0.00", "13.00", BalanceSide.DEBIT)),
                  new TrialBalanceRowView(
                      bookSession.findAccount(new AccountCode("2000")).orElseThrow(),
                      currencyBalance("EUR", "0.00", "13.00", "13.00", BalanceSide.CREDIT)))),
          bookSession.trialBalance(
              new TrialBalanceCriteria(
                  Optional.of(LocalDate.parse("2026-04-07")), allPostingKinds())));
      assertEquals(
          new AccountLedgerView(
              cashAccount,
              reportDate,
              openingBalances,
              List.of(
                  new AccountLedgerEntryView(
                      periodEur,
                      currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                      Money.parse("EUR", "13.00"),
                      BalanceSide.DEBIT)),
              closingBalances),
          bookSession.accountLedger(
              new AccountLedgerCriteria(cashAccount.accountCode(), reportDate), cashAccount));
      assertEquals(
          new PeriodSummaryView(
              LocalDate.parse("2026-04-07"),
              LocalDate.parse("2026-04-07"),
              1,
              2,
              2,
              List.of(
                  new PeriodCurrencySummaryView(
                      currencyBalance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
              List.of(
                  new PeriodAccountActivityView(
                      cashAccount,
                      currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
                  new PeriodAccountActivityView(
                      bookSession.findAccount(new AccountCode("2000")).orElseThrow(),
                      currencyBalance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
          bookSession.periodSummary(
              new PeriodSummaryCriteria(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"))));
    }
  }

  @Test
  void ledgerPlanTransactions_guardLifecycleAndRestoreSnapshotState() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());
      declareDefaultAccounts(bookSession);
      CommittedPosting baselinePosting = postingFact("idem-baseline");
      bookSession.commit(baselinePosting);

      bookSession.rollbackLedgerPlanTransaction();
      IllegalStateException noActiveCommit =
          assertThrows(IllegalStateException.class, bookSession::commitLedgerPlanTransaction);
      assertTrue(
          Objects.requireNonNull(noActiveCommit.getMessage())
              .contains("No ledger plan transaction"));

      bookSession.beginLedgerPlanTransaction();
      IllegalStateException nestedBegin =
          assertThrows(IllegalStateException.class, bookSession::beginLedgerPlanTransaction);
      assertTrue(Objects.requireNonNull(nestedBegin.getMessage()).contains("already active"));

      RegisteredAccount temporaryAccount =
          declareAccount(
              bookSession,
              new AccountCode("3000"),
              new AccountName("Temporary"),
              AccountType.ASSET,
              NormalBalance.DEBIT);
      CommittedPosting temporaryPosting = postingFact("idem-temporary");
      bookSession.commit(temporaryPosting);

      assertEquals(Optional.of(temporaryAccount), bookSession.findAccount(new AccountCode("3000")));
      assertEquals(
          Optional.of(temporaryPosting), bookSession.findPosting(temporaryPosting.postingId()));
      bookSession.rollbackLedgerPlanTransaction();

      assertEquals(Optional.empty(), bookSession.findAccount(new AccountCode("3000")));
      assertEquals(Optional.empty(), bookSession.findPosting(temporaryPosting.postingId()));
      assertEquals(
          Optional.of(baselinePosting), bookSession.findPosting(baselinePosting.postingId()));
      assertNotEquals(
          Optional.of(temporaryPosting),
          bookSession.findExistingPosting(new IdempotencyKey("idem-temporary")));

      bookSession.beginLedgerPlanTransaction();
      RegisteredAccount committedAccount =
          declareAccount(
              bookSession,
              new AccountCode("4000"),
              new AccountName("Committed"),
              AccountType.ASSET,
              NormalBalance.DEBIT);
      bookSession.commitLedgerPlanTransaction();

      assertEquals(Optional.of(committedAccount), bookSession.findAccount(new AccountCode("4000")));
    }
  }

  @Test
  void close_isANoOp() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertDoesNotThrow(bookSession::close);
    }
  }

  @Test
  void deactivateAccount_rejectsUnknownAccount() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT, bookIdentity());

      IllegalArgumentException thrown =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalArgumentException.class,
              () -> bookSession.deactivateAccount(new AccountCode("9999")));

      assertTrue(Objects.requireNonNull(thrown.getMessage()).contains("9999"));
    }
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountRole(AccountType.ASSET, NormalBalance.DEBIT),
        FIXED_INSTANT);
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountRole(AccountType.REVENUE, NormalBalance.CREDIT),
        FIXED_INSTANT);
  }

  private static RegisteredAccount declareAccount(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance) {
    return switch (bookSession.declareAccount(
        accountCode,
        accountName,
        accountType,
        accountRole(accountType, normalBalance),
        FIXED_INSTANT)) {
      case AccountDeclarationOutcome.Declared declared -> declared.account();
      case AccountDeclarationOutcome.Rejected rejected ->
          throw new AssertionError("Unexpected declaration rejection: " + rejected.rejection());
    };
  }

  private static CommittedPosting postingFact(String idempotencyKey) {
    return postingFact(
        "posting-" + idempotencyKey,
        idempotencyKey,
        LocalDate.parse("2026-04-07"),
        FIXED_INSTANT,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, List.copyOf(lines)),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        committedProvenance(idempotencyKey, recordedAt));
  }

  private static CommittedPosting reversalFact(String idempotencyKey, String priorPostingId) {
    return new CommittedPosting(
        new PostingId("posting-" + idempotencyKey),
        reversalJournalEntry(),
        PostingLineageModel.reversal(
            new ReversalReference(new PostingId(priorPostingId)),
            new ReversalReason("historical full reversal")),
        PostingKind.STANDARD,
        committedProvenance(idempotencyKey));
  }

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return committedProvenance(idempotencyKey, FIXED_INSTANT);
  }

  private static CommittedProvenance committedProvenance(
      String idempotencyKey, Instant recordedAt) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-" + idempotencyKey),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.empty()),
        recordedAt,
        SourceChannel.CLI);
  }

  private static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return line(accountCode, "EUR", side, amount);
  }

  private static JournalLine line(
      String accountCode, String currencyCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse(currencyCode, amount));
  }

  private static CurrencyBalance currencyBalance(
      String currencyCode,
      String debitAmount,
      String creditAmount,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(
            Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
    if (!balance.netAmount().equals(Money.parse(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }
}
