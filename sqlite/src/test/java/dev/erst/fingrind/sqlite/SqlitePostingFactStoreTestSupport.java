package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.io.TempDir;

/** Thin compatibility base that now layers small SQLite fixture and introspection supports. */
@NullUnmarked
class SqlitePostingFactStoreTestSupport extends SqliteStoreTestIntrospectionSupport {
  @TempDir Path tempDirectory;

  void assertOpenConfigurationFailure(String driftSql, String expectedMessage) {
    Path bookPath =
        tempDirectory.resolve(expectedMessage.replace(' ', '-').replace('.', '_') + ".sqlite");
    try (SqliteNativeDatabase database =
        SqliteConnectionConfigurer.configureOpenedDatabase(
            SqliteNativeConnections.open(bookAccess(bookPath)),
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
    try {
      Path keyDirectory = tempDirectory.resolve("book-keys");
      Files.createDirectories(keyDirectory);
      Path keyPath = keyDirectory.resolve(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
