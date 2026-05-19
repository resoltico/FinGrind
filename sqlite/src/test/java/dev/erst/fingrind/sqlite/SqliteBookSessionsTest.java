package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for the public narrow {@link SqliteBookSessions} seams. */
class SqliteBookSessionsTest extends SqlitePostingFactStoreTestSupport {
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("This session suite uses key-file-backed access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("This session suite uses key-file-backed access only.");
          };

  @Test
  void administrationOpen_usesDefaultAndExplicitSessionModes() {
    Path defaultCreatePath = tempDirectory.resolve("default-create.sqlite");
    assertSessionAccessMode(
        SqliteBookSessions.openAdministration(defaultCreatePath, passphrase("default create")),
        SqliteStoreAccessMode.READ_WRITE_CREATE);
    assertTrue(java.nio.file.Files.exists(defaultCreatePath));
    assertSessionAccessMode(
        SqliteBookSessions.openAdministration(
            tempDirectory.resolve("read-write-existing.sqlite"),
            passphrase("read write existing"),
            SqliteBookSessionMode.READ_WRITE_EXISTING),
        SqliteStoreAccessMode.READ_WRITE_EXISTING);
  }

  @Test
  void planExecutionAndPostingOpenResolved_mapAcceptedAndRejectedStoreOutcomes() throws Exception {
    Path acceptedPath = tempDirectory.resolve("open-resolved-plan.sqlite");
    ContractDecision<SqlitePlanExecutionSession> acceptedDecision =
        SqliteBookSessions.openResolvedPlanExecution(
            bookAccess(acceptedPath),
            (resolvedBookPath, passphraseSource, intent) ->
                ContractDecision.accepted(passphrase("accepted plan execution")),
            SqlitePassphraseIntent.EXISTING_SECRET);
    switch (acceptedDecision) {
      case ContractDecision.Accepted<SqlitePlanExecutionSession>(
              SqlitePlanExecutionSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.PLAN_EXECUTION, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(acceptedPath));
        }
      }
      case ContractDecision.Rejected<SqlitePlanExecutionSession>(var failure) ->
          fail("Expected plan-execution open to succeed but was " + failure.code());
    }

    Path rejectedPath = tempDirectory.resolve("open-resolved-wrong-passphrase.sqlite");
    initializeBookOnDisk(rejectedPath);
    ContractDecision<SqlitePostingSession> rejectedDecision =
        SqliteBookSessions.openResolvedPosting(
            rejectedPath,
            SqliteBookPassphrase.fromCharacters(
                "wrong public session passphrase", "wrong-passphrase".toCharArray()),
            SqliteBookSessionMode.READ_WRITE_EXISTING);
    switch (rejectedDecision) {
      case ContractDecision.Accepted<SqlitePostingSession>(SqlitePostingSession session) -> {
        try (session) {
          fail("Expected wrong passphrase to be rejected.");
        }
      }
      case ContractDecision.Rejected<SqlitePostingSession>(var failure) ->
          assertEquals(
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(), failure.code());
    }
  }

  @Test
  void readAndPostingOpenResolved_keepMissingBookStateLazy() {
    Path readOnlyMissingPath = tempDirectory.resolve("open-resolved-read-only-missing.sqlite");
    ContractDecision<SqliteReadSession> readOnlyDecision =
        SqliteBookSessions.openResolvedRead(readOnlyMissingPath, passphrase("missing read only"));
    switch (readOnlyDecision) {
      case ContractDecision.Accepted<SqliteReadSession>(SqliteReadSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_ONLY, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(readOnlyMissingPath));
          assertFalse(session.inspectBook().initialized());
        }
      }
      case ContractDecision.Rejected<SqliteReadSession>(var failure) ->
          fail("Expected missing read-only session to stay lazy but was " + failure.code());
    }

    Path existingMissingPath =
        tempDirectory.resolve("open-resolved-read-write-existing-missing.sqlite");
    ContractDecision<SqlitePostingSession> existingDecision =
        SqliteBookSessions.openResolvedPosting(
            existingMissingPath,
            passphrase("missing read write existing"),
            SqliteBookSessionMode.READ_WRITE_EXISTING);
    switch (existingDecision) {
      case ContractDecision.Accepted<SqlitePostingSession>(SqlitePostingSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(existingMissingPath));
          assertFalse(session.inspectBook().initialized());
        }
      }
      case ContractDecision.Rejected<SqlitePostingSession>(var failure) ->
          fail(
              "Expected missing read-write-existing session to stay lazy but was "
                  + failure.code());
    }
  }

  @Test
  void administrationOpenResolved_supportsContractLevelBookAccessAndResolver() {
    Path bookPath = tempDirectory.resolve("resolved-from-book-access.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    ContractDecision<SqliteAdministrationSession> decision =
        SqliteBookSessions.openResolvedAdministration(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_CREATE,
            (resolvedBookPath, passphraseSource, intent) -> {
              assertEquals(bookPath, resolvedBookPath);
              assertEquals(bookAccess.passphraseSource(), passphraseSource);
              assertEquals(SqlitePassphraseIntent.NEW_SECRET, intent);
              return ContractDecision.accepted(passphrase("resolver-secret"));
            },
            SqlitePassphraseIntent.NEW_SECRET);
    switch (decision) {
      case ContractDecision.Accepted<SqliteAdministrationSession>(
              SqliteAdministrationSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, storeAccessMode(store(session)));
        }
      }
      case ContractDecision.Rejected<SqliteAdministrationSession>(var failure) ->
          fail("Expected resolver-backed open to succeed but was " + failure.code());
    }
  }

  @Test
  void postingOpen_supportsContractLevelBookAccessAndThrowsWhenResolverRejects() {
    Path acceptedPath = tempDirectory.resolve("open-from-book-access.sqlite");
    BookAccess acceptedBookAccess = bookAccess(acceptedPath);
    try (SqlitePostingSession session =
        SqliteBookSessions.openPosting(
            acceptedBookAccess,
            SqliteBookSessionMode.READ_WRITE_CREATE,
            (resolvedBookPath, passphraseSource, intent) -> {
              assertEquals(acceptedPath, resolvedBookPath);
              assertEquals(acceptedBookAccess.passphraseSource(), passphraseSource);
              assertEquals(SqlitePassphraseIntent.NEW_SECRET, intent);
              return ContractDecision.accepted(passphrase("open resolver secret"));
            },
            SqlitePassphraseIntent.NEW_SECRET)) {
      assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, storeAccessMode(store(session)));
    }

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () ->
                SqliteBookSessions.openPosting(
                    bookAccess(tempDirectory.resolve("open-from-book-access-rejected.sqlite")),
                    SqliteBookSessionMode.READ_WRITE_CREATE,
                    (resolvedBookPath, passphraseSource, intent) ->
                        ContractDecision.rejected(
                            ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
                                "Rejected resolver secret", null, null)),
                    SqlitePassphraseIntent.NEW_SECRET));
    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(),
        exception.failure().code());
  }

  @Test
  void additionalSessionFactories_coverRemainingReadCloseRekeyAndStoreOverloads() throws Exception {
    Path bookPath = tempDirectory.resolve("additional-session-factories.sqlite");
    initializeBookOnDisk(bookPath);
    BookAccess access = bookAccess(bookPath);
    Path postingCreatePath = tempDirectory.resolve("posting-create-factory.sqlite");
    Path postingExistingPath = tempDirectory.resolve("posting-existing-factory.sqlite");
    initializeBookOnDisk(postingExistingPath);

    try (SqliteAdministrationSession administrationSession =
            SqliteBookSessions.openAdministration(
                access,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                KEY_FILE_RESOLVER,
                SqlitePassphraseIntent.EXISTING_SECRET);
        SqliteReadSession pathReadSession =
            SqliteBookSessions.openRead(bookPath, passphrase("path read session"));
        SqliteReadSession resolverReadSession =
            SqliteBookSessions.openRead(
                access, KEY_FILE_RESOLVER, SqlitePassphraseIntent.EXISTING_SECRET);
        SqlitePostingSession defaultPostingSession =
            SqliteBookSessions.openPosting(
                postingCreatePath, passphrase("default posting session"));
        SqlitePostingSession existingPostingSession =
            SqliteBookSessions.openPosting(
                postingExistingPath,
                passphrase("existing posting session"),
                SqliteBookSessionMode.READ_WRITE_EXISTING);
        SqlitePeriodCloseSession periodCloseSession =
            SqliteBookSessions.openPeriodClose(
                access, KEY_FILE_RESOLVER, SqlitePassphraseIntent.EXISTING_SECRET);
        SqlitePlanExecutionSession planExecutionSession =
            SqliteBookSessions.openPlanExecution(
                access, KEY_FILE_RESOLVER, SqlitePassphraseIntent.EXISTING_SECRET);
        SqliteRekeySession rekeySession =
            SqliteBookSessions.openRekey(
                access, KEY_FILE_RESOLVER, SqlitePassphraseIntent.EXISTING_SECRET);
        SqlitePostingFactStore defaultStore =
            SqliteBookSessions.openStore(bookPath, passphrase("default store"));
        SqlitePostingFactStore existingStore =
            SqliteBookSessions.openStore(
                bookPath, passphrase("existing store"), SqliteBookSessionMode.READ_WRITE_EXISTING);
        SqlitePostingFactStore resolverStore =
            SqliteBookSessions.openStore(
                access,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                KEY_FILE_RESOLVER,
                SqlitePassphraseIntent.EXISTING_SECRET)) {
      assertEquals(
          SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(store(administrationSession)));
      assertEquals(SqliteStoreAccessMode.READ_ONLY, storeAccessMode(store(pathReadSession)));
      assertEquals(SqliteStoreAccessMode.READ_ONLY, storeAccessMode(store(resolverReadSession)));
      assertEquals(
          SqliteStoreAccessMode.READ_WRITE_CREATE, storeAccessMode(store(defaultPostingSession)));
      assertEquals(
          SqliteStoreAccessMode.READ_WRITE_EXISTING,
          storeAccessMode(store(existingPostingSession)));
      assertEquals(
          SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(store(periodCloseSession)));
      assertEquals(
          SqliteStoreAccessMode.PLAN_EXECUTION, storeAccessMode(store(planExecutionSession)));
      assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(store(rekeySession)));
      assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, storeAccessMode(defaultStore));
      assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(existingStore));
      assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(resolverStore));
    }
  }

  private SqliteBookPassphrase passphrase(String description) {
    return SqliteBookPassphrase.fromCharacters(description, TEST_BOOK_KEY.toCharArray());
  }

  private void assertSessionAccessMode(
      AutoCloseable session, SqliteStoreAccessMode expectedAccessMode) {
    try (session) {
      assertEquals(expectedAccessMode, storeAccessMode(store(session)));
    } catch (Exception exception) {
      throw new AssertionError("Unexpected session close failure.", exception);
    }
  }

  private static SqlitePostingFactStore store(Object session) {
    return (SqlitePostingFactStore) session;
  }
}
