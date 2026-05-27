package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import dev.erst.fingrind.contract.runtime.SqliteRuntimeStateValidator;
import dev.erst.fingrind.core.WireValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Public runtime metadata for the packaged SQLite adapter. */
public final class SqliteRuntime {
  private static final Pattern PATH_TOKEN = Pattern.compile("([A-Za-z]:\\\\[^\\s]+|/[^\\s]+)");
  private static final String TOOLCHAIN_FINGERPRINT_FILE_NAME = "toolchain-fingerprint.json";
  private static final String BUILD_CONTRACT_FILE_NAME = "build-contract.json";
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
    return publicLoadedLibraryPath(SqliteNativeBootstrap.api().loadedLibraryPath());
  }

  /** Probes the packaged SQLite runtime without throwing, for CLI discovery surfaces. */
  public static Probe probe() {
    return probeConfiguredTarget(
        () ->
            SqliteManagedLibraryTargetLocator.configuredLibraryTarget(
                System.getenv("FINGRIND_SQLITE_LIBRARY"),
                System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier) {
    return probeConfiguredTarget(
        configuredLibraryTargetSupplier, SqliteNativeAccessGate.runtimeModule());
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier, Module nativeAccessModule) {
    return probeConfiguredTarget(
        configuredLibraryTargetSupplier,
        nativeAccessModule,
        SqliteNativeAccessGate.isEnabled(nativeAccessModule));
  }

  static Probe probeConfiguredTarget(
      Supplier<SqliteLibraryTarget> configuredLibraryTargetSupplier,
      Module nativeAccessModule,
      boolean nativeAccessEnabled) {
    Objects.requireNonNull(nativeAccessModule, "nativeAccessModule");
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
          null,
          failureDetail(throwable),
          null);
    }
    if (!nativeAccessEnabled) {
      return new Probe(
          configuredLibraryTarget.mode(),
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
          null,
          SqliteNativeAccessGate.failureMessage(nativeAccessModule),
          null);
    }
    return probe(
        () -> configuredLibraryTarget.mode(),
        () -> configuredLibraryTarget.provenance(),
        () -> configuredLibraryTarget.lookupTarget(),
        SqliteNativeRuntimeMetadata::sqliteVersion,
        SqliteNativeRuntimeMetadata::sqlite3MultipleCiphersVersion,
        SqliteNativeRuntimeMetadata::sqliteSourceId,
        SqliteRuntime::failureDetail);
  }

  /** Normalizes a runtime probe failure into one stable sentence for machine-facing surfaces. */
  public static String failureDetail(Throwable throwable) {
    return redactPathDetails(
        Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
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
    String loadedLibraryPath = publicLoadedLibraryPath(loadedLibraryPathSupplier.get());
    SqliteRuntimeArtifactEvidence runtimeArtifactEvidence =
        artifactEvidence(loadedLibraryPathSupplier.get());
    String loadedSqliteVersion = null;
    String loadedSqlite3mcVersion = null;
    String loadedSqliteSourceId = null;
    try {
      loadedSqliteVersion = sqliteVersionSupplier.get();
      loadedSqlite3mcVersion = sqlite3MultipleCiphersVersionSupplier.get();
      loadedSqliteSourceId = sqliteSourceIdSupplier.get();
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.VERIFIED,
          Status.READY,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          null,
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          exception.loadedVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteMultipleCiphersVersionException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteSourceIdException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (UnsupportedSqliteCompileOptionsException exception) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.FAILED,
          Status.INCOMPATIBLE,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          exception.loadedSqliteVersion(),
          exception.loadedSqlite3mcVersion(),
          exception.loadedSourceId(),
          failureDetail.apply(exception),
          runtimeArtifactEvidence);
    } catch (RuntimeException | Error throwable) {
      return new Probe(
          libraryMode,
          REQUIRED_MINIMUM_SQLITE_VERSION,
          REQUIRED_SQLITE3MC_VERSION,
          REQUIRED_SQLITE_SOURCE_ID,
          SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
          Status.FAILED,
          runtimeProvenance,
          SqliteRuntimeTrustBasis.fromProvenance(runtimeProvenance),
          loadedLibraryPath,
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          failureDetail.apply(throwable),
          runtimeArtifactEvidence);
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

  static String publicLoadedLibraryPath(String loadedLibraryPath) {
    String normalized = Objects.requireNonNull(loadedLibraryPath, "loadedLibraryPath").strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("loadedLibraryPath must not be blank.");
    }
    int lastSeparator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
    if (lastSeparator < 0 || lastSeparator == normalized.length() - 1) {
      return normalized;
    }
    return "<redacted>/" + normalized.substring(lastSeparator + 1);
  }

  private static @Nullable SqliteRuntimeArtifactEvidence artifactEvidence(
      String loadedLibraryPath) {
    Path libraryPath = Path.of(Objects.requireNonNull(loadedLibraryPath, "loadedLibraryPath"));
    Path parentDirectory = libraryPath.getParent();
    if (parentDirectory == null) {
      return null;
    }
    Path toolchainFingerprintPath = parentDirectory.resolve(TOOLCHAIN_FINGERPRINT_FILE_NAME);
    Path buildContractPath = parentDirectory.resolve(BUILD_CONTRACT_FILE_NAME);
    if (!Files.isRegularFile(toolchainFingerprintPath) || !Files.isRegularFile(buildContractPath)) {
      return null;
    }
    return new SqliteRuntimeArtifactEvidence(
        publicLoadedLibraryPath(toolchainFingerprintPath.toString()),
        SqliteManagedLibraryIdentity.actualSha256(toolchainFingerprintPath),
        publicLoadedLibraryPath(buildContractPath.toString()),
        SqliteManagedLibraryIdentity.actualSha256(buildContractPath));
  }

  private static String redactPathDetails(String message) {
    Matcher matcher = PATH_TOKEN.matcher(Objects.requireNonNull(message, "message"));
    StringBuffer redactedMessage = new StringBuffer();
    while (matcher.find()) {
      String rawPath = matcher.group(1);
      int pathEnd = trailingPunctuationStart(rawPath);
      String path = rawPath.substring(0, pathEnd);
      String trailingPunctuation = rawPath.substring(pathEnd);
      matcher.appendReplacement(
          redactedMessage,
          Matcher.quoteReplacement(publicLoadedLibraryPath(path) + trailingPunctuation));
    }
    matcher.appendTail(redactedMessage);
    return redactedMessage.toString();
  }

  static int trailingPunctuationStart(String rawPath) {
    String normalized = Objects.requireNonNull(rawPath, "rawPath");
    int end = normalized.length();
    while (end > 0 && isTrailingPunctuation(normalized.charAt(end - 1))) {
      end--;
    }
    return end;
  }

  private static boolean isTrailingPunctuation(char candidate) {
    return candidate == '.'
        || candidate == ','
        || candidate == ';'
        || candidate == ':'
        || candidate == ')'
        || candidate == ']';
  }
}
