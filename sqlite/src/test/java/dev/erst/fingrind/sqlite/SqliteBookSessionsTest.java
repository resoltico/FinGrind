package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for the public {@link SqliteBookSessions} seam. */
@NullUnmarked
class SqliteBookSessionsTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void open_usesDefaultAndExplicitSessionModes() {
    assertSessionAccessMode(
        SqliteBookSessions.open(
            tempDirectory.resolve("default-create.sqlite"), passphrase("default create")),
        SqliteStoreAccessMode.READ_WRITE_CREATE);
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
          assertEquals(
              SqliteStoreAccessMode.PLAN_EXECUTION, store(session).lifecycle().accessMode());
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
          assertEquals(ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.code(), failure.code());
    }
  }

  private SqliteBookPassphrase passphrase(String description) {
    return SqliteBookPassphrase.fromCharacters(description, TEST_BOOK_KEY.toCharArray());
  }

  private void assertSessionAccessMode(
      SqliteBookSession session, SqliteStoreAccessMode expectedAccessMode) {
    try (session) {
      assertEquals(expectedAccessMode, store(session).lifecycle().accessMode());
    }
  }

  private static SqlitePostingFactStore store(SqliteBookSession session) {
    return (SqlitePostingFactStore) session;
  }
}
