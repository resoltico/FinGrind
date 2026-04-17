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
import dev.erst.fingrind.contract.LedgerExecutionPolicy;
import dev.erst.fingrind.contract.LedgerFailurePolicy;
import dev.erst.fingrind.contract.LedgerJournalLevel;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepStatus;
import dev.erst.fingrind.contract.LedgerTransactionMode;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingRejection;
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
import dev.erst.fingrind.core.ReversalReason;
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
                      "plan-1",
                      policy(),
                      List.of(
                          new LedgerStep.OpenBook("open"),
                          new LedgerStep.DeclareAccount(
                              "cash", account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              "revenue", account("2000", "Revenue", NormalBalance.CREDIT)),
                          new LedgerStep.PreflightEntry("preflight", postEntryCommand("idem-1")),
                          new LedgerStep.PostEntry("post", postEntryCommand("idem-1")),
                          new LedgerStep.InspectBook("inspect"),
                          new LedgerStep.ListAccounts("accounts", new ListAccountsQuery(50, 0)),
                          new LedgerStep.GetPosting("get", new PostingId("posting-1")),
                          new LedgerStep.ListPostings(
                              "postings",
                              new ListPostingsQuery(
                                  Optional.empty(), Optional.empty(), Optional.empty(), 50, 0)),
                          new LedgerStep.AccountBalance(
                              "balance",
                              new AccountBalanceQuery(
                                  new AccountCode("1000"), Optional.empty(), Optional.empty())),
                          new LedgerStep.Assert(
                              "assert-declared",
                              new LedgerAssertion.AccountDeclared(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              "assert-active",
                              new LedgerAssertion.AccountActive(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              "assert-posting",
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))),
                          new LedgerStep.Assert(
                              "assert-balance",
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
                      "plan-1",
                      policy(),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              "cash", account("1000", "Cash", NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "book-not-initialized",
          result.journal().steps().getFirst().failure().orElseThrow().code());
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_rollsBackOnPostingRejection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-1",
                      policy(),
                      List.of(
                          new LedgerStep.OpenBook("open"),
                          new LedgerStep.DeclareAccount(
                              "cash", account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.PostEntry("post", postEntryCommand("idem-1")))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          result.journal().steps().getLast().failure().orElseThrow().code());
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
                      "plan-1",
                      policy(),
                      List.of(
                          new LedgerStep.OpenBook("open"),
                          new LedgerStep.DeclareAccount(
                              "cash", account("1000", "Cash", NormalBalance.DEBIT)),
                          new LedgerStep.Assert(
                              "missing-posting",
                              new LedgerAssertion.PostingExists(
                                  new PostingId("posting-missing"))))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
      assertEquals(
          "assertion-failed", result.journal().steps().getLast().failure().orElseThrow().code());
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_rejectsAlreadyInitializedOpenBookAndConflictingRedeclaration() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult openBookResult =
          service(bookSession)
              .execute(
                  new LedgerPlan("plan-open", policy(), List.of(new LedgerStep.OpenBook("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, openBookResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookAlreadyInitialized()),
          openBookResult.journal().steps().getLast().failure().orElseThrow().code());
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
                      "plan-declare",
                      policy(),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              "cash", account("1000", "Cash", NormalBalance.CREDIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, redeclareResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          redeclareResult.journal().steps().getLast().failure().orElseThrow().code());
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
                      "plan-preflight",
                      policy(),
                      List.of(
                          new LedgerStep.PreflightEntry("preflight", postEntryCommand("idem-2")))));

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          preflightResult.journal().steps().getLast().failure().orElseThrow().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult getPostingResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-get",
                      policy(),
                      List.of(new LedgerStep.GetPosting("get", new PostingId("posting-missing")))));

      assertEquals(LedgerPlanStatus.REJECTED, getPostingResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-missing"))),
          getPostingResult.journal().steps().getLast().failure().orElseThrow().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult listPostingsResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-list-postings",
                      policy(),
                      List.of(
                          new LedgerStep.ListPostings(
                              "postings",
                              new ListPostingsQuery(
                                  Optional.of(new AccountCode("9999")),
                                  Optional.empty(),
                                  Optional.empty(),
                                  50,
                                  0)))));

      assertEquals(LedgerPlanStatus.REJECTED, listPostingsResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          listPostingsResult.journal().steps().getLast().failure().orElseThrow().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult balanceResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-balance",
                      policy(),
                      List.of(
                          new LedgerStep.AccountBalance(
                              "balance",
                              new AccountBalanceQuery(
                                  new AccountCode("9999"), Optional.empty(), Optional.empty())))));

      assertEquals(LedgerPlanStatus.REJECTED, balanceResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          balanceResult.journal().steps().getLast().failure().orElseThrow().code());
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
                      "plan-assert-query",
                      policy(),
                      List.of(
                          new LedgerStep.Assert(
                              "assert-balance",
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
          rejectedQueryResult.journal().steps().getLast().failure().orElseThrow().code());
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      LedgerPlanResult mismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-assert-mismatch",
                      policy(),
                      List.of(
                          new LedgerStep.Assert(
                              "assert-balance",
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                                  NormalBalance.CREDIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, mismatchResult.status());
      assertEquals(
          "assertion-failed",
          mismatchResult.journal().steps().getLast().failure().orElseThrow().code());
      assertTrue(
          mismatchResult.journal().steps().getLast().failure().orElseThrow().facts().stream()
              .anyMatch(
                  fact -> "expectedNetAmount".equals(fact.name()) && "10".equals(fact.value())));
      assertTrue(
          mismatchResult.journal().steps().getLast().failure().orElseThrow().facts().stream()
              .anyMatch(
                  fact ->
                      "expectedBalanceSide".equals(fact.name()) && "CREDIT".equals(fact.value())));
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      LedgerPlanResult amountMismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      "plan-assert-amount-mismatch",
                      policy(),
                      List.of(
                          new LedgerStep.Assert(
                              "assert-balance",
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("9.00")),
                                  NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, amountMismatchResult.status());
      assertTrue(
          amountMismatchResult.journal().steps().getLast().failure().orElseThrow().facts().stream()
              .anyMatch(
                  fact -> "actualNetAmount".equals(fact.name()) && "10".equals(fact.value())));
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
                  new LedgerPlan("plan-1", policy(), List.of(new LedgerStep.OpenBook("open")))));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  private static void assertAssertionFailure(
      InMemoryBookSession bookSession, LedgerAssertion assertion) {
    LedgerPlanResult result =
        service(bookSession)
            .execute(
                new LedgerPlan(
                    "plan-assert", policy(), List.of(new LedgerStep.Assert("assert", assertion))));

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

  private static LedgerPlanService service(InMemoryBookSession bookSession) {
    return new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK);
  }

  private static LedgerExecutionPolicy policy() {
    return new LedgerExecutionPolicy(
        LedgerJournalLevel.NORMAL,
        LedgerFailurePolicy.HALT_ON_FIRST_FAILURE,
        LedgerTransactionMode.ATOMIC);
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
        Optional.empty(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1")),
            Optional.<ReversalReason>empty()),
        SourceChannel.CLI);
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  private static final class ThrowingLedgerPlanSession extends InMemoryBookSession {
    private boolean rollbackCalled;

    @Override
    public OpenBookResult openBook(Instant initializedAt) {
      throw new IllegalStateException("boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      super.rollbackLedgerPlanTransaction();
    }

    private boolean rollbackCalled() {
      return rollbackCalled;
    }
  }
}
