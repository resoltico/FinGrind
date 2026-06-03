package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Shared redaction and evidence helpers for public SQLite runtime inspection surfaces. */
final class SqliteRuntimePathEvidenceSupport {
  private static final Pattern PATH_TOKEN = Pattern.compile("([A-Za-z]:\\\\[^\\s]+|/[^\\s]+)");
  private static final String TOOLCHAIN_FINGERPRINT_FILE_NAME = "toolchain-fingerprint.json";
  private static final String BUILD_CONTRACT_FILE_NAME = "build-contract.json";

  private SqliteRuntimePathEvidenceSupport() {}

  static String failureDetail(Throwable throwable) {
    return redactPathDetails(
        Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getSimpleName()));
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

  static int trailingPunctuationStart(String rawPath) {
    String normalized = Objects.requireNonNull(rawPath, "rawPath");
    int end = normalized.length();
    while (end > 0 && isTrailingPunctuation(normalized.charAt(end - 1))) {
      end--;
    }
    return end;
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

  private static boolean isTrailingPunctuation(char candidate) {
    return candidate == '.'
        || candidate == ','
        || candidate == ';'
        || candidate == ':'
        || candidate == ')'
        || candidate == ']';
  }
}
