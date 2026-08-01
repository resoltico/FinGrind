package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeStateValidator;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Public runtime metadata for the packaged SQLite adapter. */
public final class SqliteRuntime {
  public static final String STORAGE_DRIVER = ProtocolCatalog.runtime().storageDriver().wireValue();
  public static final String STORAGE_ENGINE = ProtocolCatalog.runtime().storageEngine().wireValue();
  public static final String BOOK_PROTECTION_MODE =
      ProtocolCatalog.runtime().bookProtectionMode().wireValue();
  public static final String DEFAULT_BOOK_CIPHER =
      ProtocolCatalog.runtime().defaultBookCipher().wireValue();
  public static final String BUNDLE_HOME_SYSTEM_PROPERTY =
      ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty();
  public static final String LIBRARY_MODE =
      ProtocolCatalog.runtime().sqliteLibraryMode().wireValue();
  public static final String REQUIRED_MINIMUM_SQLITE_VERSION =
      ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion();
  public static final String REQUIRED_SQLITE3MC_VERSION =
      ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion();
  public static final String REQUIRED_SQLITE_SOURCE_ID =
      ProtocolCatalog.managedSqlite().requiredSqliteSourceId();
  public static final List<String> REQUIRED_SQLITE_COMPILE_OPTIONS =
      ProtocolCatalog.managedSqlite().requiredCompileOptions();
  public static final List<String> FORBIDDEN_SQLITE_COMPILE_OPTIONS =
      ProtocolCatalog.managedSqlite().forbiddenCompileOptions();
  public static final boolean REQUIRES_SECURE_MEMORY_SUPPORT =
      ProtocolCatalog.managedSqlite().requiresSecureMemorySupport();

  private SqliteRuntime() {}

  /** Reads the loaded SQLite library version through the Java 26 FFM bridge. */
  public static String sqliteVersion() {
    return SqliteNativeRuntimeMetadata.sqliteVersion();
  }

  /** Reads the loaded SQLite3 Multiple Ciphers version through the Java 26 FFM bridge. */
  public static String sqlite3MultipleCiphersVersion() {
    return SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion();
  }

  /** Reads the loaded SQLite source identifier through the Java 26 FFM bridge. */
  public static String sqliteSourceId() {
    return SqliteNativeRuntimeMetadata.sqliteSourceId();
  }

  /** Returns the provenance class for the loaded SQLite runtime artifact. */
  public static SqliteRuntimeProvenance runtimeProvenance() {
    return SqliteNativeBootstrap.api().runtimeProvenance();
  }

  /** Returns the public trust basis for the loaded SQLite runtime artifact. */
  public static SqliteRuntimeTrustBasis runtimeTrustBasis() {
    return SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance());
  }

  /** Returns the resolved absolute path for the loaded SQLite runtime artifact. */
  public static String loadedLibraryPath() {
    return absoluteLoadedLibraryPath(SqliteNativeBootstrap.api().loadedLibraryPath());
  }

  /**
   * Releases the process-scoped native runtime after a completed FinGrind command invocation.
   *
   * <p>This is terminal for the current process: callers must not use the SQLite runtime again
   * after releasing it.
   */
  public static void releaseProcessScopedRuntime() {
    SqliteNativeBootstrap.releaseProcessScopedRuntime();
  }

  /** Probes the packaged SQLite runtime without throwing, for CLI discovery surfaces. */
  public static Probe probe() {
    return SqliteRuntimeProbeSupport.probeConfiguredTarget(
        () ->
            SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier) {
    return SqliteRuntimeProbeSupport.probeConfiguredTarget(configuredLibraryTargetSupplier);
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier, Module nativeAccessModule) {
    return SqliteRuntimeProbeSupport.probeConfiguredTarget(
        configuredLibraryTargetSupplier, nativeAccessModule);
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier,
      Module nativeAccessModule,
      boolean nativeAccessEnabled) {
    return SqliteRuntimeProbeSupport.probeConfiguredTarget(
        configuredLibraryTargetSupplier, nativeAccessModule, nativeAccessEnabled);
  }

  /** Normalizes a runtime probe failure into one stable sentence for machine-facing surfaces. */
  public static String failureDetail(Throwable throwable) {
    return SqliteRuntimePathEvidenceSupport.failureDetail(throwable);
  }

  static Probe probe(
      Supplier<String> libraryModeSupplier,
      Supplier<SqliteRuntimeProvenance> runtimeProvenanceSupplier,
      Supplier<String> loadedLibraryPathSupplier,
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3MultipleCiphersVersionSupplier,
      Supplier<String> sqliteSourceIdSupplier,
      Function<Throwable, String> failureDetail) {
    return SqliteRuntimeProbeSupport.probe(
        libraryModeSupplier,
        runtimeProvenanceSupplier,
        loadedLibraryPathSupplier,
        sqliteVersionSupplier,
        sqlite3MultipleCiphersVersionSupplier,
        sqliteSourceIdSupplier,
        failureDetail);
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
      @Nullable SqliteRuntimeTrustBasis runtimeTrustBasis,
      @Nullable String loadedLibraryPath,
      @Nullable String loadedSqliteVersion,
      @Nullable String loadedSqlite3mcVersion,
      @Nullable String loadedSqliteSourceId,
      @Nullable String issue,
      @Nullable SqliteRuntimeArtifactEvidence runtimeArtifactEvidence) {
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
      if (runtimeProvenance != null && runtimeTrustBasis == null) {
        runtimeTrustBasis = SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance);
      }
      loadedLibraryPath = normalizeNullableText(loadedLibraryPath);
      loadedSqliteVersion = normalizeNullableText(loadedSqliteVersion);
      loadedSqlite3mcVersion = normalizeNullableText(loadedSqlite3mcVersion);
      loadedSqliteSourceId = normalizeNullableText(loadedSqliteSourceId);
      issue = normalizeNullableText(issue);
      SqliteRuntimeStateValidator.validate(
          compileOptionsVerification,
          contractStatus(status),
          runtimeProvenance,
          runtimeTrustBasis,
          loadedLibraryPath,
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          issue);
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
      String normalized = value.strip();
      return normalized.isEmpty() ? null : normalized;
    }

    private static SqliteRuntimeStatus contractStatus(Status status) {
      return switch (Objects.requireNonNull(status, "status")) {
        case READY -> SqliteRuntimeStatus.READY;
        case UNAVAILABLE -> SqliteRuntimeStatus.UNAVAILABLE;
        case FAILED -> SqliteRuntimeStatus.FAILED;
        case INCOMPATIBLE -> SqliteRuntimeStatus.INCOMPATIBLE;
      };
    }
  }

  /** Stable wire names for machine-readable runtime statuses. */
  public enum Status implements WireValue {
    READY("ready"),
    UNAVAILABLE("unavailable"),
    FAILED("failed"),
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

  static String absoluteLoadedLibraryPath(String loadedLibraryPath) {
    return SqliteRuntimePathEvidenceSupport.absolutePath(loadedLibraryPath);
  }

  static @Nullable SqliteRuntimeArtifactEvidence artifactEvidence(String loadedLibraryPath) {
    return SqliteRuntimePathEvidenceSupport.artifactEvidence(loadedLibraryPath);
  }
}
