package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Initializes the canonical FinGrind book schema when a durable commit needs it. */
final class SqliteSchemaManager {
  private static final AtomicReference<String> canonicalSchemaSql = new AtomicReference<>();

  private SqliteSchemaManager() {}

  /** Ensures the book parent directory exists before a writable connection is opened. */
  static void ensureParentDirectory(Path bookPath) {
    Path parent = Objects.requireNonNull(bookPath.getParent(), "Book path parent is missing.");
    try {
      Files.createDirectories(parent);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create SQLite book directory.", exception);
    }
  }

  /** Applies the canonical schema to the supplied connection exactly once per opened session. */
  static void initializeBook(SqliteNativeDatabase database) throws SqliteNativeException {
    database.executeScript(canonicalSchemaSql());
  }

  static void initializeBook(
      SqliteNativeDatabase database, Supplier<InputStream> schemaStreamSupplier)
      throws SqliteNativeException {
    database.executeScript(readSchema(schemaStreamSupplier));
  }

  static String readSchema(Supplier<InputStream> schemaStreamSupplier) {
    try (InputStream schemaStream =
        Objects.requireNonNull(
            schemaStreamSupplier.get(), "SQLite book schema resource is missing.")) {
      return new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read SQLite book schema.", exception);
    }
  }

  private static String canonicalSchemaSql() {
    return cachedValue(canonicalSchemaSql, () -> readSchema(SqliteSchemaManager::openSchemaStream));
  }

  static String cachedValue(AtomicReference<String> cache, Supplier<String> loader) {
    String cachedSchema = cache.get();
    if (cachedSchema != null) {
      return cachedSchema;
    }
    // The packaged schema resource is mandatory for the FinGrind runtime. Load it lazily here so
    // a packaging mistake fails as one shaped runtime problem rather than a class-init crash.
    String loadedSchema = loader.get();
    if (cache.compareAndSet(null, loadedSchema)) {
      return loadedSchema;
    }
    return cache.get();
  }

  private static InputStream openSchemaStream() {
    return SqliteSchemaManager.class.getResourceAsStream(
        "/dev/erst/fingrind/sqlite/book_schema.sql");
  }
}
