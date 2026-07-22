package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Key-file and native-database fixture helpers for SQLite bridge tests. */
final class SqliteNativeDatabaseFixtures {
  private SqliteNativeDatabaseFixtures() {}

  static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    Path parentDirectory = keyPath.toAbsolutePath().normalize().getParent();
    if (parentDirectory == null) {
      throw new IOException("SQLite native test key path must have a parent directory: " + keyPath);
    }
    Files.createDirectories(parentDirectory);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
    if (Files.notExists(keyPath)) {
      SqliteBookKeyFileGenerator.generate(keyPath);
    } else {
      try (SqliteBookPassphrase ignored = SqliteBookKeyFile.load(keyPath)) {
        // The load path enforces the same key-file security contract before rewriting test data.
      }
    }
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
  }

  static void withOpenDatabase(
      BookAccess bookAccess, SqliteNativeBridgeTestSupport.SqliteDatabaseAction action) {
    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess)) {
      action.run(database);
    }
  }

  static SqliteNativeDatabase openNativeDatabase(BookAccess bookAccess) {
    return SqliteNativeKeyFileAccess.open(
        bookAccess.bookFilePath(), SqliteStoreFixtureSupport.requireKeyFilePath(bookAccess));
  }
}
