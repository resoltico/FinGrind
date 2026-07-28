package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.nio.file.Path;
import java.util.Objects;

/** Maps shared private-output ancestry admission onto the SQLite caller-path contract. */
final class SqlitePrivateOutputDirectoryAdmission {
  private SqlitePrivateOutputDirectoryAdmission() {}

  static void requireOwnerOnlyNonMutableAncestry(Path requestedPath, Path parentDirectory) {
    Path checkedRequestedPath = Objects.requireNonNull(requestedPath, "requestedPath");
    Path checkedParentDirectory = Objects.requireNonNull(parentDirectory, "parentDirectory");
    try {
      PrivateOutputDirectory.requireExistingOwnerOnly(checkedParentDirectory);
    } catch (PrivateOutputDirectory.Violation violation) {
      throw admissionFailure(
          checkedRequestedPath,
          checkedParentDirectory,
          "The FinGrind protected-book path requires a private, non-mutable parent ancestry: ",
          violation,
          false);
    }
  }

  static void createNewPosixOwnerOnlyDirectories(Path requestedPath, Path plannedParentDirectory) {
    Path checkedRequestedPath = Objects.requireNonNull(requestedPath, "requestedPath");
    Path checkedPlannedParentDirectory =
        Objects.requireNonNull(plannedParentDirectory, "plannedParentDirectory");
    try {
      PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(checkedPlannedParentDirectory);
    } catch (PrivateOutputDirectory.Violation violation) {
      throw admissionFailure(
          checkedRequestedPath,
          checkedPlannedParentDirectory,
          "The FinGrind protected-book path requires a private, non-mutable parent ancestry before directory creation: ",
          violation,
          true);
    }
  }

  static SqliteCallerPathContractException atomicOwnerOnlyDirectoryCreationUnsupported(
      Path requestedPath) {
    return new SqliteCallerPathContractException(
        Objects.requireNonNull(requestedPath, "requestedPath"),
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        "The selected filesystem cannot atomically create one owner-only FinGrind parent directory.");
  }

  private static SqliteCallerPathContractException admissionFailure(
      Path requestedPath,
      Path parentDirectory,
      String messagePrefix,
      PrivateOutputDirectory.Violation violation,
      boolean directoryCreationWasAttempted) {
    SqliteCallerPathFailure failure =
        failureFor(
            violation.kind(), violation.getCause(), directoryCreationWasAttempted);
    return new SqliteCallerPathContractException(
        requestedPath,
        failure,
        messagePrefix + SqliteMachinePaths.absoluteValue(parentDirectory),
        violation);
  }

  static SqliteCallerPathFailure failureFor(
      PrivateOutputDirectory.Violation.Kind violationKind,
      @org.jspecify.annotations.Nullable Throwable violationCause,
      boolean directoryCreationWasAttempted) {
    return switch (violationKind) {
      case PATH_COLLISION -> SqliteCallerPathFailure.PARENT_PATH_COLLISION;
      case OWNER_ONLY_REQUIRED ->
          directoryCreationWasAttempted
                  && violationCause instanceof UnsupportedOperationException
              ? SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED
              : SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED;
    };
  }
}
