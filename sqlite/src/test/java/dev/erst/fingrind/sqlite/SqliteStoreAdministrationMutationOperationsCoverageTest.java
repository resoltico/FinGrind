package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Covers durable-account extraction and audit-event mapping for account declarations. */
class SqliteStoreAdministrationMutationOperationsCoverageTest {
  @Test
  void declaredAccount_returnsDurableSnapshotsAndRejectsRejectedOutcomes() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        account,
        SqliteStoreAdministrationMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Declared(account)));
    assertEquals(
        account,
        SqliteStoreAdministrationMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Reactivated(account)));
    assertEquals(
        account,
        SqliteStoreAdministrationMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Renamed(account)));
    assertEquals(
        account,
        SqliteStoreAdministrationMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Unchanged(account)));

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAdministrationMutationOperations.declaredAccount(
                    new AccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized())));
    assertTrue(
        Objects.requireNonNullElse(rejected.getMessage(), "")
            .contains("Rejected account declarations do not carry a durable account snapshot"));
  }

  @Test
  void accountAuditEvent_mapsDurableOutcomesAndRejectsNonAuditedOutcomes() {
    Instant recordedAt = Instant.parse("2026-04-08T11:15:30Z");
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        BookAuditEvent.accountDeclared(recordedAt, account.accountCode()),
        SqliteStoreAdministrationMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Declared(account)));
    assertEquals(
        BookAuditEvent.accountReactivated(recordedAt, account.accountCode()),
        SqliteStoreAdministrationMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Reactivated(account)));
    assertEquals(
        BookAuditEvent.accountRenamed(recordedAt, account.accountCode()),
        SqliteStoreAdministrationMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Renamed(account)));

    IllegalArgumentException unchanged =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAdministrationMutationOperations.accountAuditEvent(
                    recordedAt, new AccountDeclarationOutcome.Unchanged(account)));
    assertEquals("Unchanged account declarations do not append audit.", unchanged.getMessage());

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAdministrationMutationOperations.accountAuditEvent(
                    recordedAt,
                    new AccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized())));
    assertTrue(
        Objects.requireNonNullElse(rejected.getMessage(), "")
            .contains("Rejected account declarations do not append audit"));
  }
}
