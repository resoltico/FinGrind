package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Covers durable-account extraction and audit-event mapping for account declarations. */
class SqliteStoreAdministrationMutationOperationsCoverageTest
    extends SqlitePostingFactStoreTestSupport {
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
        SqliteStoreAccountRegistryMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Declared(account)));
    assertEquals(
        account,
        SqliteStoreAccountRegistryMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Reactivated(account)));
    assertEquals(
        account,
        SqliteStoreAccountRegistryMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Renamed(account)));
    assertEquals(
        account,
        SqliteStoreAccountRegistryMutationOperations.declaredAccount(
            new AccountDeclarationOutcome.Unchanged(account)));

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAccountRegistryMutationOperations.declaredAccount(
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
        SqliteStoreAccountRegistryMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Declared(account)));
    assertEquals(
        BookAuditEvent.accountReactivated(recordedAt, account.accountCode()),
        SqliteStoreAccountRegistryMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Reactivated(account)));
    assertEquals(
        BookAuditEvent.accountRenamed(recordedAt, account.accountCode()),
        SqliteStoreAccountRegistryMutationOperations.accountAuditEvent(
            recordedAt, new AccountDeclarationOutcome.Renamed(account)));

    IllegalArgumentException unchanged =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAccountRegistryMutationOperations.accountAuditEvent(
                    recordedAt, new AccountDeclarationOutcome.Unchanged(account)));
    assertEquals("Unchanged account declarations do not append audit.", unchanged.getMessage());

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteStoreAccountRegistryMutationOperations.accountAuditEvent(
                    recordedAt,
                    new AccountDeclarationOutcome.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized())));
    assertTrue(
        Objects.requireNonNullElse(rejected.getMessage(), "")
            .contains("Rejected account declarations do not append audit"));
  }

  @Test
  void declarationMutation_mapsPersistedAccountChangesAndRejectsNonMutatingOutcomes() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("3000"),
            new AccountName("Retained earnings"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        AttestationEffectMutation.CREATE,
        invokeDeclarationMutation(new AccountDeclarationOutcome.Declared(account)));
    assertEquals(
        AttestationEffectMutation.REACTIVATE,
        invokeDeclarationMutation(new AccountDeclarationOutcome.Reactivated(account)));
    assertEquals(
        AttestationEffectMutation.AMEND,
        invokeDeclarationMutation(new AccountDeclarationOutcome.Renamed(account)));

    assertEquals(
        "Unchanged account declarations do not append attestation.",
        assertThrows(
                IllegalArgumentException.class,
                () -> invokeDeclarationMutation(new AccountDeclarationOutcome.Unchanged(account)))
            .getMessage());
    assertTrue(
        Objects.requireNonNullElse(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                            invokeDeclarationMutation(
                                new AccountDeclarationOutcome.Rejected(
                                    new BookkeepingAdministrationRejection.BookNotInitialized())))
                    .getMessage(),
                "")
            .contains("Rejected account declarations do not append attestation"));
  }

  @Test
  void openAttestedBook_translatesAndRollsBackANativeSchemaInitializationFailure() {
    Path bookPath = tempDirectory.resolve("native-open-failure.sqlite");
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters("native open", TEST_BOOK_KEY.toCharArray()));
        SchemaFailingDatabase database = new SchemaFailingDatabase()) {
      SqliteStoreContext context =
          new SqliteStoreContext(
              bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, sessionSecret) {
            @Override
            SqliteNativeDatabase database() {
              return database;
            }

            @Override
            SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
              return new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE);
            }
          };
      SqliteStoreAdministrationMutationOperations operations =
          new SqliteStoreAdministrationMutationOperations(context, lifecycle);

      SqliteStorageFailureException failure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  operations.openAttestedBook(
                      initializedAt,
                      SqlitePostingFactFixtureSupport.bookIdentity(),
                      List.of(),
                      SqliteAttestationTestSupport.genesis(
                          SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt)));

      assertEquals(
          "Failed to initialize SQLite book. SQLITE_IOERR: simulated schema initialization failure",
          failure.getMessage());
      assertEquals(List.of("begin immediate", "rollback"), database.statements);
      lifecycle.close();
    }
  }

  @Test
  void openAttestedBook_rollsBackAndRethrowsOneRuntimeSchemaInitializationFailure() {
    Path bookPath = tempDirectory.resolve("runtime-open-failure.sqlite");
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    IllegalStateException schemaFailure =
        new IllegalStateException("simulated schema runtime failure");
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters("runtime open", TEST_BOOK_KEY.toCharArray()));
        RuntimeSchemaFailingDatabase database = new RuntimeSchemaFailingDatabase(schemaFailure)) {
      SqliteStoreContext context =
          new SqliteStoreContext(
              bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, sessionSecret) {
            @Override
            SqliteNativeDatabase database() {
              return database;
            }

            @Override
            SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
              return new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE);
            }
          };
      SqliteStoreAdministrationMutationOperations operations =
          new SqliteStoreAdministrationMutationOperations(context, lifecycle);

      assertEquals(
          schemaFailure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  operations.openAttestedBook(
                      initializedAt,
                      SqlitePostingFactFixtureSupport.bookIdentity(),
                      List.of(),
                      SqliteAttestationTestSupport.genesis(
                          SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt))));
      assertEquals(List.of("begin immediate", "rollback"), database.statements);
      lifecycle.close();
    }
  }

  private static AttestationEffectMutation invokeDeclarationMutation(
      AccountDeclarationOutcome outcome) {
    try {
      MethodHandle declarationMutation =
          MethodHandles.privateLookupIn(
                  SqliteStoreAccountRegistryMutationOperations.class, MethodHandles.lookup())
              .findStatic(
                  SqliteStoreAccountRegistryMutationOperations.class,
                  "declarationMutation",
                  MethodType.methodType(
                      AttestationEffectMutation.class, AccountDeclarationOutcome.class));
      return (AttestationEffectMutation) declarationMutation.invoke(outcome);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke account declaration-mutation mapping.", throwable);
    }
  }

  /** Records transaction control while making only schema execution fail natively. */
  private static final class SchemaFailingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();

    private SchemaFailingDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
    }

    @Override
    void executeScript(String sql) {
      throw new SqliteNativeException(
          SqliteNativeResultCode.code("IOERR"), "simulated schema initialization failure");
    }

    @Override
    public void close() {}
  }

  /** Records transaction control while making only schema execution fail with a runtime error. */
  private static final class RuntimeSchemaFailingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();
    private final IllegalStateException failure;

    private RuntimeSchemaFailingDatabase(IllegalStateException failure) {
      super(MemorySegment.NULL);
      this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
    }

    @Override
    void executeScript(String sql) {
      throw failure;
    }

    @Override
    public void close() {}
  }
}
