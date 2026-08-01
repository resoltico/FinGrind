package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PrivateOutputFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Maps the core owner-only file vocabulary onto SQLite's caller-path contract. */
final class SqlitePrivateOutputFileFailures {
  private SqlitePrivateOutputFileFailures() {}

  static SqliteCallerPathContractException map(
      Path artifactPath, PrivateOutputFile.OwnerOnlyFileViolation violation) {
    Path checkedPath = Objects.requireNonNull(artifactPath, "artifactPath");
    PrivateOutputFile.OwnerOnlyFileViolation checkedViolation =
        Objects.requireNonNull(violation, "violation");
    return map(checkedPath, checkedViolation.kind(), checkedViolation);
  }

  static SqliteCallerPathContractException map(
      Path artifactPath, PrivateOutputFile.ViolationKind violationKind, Throwable cause) {
    Path checkedPath = Objects.requireNonNull(artifactPath, "artifactPath");
    PrivateOutputFile.ViolationKind checkedKind =
        Objects.requireNonNull(violationKind, "violationKind");
    Throwable checkedCause = Objects.requireNonNull(cause, "cause");
    return switch (checkedKind) {
      case MISSING_PARENT -> missingParent(checkedPath, checkedCause);
      case PARENT_OWNER_ONLY_REQUIRED -> parentAdmissionFailure(checkedPath, checkedCause);
      case REGULAR_NON_SYMLINK_REQUIRED ->
          failure(
              checkedPath,
              SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
              "The FinGrind-owned artifact path must resolve to an existing regular non-symlink file: ",
              checkedCause);
      case OWNER_ONLY_REQUIRED ->
          failure(
              checkedPath,
              SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
              "The FinGrind-owned artifact must already use owner-only permissions: ",
              checkedCause);
      case ATOMIC_CREATION_UNSUPPORTED ->
          failure(
              checkedPath,
              SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
              "The selected filesystem cannot atomically create one owner-only FinGrind artifact: ",
              checkedCause);
    };
  }

  private static SqliteCallerPathContractException parentAdmissionFailure(
      Path artifactPath, Throwable cause) {
    Path parent = Objects.requireNonNull(artifactPath.getParent(), "artifactPath parent");
    if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
      return missingParent(artifactPath, cause);
    }
    if (cause.getCause() instanceof PrivateOutputDirectory.Violation directoryViolation
        && directoryViolation.kind() == PrivateOutputDirectory.Violation.Kind.PATH_COLLISION) {
      return failure(
          artifactPath,
          SqliteCallerPathFailure.PARENT_PATH_COLLISION,
          "The FinGrind-owned artifact path requires a real parent directory: ",
          cause);
    }
    return failure(
        artifactPath,
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        "The FinGrind-owned artifact path must resolve beneath an existing owner-only parent directory: ",
        cause);
  }

  private static SqliteCallerPathContractException missingParent(
      Path artifactPath, Throwable cause) {
    Objects.requireNonNull(artifactPath.getParent(), "artifactPath parent");
    return failure(
        artifactPath,
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        "The FinGrind-owned artifact path must resolve beneath an existing parent directory: ",
        cause);
  }

  private static SqliteCallerPathContractException failure(
      Path artifactPath, SqliteCallerPathFailure failure, String messagePrefix, Throwable cause) {
    return new SqliteCallerPathContractException(
        artifactPath,
        failure,
        messagePrefix + SqliteBookFilesystemSupport.absolutePath(artifactPath),
        cause);
  }
}
