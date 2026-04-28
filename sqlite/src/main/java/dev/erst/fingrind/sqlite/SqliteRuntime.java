package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Public runtime metadata for the packaged SQLite adapter. */
public final class SqliteRuntime {
  public static final String STORAGE_DRIVER = ProtocolCatalog.storageDriver().wireValue();
  public static final String STORAGE_ENGINE = ProtocolCatalog.storageEngine().wireValue();
  public static final String BOOK_PROTECTION_MODE =
      ProtocolCatalog.bookProtectionMode().wireValue();
  public static final String DEFAULT_BOOK_CIPHER = ProtocolCatalog.defaultBookCipher().wireValue();
  public static final String LIBRARY_ENVIRONMENT_VARIABLE =
      ProtocolCatalog.sqliteLibraryEnvironmentVariable();
  public static final String BUNDLE_HOME_SYSTEM_PROPERTY =
      ProtocolCatalog.sqliteBundleHomeSystemProperty();
  public static final String LIBRARY_MODE = ProtocolCatalog.sqliteLibraryMode().wireValue();
  public static final String REQUIRED_MINIMUM_SQLITE_VERSION =
      ProtocolCatalog.requiredMinimumSqliteVersion();
  public static final String REQUIRED_SQLITE3MC_VERSION =
      ProtocolCatalog.requiredSqlite3mcVersion();
  public static final String REQUIRED_SQLITE_SOURCE_ID = ProtocolCatalog.requiredSqliteSourceId();
  public static final List<String> REQUIRED_SQLITE_COMPILE_OPTIONS =
      ProtocolCatalog.requiredSqliteCompileOptions();

  private SqliteRuntime() {}

  /** Reads the loaded SQLite library version through the Java 26 FFM bridge. */
  public static String sqliteVersion() {
    return SqliteNativeBootstrap.sqliteVersion();
  }

  /** Reads the loaded SQLite3 Multiple Ciphers version through the Java 26 FFM bridge. */
  public static String sqlite3MultipleCiphersVersion() {
    return SqliteNativeBootstrap.sqlite3MultipleCiphersVersion();
  }

  /** Reads the loaded SQLite source identifier through the Java 26 FFM bridge. */
  public static String sqliteSourceId() {
    return SqliteNativeBootstrap.sqliteSourceId();
  }

  /** Returns the provenance class for the loaded SQLite runtime artifact. */
  public static SqliteRuntimeProvenance runtimeProvenance() {
    return SqliteNativeBootstrap.api().runtimeProvenance();
  }

  /** Returns the resolved absolute path for the loaded SQLite runtime artifact. */
  public static String loadedLibraryPath() {
    return SqliteNativeBootstrap.api().loadedLibraryPath();
  }

  /** Probes the packaged SQLite runtime without throwing, for CLI discovery surfaces. */
  public static Probe probe() {
    return probeConfiguredTarget(
        () ->
            SqliteNativeRuntimePolicy.configuredLibraryTarget(
                System.getenv(SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE),
                System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier) {
    SqliteLibraryTarget configuredLibraryTarget;
    try {
      configuredLibraryTarget = configuredLibraryTargetSupplier.get();
    } catch (RuntimeException | Error throwable) {
      return new Probe(
          LIBRARY_MODE,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.UNAVAILABLE,
          null,
          null,
          null,
          null,
          null,
          failureDetail(throwable));
    }
    return probe(
        () -> configuredLibraryTarget.mode(),
        () -> configuredLibraryTarget.provenance(),
        () -> configuredLibraryTarget.lookupTarget(),
        SqliteNativeBootstrap::sqliteVersion,
        SqliteNativeBootstrap::sqlite3MultipleCiphersVersion,
        SqliteNativeBootstrap::sqliteSourceId,
        SqliteRuntime::failureDetail);
  }

  /** Normalizes a runtime probe failure into one stable sentence for machine-facing surfaces. */
  public static String failureDetail(Throwable throwable) {
    return Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName());
  }

  static Probe probe(
      Supplier<String> libraryModeSupplier,
      Supplier<SqliteRuntimeProvenance> runtimeProvenanceSupplier,
      Supplier<String> loadedLibraryPathSupplier,
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3MultipleCiphersVersionSupplier,
      Supplier<String> sqliteSourceIdSupplier,
      Function<Throwable, String> failureDetail) {
    Objects.requireNonNull(libraryModeSupplier, "libraryModeSupplier");
    Objects.requireNonNull(runtimeProvenanceSupplier, "runtimeProvenanceSupplier");
    Objects.requireNonNull(loadedLibraryPathSupplier, "loadedLibraryPathSupplier");
    Objects.requireNonNull(sqliteVersionSupplier, "sqliteVersionSupplier");
    Objects.requireNonNull(
        sqlite3MultipleCiphersVersionSupplier, "sqlite3MultipleCiphersVersionSupplier");
    Objects.requireNonNull(sqliteSourceIdSupplier, "sqliteSourceIdSupplier");
    Objects.requireNonNull(failureDetail, "failureDetail");

    String libraryMode = libraryModeSupplier.get();
    SqliteRuntimeProvenance runtimeProvenance = runtimeProvenanceSupplier.get();
    String loadedLibraryPath = loadedLibraryPathSupplier.get();
    try {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.VERIFIED,
          Status.READY,
          runtimeProvenance,
          loadedLibraryPath,
          sqliteVersionSupplier.get(),
          sqlite3MultipleCiphersVersionSupplier.get(),
          sqliteSourceIdSupplier.get(),
          null);
    } catch (UnsupportedSqliteVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception));
    } catch (UnsupportedSqliteMultipleCiphersVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception));
    } catch (UnsupportedSqliteSourceIdException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception));
    } catch (UnsupportedSqliteCompileOptionsException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.FAILED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception));
    } catch (RuntimeException | Error throwable) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.UNAVAILABLE,
          null,
          null,
          null,
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
      String requiredSqliteSourceId,
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      Status status,
      @Nullable SqliteRuntimeProvenance runtimeProvenance,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String issue) {
    public Probe {
      libraryMode = requireText(libraryMode, "libraryMode");
      if (!LIBRARY_MODE.equals(libraryMode)) {
        throw new IllegalArgumentException("libraryMode must be " + LIBRARY_MODE + ".");
      }
      requiredMinimumSqliteVersion =
          requireText(requiredMinimumSqliteVersion, "requiredMinimumSqliteVersion");
      requiredSqlite3mcVersion = requireText(requiredSqlite3mcVersion, "requiredSqlite3mcVersion");
      requiredSqliteSourceId = requireText(requiredSqliteSourceId, "requiredSqliteSourceId");
      Objects.requireNonNull(compileOptionsVerification, "compileOptionsVerification");
      Objects.requireNonNull(status, "status");
      loadedLibraryPath = normalizeNullableText(loadedLibraryPath);
      loadedSqliteVersion = normalizeNullableText(loadedSqliteVersion);
      loadedSqlite3mcVersion = normalizeNullableText(loadedSqlite3mcVersion);
      loadedSqliteSourceId = normalizeNullableText(loadedSqliteSourceId);
      issue = normalizeNullableText(issue);
      if (status == Status.READY) {
        if (compileOptionsVerification != SqliteCompileOptionsVerificationStatus.VERIFIED) {
          throw new IllegalArgumentException(
              "compileOptionsVerification must be VERIFIED when SQLite runtime status is READY.");
        }
        if (runtimeProvenance == null) {
          throw new IllegalArgumentException(
              "runtimeProvenance is required when SQLite runtime status is READY.");
        }
        if (loadedLibraryPath == null) {
          throw new IllegalArgumentException(
              "loadedLibraryPath is required when SQLite runtime status is READY.");
        }
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is READY.");
        }
        if (loadedSqlite3mcVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqlite3mcVersion is required when SQLite runtime status is READY.");
        }
        if (loadedSqliteSourceId == null) {
          throw new IllegalArgumentException(
              "loadedSqliteSourceId is required when SQLite runtime status is READY.");
        }
        if (issue != null) {
          throw new IllegalArgumentException(
              "issue must be absent when SQLite runtime status is READY.");
        }
      } else if (status == Status.UNAVAILABLE) {
        if (compileOptionsVerification != SqliteCompileOptionsVerificationStatus.NOT_VERIFIED) {
          throw new IllegalArgumentException(
              "compileOptionsVerification must be NOT_VERIFIED when SQLite runtime status is UNAVAILABLE.");
        }
        if (runtimeProvenance != null) {
          throw new IllegalArgumentException(
              "runtimeProvenance must be absent when SQLite runtime status is UNAVAILABLE.");
        }
        if (loadedLibraryPath != null) {
          throw new IllegalArgumentException(
              "loadedLibraryPath must be absent when SQLite runtime status is UNAVAILABLE.");
        }
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
        if (loadedSqliteSourceId != null) {
          throw new IllegalArgumentException(
              "loadedSqliteSourceId must be absent when SQLite runtime status is UNAVAILABLE.");
        }
      } else {
        if (compileOptionsVerification == SqliteCompileOptionsVerificationStatus.VERIFIED) {
          throw new IllegalArgumentException(
              "compileOptionsVerification must not be VERIFIED when SQLite runtime status is INCOMPATIBLE.");
        }
        if (runtimeProvenance == null) {
          throw new IllegalArgumentException(
              "runtimeProvenance is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (loadedLibraryPath == null) {
          throw new IllegalArgumentException(
              "loadedLibraryPath is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (loadedSqliteVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqliteVersion is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (loadedSqlite3mcVersion == null) {
          throw new IllegalArgumentException(
              "loadedSqlite3mcVersion is required when SQLite runtime status is INCOMPATIBLE.");
        }
        if (loadedSqliteSourceId == null) {
          throw new IllegalArgumentException(
              "loadedSqliteSourceId is required when SQLite runtime status is INCOMPATIBLE.");
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
  public enum Status implements WireValue {
    READY("ready"),
    UNAVAILABLE("unavailable"),
    INCOMPATIBLE("incompatible");

    private final String wireValue;

    Status(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }
}
