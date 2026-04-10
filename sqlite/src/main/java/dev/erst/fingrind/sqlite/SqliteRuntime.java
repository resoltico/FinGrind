package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Public runtime metadata for the packaged SQLite adapter. */
public final class SqliteRuntime {
  public static final String STORAGE_DRIVER = "sqlite-ffm";
  public static final String STORAGE_ENGINE = "sqlite";
  public static final String LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE = "FINGRIND_SQLITE_LIBRARY";
  public static final String REQUIRED_MINIMUM_SQLITE_VERSION = "3.53.0";

  private SqliteRuntime() {}

  /** Reads the loaded SQLite library version through the Java 26 FFM bridge. */
  public static String sqliteVersion() {
    return SqliteNativeLibrary.sqliteVersion();
  }

  /** Probes the packaged SQLite runtime without throwing, for CLI discovery surfaces. */
  public static Probe probe() {
    return probe(
        SqliteNativeLibrary::configuredLibrarySource,
        SqliteNativeLibrary::sqliteVersion,
        SqliteRuntime::failureDetail);
  }

  /** Normalizes a runtime probe failure into one stable sentence for machine-facing surfaces. */
  public static String failureDetail(Throwable throwable) {
    return Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName());
  }

  static Probe probe(
      Supplier<String> librarySourceSupplier,
      Supplier<String> sqliteVersionSupplier,
      Function<Throwable, String> failureDetail) {
    Objects.requireNonNull(librarySourceSupplier, "librarySourceSupplier");
    Objects.requireNonNull(sqliteVersionSupplier, "sqliteVersionSupplier");
    Objects.requireNonNull(failureDetail, "failureDetail");

    String librarySource = librarySourceSupplier.get();
    try {
      return new Probe(
          librarySource,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          Status.READY,
          sqliteVersionSupplier.get(),
          null);
    } catch (UnsupportedSqliteVersionException exception) {
      return new Probe(
          librarySource,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          Status.INCOMPATIBLE,
          exception.loadedVersion(),
          failureDetail.apply(exception));
    } catch (RuntimeException | Error throwable) {
      return new Probe(
          librarySource,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          Status.UNAVAILABLE,
          null,
          failureDetail.apply(throwable));
    }
  }

  /** Machine-facing runtime state for one SQLite probe. */
  public record Probe(
      String librarySource,
      String requiredMinimumSqliteVersion,
      Status status,
      String loadedSqliteVersion,
      String issue) {
    public Probe {
      librarySource = requireText(librarySource, "librarySource");
      requiredMinimumSqliteVersion =
          requireText(requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
      Objects.requireNonNull(status, "status");
      loadedSqliteVersion = normalizeNullableText(loadedSqliteVersion);
      issue = normalizeNullableText(issue);
      if (status == Status.READY) {
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is READY.");
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
      } else {
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (issue == null) {
          throw new IllegalArgumentException(
              "issue is required when SQLite runtime status is INCOMPATIBLE.");
        }
      }
    }

    private static String requireText(String value, String fieldName) {
      String normalized = normalizeNullableText(value);
      if (normalized == null) {
        throw new IllegalArgumentException(fieldName + " must not be blank.");
      }
      return normalized;
    }

    private static String normalizeNullableText(String value) {
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
