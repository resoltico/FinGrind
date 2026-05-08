package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Bootstraps the canonical FinGrind book schema when a durable commit needs it. */
final class SqliteBookSchemaBootstrap {
  private static final AtomicReference<@Nullable String> canonicalSchemaSql =
      new AtomicReference<>();

  private SqliteBookSchemaBootstrap() {}

  /** Ensures the book parent directory exists before a writable connection is opened. */
  static void ensureParentDirectory(Path bookPath) {
    Path parent = bookPath.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      throw new IllegalArgumentException(
          "Book path must resolve against a writable parent directory.");
    }
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath.toAbsolutePath().normalize());
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create SQLite book directory.", exception);
    }
  }

  /** Applies the canonical schema to the supplied connection exactly once per opened session. */
  static void initializeBook(SqliteNativeDatabase database) {
    database.executeScript(canonicalSchemaSql());
  }

  static void initializeBook(
      SqliteNativeDatabase database, Supplier<@Nullable InputStream> schemaStreamSupplier) {
    database.executeScript(readSchema(schemaStreamSupplier));
  }

  static String readSchema(Supplier<@Nullable InputStream> schemaStreamSupplier) {
    try (InputStream schemaStream =
        Objects.requireNonNull(
            schemaStreamSupplier.get(), "SQLite book schema resource is missing.")) {
      return new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read SQLite book schema.", exception);
    }
  }

  private static String canonicalSchemaSql() {
    return cachedValue(
        canonicalSchemaSql, () -> readSchema(SqliteBookSchemaBootstrap::openSchemaStream));
  }

  static String cachedValue(AtomicReference<@Nullable String> cache, Supplier<String> loader) {
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
    return Objects.requireNonNull(cache.get(), "SQLite schema cache lost its loaded value.");
  }

  private static @Nullable InputStream openSchemaStream() {
    return SqliteBookSchemaBootstrap.class.getResourceAsStream(
        "/dev/erst/fingrind/sqlite/book_schema.sql");
  }
}
