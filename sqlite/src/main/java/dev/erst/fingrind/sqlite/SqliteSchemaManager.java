package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Initializes the canonical FinGrind book schema when a durable commit needs it. */
final class SqliteSchemaManager {
  private SqliteSchemaManager() {}

  /** Creates the book parent directories and ensures the canonical schema exists. */
  static void initializeBook(Path bookPath, SqliteCommandExecutor commandExecutor) {
    ensureParentDirectory(bookPath);
    SqliteCommandExecutor.SqliteCommandResult result =
        commandExecutor.script(readSchema(SqliteSchemaManager::openSchemaStream));
    if (result.exitCode() != 0) {
      throw new IllegalStateException("sqlite3 failed: " + result.standardError().strip());
    }
  }

  static String readSchema(Supplier<InputStream> schemaStreamSupplier) {
    try {
      return new String(
          Objects.requireNonNull(
                  schemaStreamSupplier.get(), "SQLite book schema resource is missing.")
              .readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read SQLite book schema.", exception);
    }
  }

  private static InputStream openSchemaStream() {
    return SqliteSchemaManager.class.getResourceAsStream(
        "/dev/erst/fingrind/sqlite/book_schema.sql");
  }

  private static void ensureParentDirectory(Path bookPath) {
    Path parent = Objects.requireNonNull(bookPath.getParent(), "Book path parent is missing.");
    try {
      Files.createDirectories(parent);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create SQLite book directory.", exception);
    }
  }
}
