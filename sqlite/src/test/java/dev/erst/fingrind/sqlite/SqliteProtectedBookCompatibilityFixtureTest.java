package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.PostingCommitResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Compatibility and restore coverage for committed protected-book fixtures. */
@NullUnmarked
class SqliteProtectedBookCompatibilityFixtureTest extends SqlitePostingFactStoreTestSupport {
  private static final String CURRENT_DEFAULT_FIXTURE_RESOURCE =
      "/dev/erst/fingrind/sqlite/fixtures/current-default-protected-book.sqlite";

  @Test
  void currentDefaultProtectedBookFixture_reopensAndRejectsWrongKey() throws Exception {
    Path fixtureCopy = copyFixture("current-default-protected-book.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(fixtureCopy))) {
      assertTrue(postingFactStore.isInitialized());
      assertEquals(2, listAccounts(postingFactStore).size());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(postingFactStore.findAccount(new AccountCode("2000")).isPresent());
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
    }

    try (SqlitePostingFactStore wrongKeyStore =
        new SqlitePostingFactStore(bookAccess(fixtureCopy, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::isInitialized);
      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void closedBookBackupCopy_restoresFixtureStateAndPreservesEncryption() throws Exception {
    Path workingBook = copyFixture("working-protected-book.sqlite");
    Path backupBook = tempDirectory.resolve("working-protected-book.backup.sqlite");
    Files.copy(workingBook, backupBook, StandardCopyOption.REPLACE_EXISTING);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(workingBook))) {
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty())),
          postingFactStore.commit(
              postingFact(
                  "fixture-posting-2",
                  "fixture-idem-2",
                  java.util.Optional.empty(),
                  java.util.Optional.empty())));
      assertTrue(
          postingFactStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
    }

    Files.copy(backupBook, workingBook, StandardCopyOption.REPLACE_EXISTING);

    try (SqlitePostingFactStore restoredStore =
        new SqlitePostingFactStore(bookAccess(workingBook))) {
      assertTrue(restoredStore.isInitialized());
      assertTrue(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-1")).isPresent());
      assertFalse(
          restoredStore.findExistingPosting(new IdempotencyKey("fixture-idem-2")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(restoredStore.findAccount(new AccountCode("2000")).isPresent());
    }

    try (SqlitePostingFactStore wrongKeyStore =
        new SqlitePostingFactStore(bookAccess(workingBook, "wrong-fixture-key"))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, wrongKeyStore::isInitialized);
      assertInvalidPlaintextBookFailure(exception);
    }
  }

  private Path copyFixture(String targetFileName) throws IOException {
    Path targetPath = tempDirectory.resolve(targetFileName);
    try (InputStream resourceStream =
        SqliteProtectedBookCompatibilityFixtureTest.class.getResourceAsStream(
            CURRENT_DEFAULT_FIXTURE_RESOURCE)) {
      if (resourceStream == null) {
        throw new IOException(
            "Missing committed protected-book fixture: " + CURRENT_DEFAULT_FIXTURE_RESOURCE);
      }
      Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath;
    }
  }
}
