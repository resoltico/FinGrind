package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.BookReadSession;
import dev.erst.fingrind.executor.InMemoryBookSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
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
        NullPointerException.class,
        () ->
            invokeFixtureMethod(
                "readPostEntryCommand", new Class<?>[] {byte[].class}, new Object[] {null}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeFixtureMethod(
                "readLedgerPlan", new Class<?>[] {byte[].class}, new Object[] {null}));
    assertThrows(
        NullPointerException.class,
        () ->
            invokeFixtureMethod(
                "postingIdGenerator", new Class<?>[] {byte[].class}, new Object[] {null}));
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
  void lifecycle_helpers_reject_uninitialized_service_states() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(bookSession);
      var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));
      DeclaredAccount account =
          new DeclaredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
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
            NormalBalance.DEBIT,
            false,
            CliFuzzFixtures.fixedClock().instant());

    BookAdministrationService driftedOpenBookService =
        new BookAdministrationService(
            new BookAdministrationSession() {
              @Override
              public OpenBookResult openBook(Instant initializedAt) {
                return new OpenBookResult.Opened(initializedAt.plusSeconds(1));
              }

              @Override
              public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  NormalBalance normalBalance,
                  Instant declaredAt) {
                throw new UnsupportedOperationException("not used");
              }
            },
            CliFuzzFixtures.fixedClock());
    BookAdministrationService inactiveReactivationService =
        new BookAdministrationService(
            new BookAdministrationSession() {
              @Override
              public OpenBookResult openBook(Instant initializedAt) {
                throw new UnsupportedOperationException("not used");
              }

              @Override
              public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  NormalBalance normalBalance,
                  Instant declaredAt) {
                return new dev.erst.fingrind.contract.DeclareAccountResult.Declared(
                    new DeclaredAccount(
                        accountCode, accountName, normalBalance, false, declaredAt));
              }
            },
            CliFuzzFixtures.fixedClock());
    BookAdministrationService changedDeclaredAtService =
        new BookAdministrationService(
            new BookAdministrationSession() {
              @Override
              public OpenBookResult openBook(Instant initializedAt) {
                throw new UnsupportedOperationException("not used");
              }

              @Override
              public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
                  AccountCode accountCode,
                  AccountName accountName,
                  NormalBalance normalBalance,
                  Instant declaredAt) {
                return new dev.erst.fingrind.contract.DeclareAccountResult.Declared(
                    new DeclaredAccount(
                        accountCode, accountName, normalBalance, true, declaredAt.plusSeconds(1)));
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
            NormalBalance.DEBIT,
            true,
            CliFuzzFixtures.fixedClock().instant());
    DeclaredAccount secondAccount =
        new DeclaredAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            NormalBalance.CREDIT,
            true,
            CliFuzzFixtures.fixedClock().instant());
    AccountPageCursor nextCursor = AccountPageCursor.fromAccount(firstAccount);
    AtomicInteger pageCalls = new AtomicInteger();
    List<Optional<AccountPageCursor>> cursors = new ArrayList<>();
    BookReadSession pagedSession =
        new BookReadSession() {
          @Override
          public dev.erst.fingrind.contract.BookInspection inspectBook() {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public boolean isInitialized() {
            return true;
          }

          @Override
          public AccountPage listAccounts(dev.erst.fingrind.contract.ListAccountsQuery query) {
            cursors.add(query.cursor());
            if (pageCalls.getAndIncrement() == 0) {
              return new AccountPage(List.of(firstAccount), query.limit(), Optional.of(nextCursor));
            }
            return new AccountPage(List.of(secondAccount), query.limit(), Optional.empty());
          }

          @Override
          public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public Optional<dev.erst.fingrind.contract.PostingFact> findPosting(
              dev.erst.fingrind.core.PostingId postingId) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public dev.erst.fingrind.contract.PostingPage listPostings(
              dev.erst.fingrind.contract.ListPostingsQuery query) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public Optional<dev.erst.fingrind.contract.AccountBalanceSnapshot> accountBalance(
              dev.erst.fingrind.contract.AccountBalanceQuery query) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public dev.erst.fingrind.contract.TrialBalanceReport trialBalance(
              dev.erst.fingrind.contract.TrialBalanceQuery query) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public dev.erst.fingrind.contract.AccountLedgerReport accountLedger(
              dev.erst.fingrind.contract.AccountLedgerQuery query, DeclaredAccount account) {
            throw new UnsupportedOperationException("not used");
          }

          @Override
          public dev.erst.fingrind.contract.PeriodSummaryReport periodSummary(
              dev.erst.fingrind.contract.PeriodSummaryQuery query) {
            throw new UnsupportedOperationException("not used");
          }
        };

    assertEquals(List.of(firstAccount, secondAccount), CliFuzzFixtures.listAccounts(pagedSession));
    assertEquals(List.of(Optional.empty(), Optional.of(nextCursor)), cursors);
  }

  private static String basicValidRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
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

  private static Object invokeFixtureMethod(
      String methodName, Class<?>[] parameterTypes, Object[] arguments) throws Exception {
    Method method = CliFuzzFixtures.class.getDeclaredMethod(methodName, parameterTypes);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }
}
