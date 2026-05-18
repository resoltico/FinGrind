package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Thin compatibility base that now layers small SQLite fixture and introspection supports. */
class SqlitePostingFactStoreTestSupport extends SqliteStoreTestIntrospectionSupport {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  void assertOpenConfigurationFailure(String driftSql, String expectedMessage) {
    Path bookPath =
        tempDirectory.resolve(expectedMessage.replace(' ', '-').replace('.', '_') + ".sqlite");
    try (SqliteNativeDatabase database =
        SqliteConnectionConfigurer.configureOpenedDatabase(
            SqliteNativeConnections.openKeyFileAccess(bookAccess(bookPath)),
            SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      database.executeScript(driftSql + ";");
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteConnectionConfigurer.assertOpenConfiguration(
                      database, SqliteStoreAccessMode.READ_WRITE_CREATE));
      assertEquals(expectedMessage, exception.getMessage());
    }
  }

  BookAccess bookAccess(Path bookPath) {
    return bookAccess(bookPath, TEST_BOOK_KEY);
  }

  BookAccess bookAccess(Path bookPath, String keyText) {
    try {
      Path keyPath = tempDirectory.resolve("book-keys").resolve(bookPath.getFileName() + ".key");
      writeSecureKeyFile(keyPath, keyText);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static PostingCommitResult commitPosting(
      SqlitePostingFactStore postingFactStore, CommittedPosting postingFact) {
    return postingFactStore.commit(
        new PostingDraft(
            postingFact.journalEntry(),
            postingFact.postingLineage(),
            postingFact.postingKind(),
            postingFact.provenance()),
        postingFact::postingId);
  }
}
