package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.LedgerStepStatus;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LedgerPlanService}. */
class LedgerPlanServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T10:15:30Z"), ZoneOffset.UTC);

  @Test
  void execute_commitsAllSupportedStepFamiliesAndRecordsJournal() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              stepId("revenue"), account("2000", "Revenue", NormalBalance.CREDIT)),
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")),
                          new LedgerStep.InspectBook(stepId("inspect")),
                          new LedgerStep.ListAccounts(
                              stepId("accounts"), new ListAccountsQuery(50, 0)),
                          new LedgerStep.GetPosting(stepId("get"), new PostingId("posting-1")),
                          new LedgerStep.ListPostings(
                              stepId("postings"),
                              new ListPostingsQuery(
                                  Optional.empty(),
                                  Optional.empty(),
                                  Optional.empty(),
                                  50,
                                  Optional.empty())),
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              new AccountBalanceQuery(
                                  new AccountCode("1000"), Optional.empty(), Optional.empty())),
                          new LedgerStep.Assert(
                              stepId("assert-declared"),
                              new LedgerAssertion.AccountDeclared(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-active"),
                              new LedgerAssertion.AccountActive(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))),
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                                  NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertEquals(14, result.journal().steps().size());
      assertTrue(
          result.journal().steps().stream()
              .allMatch(step -> step.status() == LedgerStepStatus.SUCCEEDED));
      assertEquals(LedgerStepKind.OPEN_BOOK, result.journal().steps().getFirst().kind());
      assertEquals(LedgerStepKind.ASSERT, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
          result.journal().steps().getLast().detailKind());
      assertTrue(bookSession.findPosting(new PostingId("posting-1")).isPresent());
    }
  }

  @Test
  void execute_rejectsUninitializedPlanThatDoesNotBeginWithOpenBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "administration-book-not-initialized",
          result.journal().steps().getFirst().requiredFailure().code());
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_rejectsUninitializedPlansWithFamilySpecificCodes() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult preflightResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-preflight"),
                      List.of(
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")))));

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          "posting-book-not-initialized",
          preflightResult.journal().steps().getFirst().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult queryResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              new AccountBalanceQuery(
                                  new AccountCode("1000"), Optional.empty(), Optional.empty())))));

      assertEquals(LedgerPlanStatus.REJECTED, queryResult.status());
      assertEquals(
          "query-book-not-initialized",
          queryResult.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_rejectsUninitializedAssertionPlansWithQueryFamilyCode() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          result.journal().steps().getFirst().requiredFailure().code());
      assertEquals(
          LedgerAssertionKind.POSTING_EXISTS, result.journal().steps().getFirst().detailKind());
    }
  }

  @Test
  void execute_rollsBackOnPostingRejection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      LedgerStepFailure failure = result.journal().steps().getLast().requiredFailure();
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          failure.code());
      assertTrue(failure.message().contains("undeclared or inactive accounts"));
      assertTrue(
          failure.facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact, "violation", "code", "unknown-account", "accountCode", "2000")));
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_rollsBackOnAssertionFailure() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.Assert(
                              stepId("missing-posting"),
                              new LedgerAssertion.PostingExists(
                                  new PostingId("posting-missing"))))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
      assertEquals("assertion-failed", result.journal().steps().getLast().requiredFailure().code());
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_rejectsAlreadyInitializedOpenBookAndConflictingRedeclaration() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult openBookResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-open"), List.of(new LedgerStep.OpenBook(stepId("open")))));

      assertEquals(LedgerPlanStatus.REJECTED, openBookResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookAlreadyInitialized()),
          openBookResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          FIXED_CLOCK.instant());

      LedgerPlanResult redeclareResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-declare"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.CREDIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, redeclareResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          redeclareResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_reportsPreflightAndQueryRejections() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          FIXED_CLOCK.instant());

      LedgerPlanResult preflightResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-preflight"),
                      List.of(
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-2")))));

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          preflightResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult getPostingResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-get"),
                      List.of(
                          new LedgerStep.GetPosting(
                              stepId("get"), new PostingId("posting-missing")))));

      assertEquals(LedgerPlanStatus.REJECTED, getPostingResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-missing"))),
          getPostingResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      ListPostingsQuery missingAccountQuery =
          new ListPostingsQuery(
              Optional.of(new AccountCode("9999")),
              Optional.empty(),
              Optional.empty(),
              50,
              Optional.empty());
      LedgerPlanResult listPostingsResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-list-postings"),
                      List.of(
                          new LedgerStep.ListPostings(stepId("postings"), missingAccountQuery))));

      assertEquals(LedgerPlanStatus.REJECTED, listPostingsResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          listPostingsResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult balanceResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-balance"),
                      List.of(
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              new AccountBalanceQuery(
                                  new AccountCode("9999"), Optional.empty(), Optional.empty())))));

      assertEquals(LedgerPlanStatus.REJECTED, balanceResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          balanceResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_reportsListAccountsRejectionFromQuerySeam() {
    try (ListAccountsRejectingLedgerPlanSession bookSession =
        new ListAccountsRejectingLedgerPlanSession()) {
      LedgerPlanResult result =
          new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK)
              .execute(
                  new LedgerPlan(
                      planId("plan-list-accounts"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.ListAccounts(
                              stepId("accounts"), new ListAccountsQuery(50, 0)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          result.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_reportsSpecificAssertionFailures() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          FIXED_CLOCK.instant());
      bookSession.deactivateAccount(new AccountCode("1000"));

      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountDeclared(new AccountCode("9999")));
      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountActive(new AccountCode("9999")));
      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountActive(new AccountCode("1000")));
      assertAssertionFailure(
          bookSession,
          new LedgerAssertion.AccountBalanceEquals(
              new AccountCode("1000"),
              Optional.empty(),
              Optional.empty(),
              new Money(new CurrencyCode("USD"), BigDecimal.ZERO),
              NormalBalance.DEBIT));
    }
  }

  @Test
  void execute_reportsAssertionQueryRejectionsAndBalanceMismatches() {
    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      LedgerPlanResult rejectedQueryResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-query"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("9999"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), BigDecimal.TEN),
                                  NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, rejectedQueryResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          rejectedQueryResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      LedgerPlanResult mismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-mismatch"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                                  NormalBalance.CREDIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, mismatchResult.status());
      assertEquals(
          "assertion-failed", mismatchResult.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          mismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "expectedNetAmount", "10")));
      assertTrue(
          mismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "expectedBalanceSide", "CREDIT")));
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      LedgerPlanResult amountMismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-amount-mismatch"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("9.00")),
                                  NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, amountMismatchResult.status());
      assertTrue(
          amountMismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "actualNetAmount", "10")));
    }
  }

  @Test
  void execute_rollsBackWhenUnexpectedRuntimeFailureEscapes() {
    try (ThrowingLedgerPlanSession bookSession = new ThrowingLedgerPlanSession()) {
      LedgerPlanService service =
          new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK);

      assertThrows(
          IllegalStateException.class,
          () ->
              service.execute(
                  new LedgerPlan(
                      planId("plan-1"), List.of(new LedgerStep.OpenBook(stepId("open"))))));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  private static void assertAssertionFailure(
      InMemoryBookSession bookSession, LedgerAssertion assertion) {
    LedgerPlanResult result =
        service(bookSession)
            .execute(
                new LedgerPlan(
                    planId("plan-assert"),
                    List.of(new LedgerStep.Assert(stepId("assert"), assertion))));

    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_CLOCK.instant());
    return bookSession;
  }

  private static InMemoryBookSession bookWithCommittedPosting() {
    InMemoryBookSession bookSession = initializedBook();
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        NormalBalance.CREDIT,
        FIXED_CLOCK.instant());
    PostEntryResult committed =
        new PostingApplicationService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK)
            .commit(postEntryCommand("idem-setup"));
    assertEquals(PostEntryResult.Committed.class, committed.getClass());
    return bookSession;
  }

  private static LedgerPlanService service(LedgerPlanSession bookSession) {
    return new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK);
  }

  private static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  private static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  private static boolean textFact(LedgerFact fact, String name, String value) {
    return fact instanceof LedgerFact.Text text
        && name.equals(text.name())
        && value.equals(text.value());
  }

  private static boolean groupFact(
      LedgerFact fact,
      String groupName,
      String firstName,
      String firstValue,
      String secondName,
      String secondValue) {
    return fact instanceof LedgerFact.Group group
        && groupName.equals(group.name())
        && group.facts().stream().anyMatch(child -> textFact(child, firstName, firstValue))
        && group.facts().stream().anyMatch(child -> textFact(child, secondName, secondValue));
  }

  private static DeclareAccountCommand account(
      String accountCode, String accountName, NormalBalance normalBalance) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode), new AccountName(accountName), normalBalance);
  }

  private static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))))),
        PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  private static final class ThrowingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookAdministrationSession throwingAdministrationSession =
        new BookAdministrationSession() {
          @Override
          public OpenBookResult openBook(Instant initializedAt) {
            throw new IllegalStateException("boom");
          }

          @Override
          public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
              AccountCode accountCode,
              AccountName accountName,
              NormalBalance normalBalance,
              Instant declaredAt) {
            return delegate.declareAccount(accountCode, accountName, normalBalance, declaredAt);
          }

          @Override
          public void close() {
            // Parent wrapper owns lifecycle.
          }
        };
    private boolean rollbackCalled;

    @Override
    public BookAdministrationSession administrationSession() {
      return throwingAdministrationSession;
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookQuerySession querySession() {
      return delegate.querySession();
    }

    @Override
    public void beginLedgerPlanTransaction() {
      delegate.beginLedgerPlanTransaction();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      delegate.commitLedgerPlanTransaction();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    private boolean rollbackCalled() {
      return rollbackCalled;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** Test-only seam split that keeps queries uninitialized after a successful open-book step. */
  private static final class ListAccountsRejectingLedgerPlanSession
      implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookQuerySession rejectingQuerySession =
        new BookQuerySession() {
          @Override
          public dev.erst.fingrind.contract.BookInspection inspectBook() {
            return delegate.inspectBook();
          }

          @Override
          public boolean isInitialized() {
            return false;
          }

          @Override
          public dev.erst.fingrind.contract.AccountPage listAccounts(ListAccountsQuery query) {
            return delegate.listAccounts(query);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.DeclaredAccount> findAccount(
              AccountCode accountCode) {
            return delegate.findAccount(accountCode);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.PostingFact> findPosting(PostingId postingId) {
            return delegate.findPosting(postingId);
          }

          @Override
          public dev.erst.fingrind.contract.PostingPage listPostings(ListPostingsQuery query) {
            return delegate.listPostings(query);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.AccountBalanceSnapshot> accountBalance(
              AccountBalanceQuery query) {
            return delegate.accountBalance(query);
          }

          @Override
          public void close() {
            // Parent test fixture owns lifecycle.
          }
        };

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookQuerySession querySession() {
      return rejectingQuerySession;
    }

    @Override
    public void beginLedgerPlanTransaction() {
      delegate.beginLedgerPlanTransaction();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      delegate.commitLedgerPlanTransaction();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      delegate.rollbackLedgerPlanTransaction();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
