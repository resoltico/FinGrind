package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared machine-path and provenance-evidence helpers for SQLite runtime inspection surfaces. */
final class SqliteRuntimePathEvidenceSupport {
  private static final String TOOLCHAIN_FINGERPRINT_FILE_NAME = "toolchain-fingerprint.json";
  private static final String BUILD_CONTRACT_FILE_NAME = "build-contract.json";

  private SqliteRuntimePathEvidenceSupport() {}

  static String failureDetail(Throwable throwable) {
    return Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName());
  }

  static String absolutePath(String loadedLibraryPath) {
    String normalized = Objects.requireNonNull(loadedLibraryPath, "loadedLibraryPath").strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("loadedLibraryPath must not be blank.");
    }
    if (isWindowsAbsolutePath(normalized) || normalized.startsWith("\\\\")) {
      return normalized;
    }
    return Path.of(normalized).toAbsolutePath().normalize().toString();
  }

  static @Nullable SqliteRuntimeArtifactEvidence artifactEvidence(String loadedLibraryPath) {
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
        absolutePath(toolchainFingerprintPath.toString()),
        SqliteManagedLibraryIdentity.actualSha256(toolchainFingerprintPath),
        absolutePath(buildContractPath.toString()),
        SqliteManagedLibraryIdentity.actualSha256(buildContractPath));
  }

  private static boolean isWindowsAbsolutePath(String candidate) {
    return candidate.length() >= 3
        && Character.isLetter(candidate.charAt(0))
        && candidate.charAt(1) == ':'
        && (candidate.charAt(2) == '/' || candidate.charAt(2) == '\\');
  }
}
