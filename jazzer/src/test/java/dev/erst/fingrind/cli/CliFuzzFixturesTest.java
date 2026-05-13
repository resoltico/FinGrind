package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers deterministic helper behavior shared by Jazzer CLI-backed harnesses. */
class CliFuzzFixturesTest {
  @Test
  void parsing_and_posting_id_helpers_are_deterministic() {
    byte[] requestBytes = basicValidRequest().getBytes(UTF_8);

    assertEquals(
        "2026-04-07",
        CliFuzzFixtures.readPostEntryCommand(requestBytes)
            .journalEntry()
            .effectiveDate()
            .toString());
    assertEquals(
        "plan-1",
        CliFuzzFixtures.readLedgerPlan(basicValidLedgerPlan().getBytes(UTF_8)).planId().value());
    assertEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value());
    assertNotEquals(
        CliFuzzFixtures.postingIdGenerator(requestBytes).nextPostingId().value(),
        CliFuzzFixtures.postingIdGenerator("other".getBytes(UTF_8)).nextPostingId().value());
    assertEquals(Instant.parse("2026-04-07T12:00:00Z"), CliFuzzFixtures.fixedClock().instant());
    assertThrows(
        NullPointerException.class, () -> CliFuzzFixtures.readPostEntryCommand(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.readLedgerPlan(nullValue()));
    assertThrows(NullPointerException.class, () -> CliFuzzFixtures.postingIdGenerator(nullValue()));
  }

  @Test
  void lifecycle_helpers_manage_books_accounts_and_fail_fast_on_drift() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

      CliFuzzFixtures.openBook(administrationService);
      assertThrows(
          IllegalStateException.class, () -> CliFuzzFixtures.openBook(administrationService));

      java.util.List<DeclaredAccount> declaredAccounts =
          CliFuzzFixtures.declarePostingAccounts(administrationService, command);
      assertEquals(2, declaredAccounts.size());
      assertEquals(
          CliFuzzFixtures.firstAccountCode(command), declaredAccounts.getFirst().accountCode());
      assertEquals(2, CliFuzzFixtures.listAccounts(bookSession).size());

      DeclaredAccount firstAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(firstAccount.accountCode());
      DeclaredAccount restoredAccount =
          CliFuzzFixtures.reactivateAccount(administrationService, firstAccount);
      assertTrue(restoredAccount.active());
      assertEquals(firstAccount.declaredAt(), restoredAccount.declaredAt());
    }
  }

  @Test
  void synthetic_posting_account_commands_never_reserve_retained_earnings() {
    var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

    assertTrue(
        CliFuzzFixtures.declarePostingAccountCommands(command).stream()
            .noneMatch(
                declareAccountCommand ->
                    declareAccountCommand.accountRole() == AccountRole.RETAINED_EARNINGS));
  }

  @Test
  void lifecycle_helpers_reject_uninitialized_service_states() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
      DeclaredAccount account =
          new DeclaredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              AccountRole.ORDINARY,
              false,
              CliFuzzFixtures.fixedClock().instant());

      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzFixtures.declarePostingAccounts(administrationService, command));
      assertThrows(IllegalStateException.class, () -> CliFuzzFixtures.listAccounts(bookSession));
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzFixtures.reactivateAccount(administrationService, account));
    }
  }

  @Test
  void lifecycle_helpers_reject_drifted_openBook_and_reactivateAccount_shapes() {
    DeclaredAccount account =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            false,
            CliFuzzFixtures.fixedClock().instant());

    BookAdministrationService driftedOpenBookService =
        new BookAdministrationService(
            new AbstractBookStoreStub() {
              @Override
              public BookOpeningOutcome openBook(Instant initializedAt) {
                return new BookOpeningOutcome.Opened(initializedAt.plusSeconds(1));
              }
            },
            CliFuzzFixtures.fixedClock());
    BookAdministrationService inactiveReactivationService =
        new BookAdministrationService(
            new AbstractBookStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  AccountType accountType,
                  AccountRole accountRole,
                  Instant declaredAt) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        accountCode, accountName, accountType, accountRole, false, declaredAt));
              }
            },
            CliFuzzFixtures.fixedClock());
    BookAdministrationService changedDeclaredAtService =
        new BookAdministrationService(
            new AbstractBookStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  AccountType accountType,
                  AccountRole accountRole,
                  Instant declaredAt) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        accountCode,
                        accountName,
                        accountType,
                        accountRole,
                        true,
                        declaredAt.plusSeconds(1)));
              }
            },
            CliFuzzFixtures.fixedClock());

    assertThrows(
        IllegalStateException.class, () -> CliFuzzFixtures.openBook(driftedOpenBookService));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzFixtures.reactivateAccount(inactiveReactivationService, account));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzFixtures.reactivateAccount(changedDeclaredAtService, account));
  }

  @Test
  void listAccounts_follows_pagination_until_cursor_is_exhausted() {
    DeclaredAccount firstAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            true,
            CliFuzzFixtures.fixedClock().instant());
    DeclaredAccount secondAccount =
        new DeclaredAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            AccountRole.ORDINARY,
            true,
            CliFuzzFixtures.fixedClock().instant());
    AccountRegistryCursor nextCursor = new AccountRegistryCursor(firstAccount.accountCode());
    AtomicInteger pageCalls = new AtomicInteger();
    List<Optional<AccountRegistryCursor>> cursors = new ArrayList<>();
    BookStore pagedStore =
        new AbstractBookStoreStub() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return new BookLifecycleInspection.Initialized(7, 1, 1, Instant.EPOCH);
          }

          @Override
          public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
            cursors.add(query.cursor());
            if (pageCalls.getAndIncrement() == 0) {
              return new AccountRegistryPage(
                  List.of(toRegisteredAccount(firstAccount)),
                  query.limit(),
                  Optional.of(nextCursor));
            }
            return new AccountRegistryPage(
                List.of(toRegisteredAccount(secondAccount)), query.limit(), Optional.empty());
          }
        };

    assertEquals(List.of(firstAccount, secondAccount), CliFuzzFixtures.listAccounts(pagedStore));
    assertEquals(List.of(Optional.empty(), Optional.of(nextCursor)), cursors);
  }

  private abstract static class AbstractBookStoreStub implements BookStore {
    @Override
    public BookOpeningOutcome openBook(Instant initializedAt) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findPosting(dev.erst.fingrind.core.PostingId postingId) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(
        dev.erst.fingrind.core.PostingId priorPostingId) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      throw new UnsupportedOperationException("not used");
    }
  }

  private static RegisteredAccount toRegisteredAccount(DeclaredAccount account) {
    return new RegisteredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountRole(),
        account.active(),
        account.declaredAt());
  }

  private static String basicValidRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """;
  }

  private static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            }
          ]
        }
        """;
  }

  @SuppressWarnings({"NullAway", "TypeParameterUnusedInFormals"})
  private static <T> T nullValue() {
    return null;
  }
}
