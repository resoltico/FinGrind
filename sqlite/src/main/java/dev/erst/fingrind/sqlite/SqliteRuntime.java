package dev.erst.fingrind.sqlite;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Public runtime metadata for the packaged SQLite adapter. */
public final class SqliteRuntime {
  public static final String STORAGE_DRIVER = "sqlite-ffm-sqlite3mc";
  public static final String STORAGE_ENGINE = "sqlite";
  public static final String BOOK_PROTECTION_MODE = "required";
  public static final String DEFAULT_BOOK_CIPHER = "chacha20";
  public static final String LIBRARY_ENVIRONMENT_VARIABLE = "FINGRIND_SQLITE_LIBRARY";
  public static final String BUNDLE_HOME_SYSTEM_PROPERTY = "fingrind.bundle.home";
  public static final String LIBRARY_MODE = "managed-only";
  public static final String REQUIRED_MINIMUM_SQLITE_VERSION = "3.53.0";
  public static final String REQUIRED_SQLITE3MC_VERSION = "2.3.3";
  public static final List<String> REQUIRED_SQLITE_COMPILE_OPTIONS =
      List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE");

  private SqliteRuntime() {}

  /** Reads the loaded SQLite library version through the Java 26 FFM bridge. */
  public static String sqliteVersion() {
    return SqliteNativeLibrary.sqliteVersion();
  }

  /** Reads the loaded SQLite3 Multiple Ciphers version through the Java 26 FFM bridge. */
  public static String sqlite3MultipleCiphersVersion() {
    return SqliteNativeLibrary.sqlite3MultipleCiphersVersion();
  }

  /** Probes the packaged SQLite runtime without throwing, for CLI discovery surfaces. */
  public static Probe probe() {
    return probe(
        SqliteNativeLibrary::configuredLibraryMode,
        SqliteNativeLibrary::sqliteVersion,
        SqliteNativeLibrary::sqlite3MultipleCiphersVersion,
        SqliteRuntime::failureDetail);
  }

  /** Normalizes a runtime probe failure into one stable sentence for machine-facing surfaces. */
  public static String failureDetail(Throwable throwable) {
    return Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName());
  }

  static Probe probe(
      Supplier<String> libraryModeSupplier,
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3MultipleCiphersVersionSupplier,
      Function<Throwable, String> failureDetail) {
    Objects.requireNonNull(libraryModeSupplier, "libraryModeSupplier");
    Objects.requireNonNull(sqliteVersionSupplier, "sqliteVersionSupplier");
    Objects.requireNonNull(
        sqlite3MultipleCiphersVersionSupplier, "sqlite3MultipleCiphersVersionSupplier");
    Objects.requireNonNull(failureDetail, "failureDetail");

    String libraryMode = libraryModeSupplier.get();
    try {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          Status.READY,
          sqliteVersionSupplier.get(),
          sqlite3MultipleCiphersVersionSupplier.get(),
          null);
    } catch (UnsupportedSqliteVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          Status.INCOMPATIBLE,
          exception.loadedVersion(),
          sqlite3MultipleCiphersVersionSupplier.get(),
          failureDetail.apply(exception));
    } catch (UnsupportedSqliteMultipleCiphersVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          Status.INCOMPATIBLE,
          sqliteVersionSupplier.get(),
          exception.loadedVersion(),
          failureDetail.apply(exception));
    } catch (UnsupportedSqliteCompileOptionsException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          Status.INCOMPATIBLE,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          failureDetail.apply(exception));
    } catch (RuntimeException | Error throwable) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          Status.UNAVAILABLE,
          null,
          null,
          failureDetail.apply(throwable));
    }
  }

  /** Machine-facing runtime state for one SQLite probe. */
  public record Probe(
      String libraryMode,
      String requiredMinimumSqliteVersion,
      String requiredSqlite3mcVersion,
      Status status,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String issue) {
    public Probe {
      libraryMode = requireText(libraryMode, "libraryMode");
      requiredMinimumSqliteVersion =
          requireText(requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
      requiredSqlite3mcVersion = requireText(requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
      Objects.requireNonNull(status, "status");
      loadedSqliteVersion = normalizeNullableText(loadedSqliteVersion);
      loadedSqlite3mcVersion = normalizeNullableText(loadedSqlite3mcVersion);
      issue = normalizeNullableText(issue);
      if (status == Status.READY) {
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is READY.");
        }
        if (loadedSqlite3mcVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqlite3mcVersion is required when SQLite runtime status is READY.");
        }
        if (issue != null) {
          throw new IllegalArgumentException(
              "issue must be absent when SQLite runtime status is READY.");
        }
      } else if (status == Status.UNAVAILABLE) {
        if (loadedSqliteVersion != null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion must be absent when SQLite runtime status is UNAVAILABLE.");
        }
        if (issue == null) {
          throw new IllegalArgumentException(
              "issue is required when SQLite runtime status is UNAVAILABLE.");
        }
        if (loadedSqlite3mcVersion != null) {
          throw new IllegalArgumentException(
              "loadedSqlite3mcVersion must be absent when SQLite runtime status is UNAVAILABLE.");
        }
      } else {
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (loadedSqlite3mcVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqlite3mcVersion is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (issue == null) {
          throw new IllegalArgumentException(
              "issue is required when SQLite runtime status is INCOMPATIBLE.");
        }
      }
    }

    private static String requireText(@Nullable String value, String fieldName) {
      String normalized = normalizeNullableText(value);
      if (normalized == null) {
        throw new IllegalArgumentException(fieldName + " must not be blank.");
      }
      return normalized;
    }

    private static @Nullable String normalizeNullableText(@Nullable String value) {
      if (value == null) {
        return null;
      }
      String normalized = value.trim();
      return normalized.isEmpty() ? null : normalized;
    }
  }

  /** Stable wire names for machine-readable runtime statuses. */
  public enum Status {
    READY("ready"),
    UNAVAILABLE("unavailable"),
    INCOMPATIBLE("incompatible");

    private final String wireValue;

    Status(String wireValue) {
      this.wireValue = wireValue;
    }

    public String wireValue() {
      return wireValue;
    }
  }
}
