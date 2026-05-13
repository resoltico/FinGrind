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

/** Unit and integration tests for the public {@link SqliteBookSessions} seam. */
class SqliteBookSessionsTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void open_usesDefaultAndExplicitSessionModes() {
    Path defaultCreatePath = tempDirectory.resolve("default-create.sqlite");
    assertSessionAccessMode(
        SqliteBookSessions.open(defaultCreatePath, passphrase("default create")),
        SqliteStoreAccessMode.READ_WRITE_CREATE);
    assertTrue(java.nio.file.Files.exists(defaultCreatePath));
    assertSessionAccessMode(
        SqliteBookSessions.open(
            tempDirectory.resolve("read-only.sqlite"),
            passphrase("read only"),
            SqliteBookSessionMode.READ_ONLY),
        SqliteStoreAccessMode.READ_ONLY);
    assertSessionAccessMode(
        SqliteBookSessions.open(
            tempDirectory.resolve("read-write-existing.sqlite"),
            passphrase("read write existing"),
            SqliteBookSessionMode.READ_WRITE_EXISTING),
        SqliteStoreAccessMode.READ_WRITE_EXISTING);
    assertSessionAccessMode(
        SqliteBookSessions.open(
            tempDirectory.resolve("plan-execution.sqlite"),
            passphrase("plan execution"),
            SqliteBookSessionMode.PLAN_EXECUTION),
        SqliteStoreAccessMode.PLAN_EXECUTION);
  }

  @Test
  void openResolved_mapsAcceptedAndRejectedStoreOutcomesToPublicSessionDecisions()
      throws Exception {
    Path acceptedPath = tempDirectory.resolve("open-resolved-plan.sqlite");
    ContractDecision<SqliteBookSession> acceptedDecision =
        SqliteBookSessions.openResolved(
            acceptedPath,
            passphrase("accepted plan execution"),
            SqliteBookSessionMode.PLAN_EXECUTION);
    switch (acceptedDecision) {
      case ContractDecision.Accepted<SqliteBookSession>(SqliteBookSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.PLAN_EXECUTION, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(acceptedPath));
        }
      }
      case ContractDecision.Rejected<SqliteBookSession>(var failure) ->
          fail("Expected plan-execution open to succeed but was " + failure.code());
    }
    Path rejectedPath = tempDirectory.resolve("open-resolved-wrong-passphrase.sqlite");
    initializeBookOnDisk(rejectedPath);
    ContractDecision<SqliteBookSession> rejectedDecision =
        SqliteBookSessions.openResolved(
            rejectedPath,
            SqliteBookPassphrase.fromCharacters(
                "wrong public session passphrase", "wrong-passphrase".toCharArray()),
            SqliteBookSessionMode.READ_WRITE_EXISTING);
    switch (rejectedDecision) {
      case ContractDecision.Accepted<SqliteBookSession>(SqliteBookSession session) -> {
        try (session) {
          fail("Expected wrong passphrase to be rejected.");
        }
      }
      case ContractDecision.Rejected<SqliteBookSession>(var failure) ->
          assertEquals(
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(), failure.code());
    }
  }

  @Test
  void openResolved_keepsMissingBookStateForReadOnlyAndReadWriteExistingSessions() {
    Path readOnlyMissingPath = tempDirectory.resolve("open-resolved-read-only-missing.sqlite");
    ContractDecision<SqliteBookSession> readOnlyDecision =
        SqliteBookSessions.openResolved(
            readOnlyMissingPath, passphrase("missing read only"), SqliteBookSessionMode.READ_ONLY);
    switch (readOnlyDecision) {
      case ContractDecision.Accepted<SqliteBookSession>(SqliteBookSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_ONLY, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(readOnlyMissingPath));
          assertFalse(session.inspectBook().initialized());
        }
      }
      case ContractDecision.Rejected<SqliteBookSession>(var failure) ->
          fail("Expected missing read-only session to stay lazy but was " + failure.code());
    }
    Path existingMissingPath =
        tempDirectory.resolve("open-resolved-read-write-existing-missing.sqlite");
    ContractDecision<SqliteBookSession> existingDecision =
        SqliteBookSessions.openResolved(
            existingMissingPath,
            passphrase("missing read write existing"),
            SqliteBookSessionMode.READ_WRITE_EXISTING);
    switch (existingDecision) {
      case ContractDecision.Accepted<SqliteBookSession>(SqliteBookSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_WRITE_EXISTING, storeAccessMode(store(session)));
          assertFalse(java.nio.file.Files.exists(existingMissingPath));
          assertFalse(session.inspectBook().initialized());
        }
      }
      case ContractDecision.Rejected<SqliteBookSession>(var failure) ->
          fail(
              "Expected missing read-write-existing session to stay lazy but was "
                  + failure.code());
    }
  }

  @Test
  void openResolved_supportsContractLevelBookAccessAndResolver() {
    Path bookPath = tempDirectory.resolve("resolved-from-book-access.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    ContractDecision<SqliteBookSession> decision =
        SqliteBookSessions.openResolved(
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
      case ContractDecision.Accepted<SqliteBookSession>(SqliteBookSession session) -> {
        try (session) {
          assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, storeAccessMode(store(session)));
        }
      }
      case ContractDecision.Rejected<SqliteBookSession>(var failure) ->
          fail("Expected resolver-backed open to succeed but was " + failure.code());
    }
  }

  @Test
  void open_supportsContractLevelBookAccessAndThrowsWhenResolverRejects() {
    Path acceptedPath = tempDirectory.resolve("open-from-book-access.sqlite");
    BookAccess acceptedBookAccess = bookAccess(acceptedPath);
    try (SqliteBookSession session =
        SqliteBookSessions.open(
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
                SqliteBookSessions.open(
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

  private SqliteBookPassphrase passphrase(String description) {
    return SqliteBookPassphrase.fromCharacters(description, TEST_BOOK_KEY.toCharArray());
  }

  private void assertSessionAccessMode(
      SqliteBookSession session, SqliteStoreAccessMode expectedAccessMode) {
    try (session) {
      assertEquals(expectedAccessMode, storeAccessMode(store(session)));
    }
  }

  private static SqlitePostingFactStore store(SqliteBookSession session) {
    return (SqlitePostingFactStore) session;
  }
}
