package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;

/** Maps local caller path violations onto public failures and maintenance refusals. */
final class SqliteCallerPathFailureMapper {
  private SqliteCallerPathFailureMapper() {}

  static ContractFailure invalidBookFilePath(SqliteCallerPathContractException exception) {
    return ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.failureAt(
        exception.requestedPath(),
        bookFileMessage(exception.pathFailure()),
        SqliteBookFileSecuritySupport.invalidBookFilePathHint(),
        null);
  }

  static ContractFailure invalidBookKeyFile(SqliteCallerPathContractException exception) {
    return ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
        exception.requestedPath(),
        bookKeyFileMessage(exception.pathFailure()),
        SqliteBookKeyFileSecuritySupport.generalKeyFileHint(),
        null);
  }

  static ProtectedBookMaintenanceRejection.ArtifactPathInvalid maintenanceRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      SqliteCallerPathContractException exception) {
    return new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
        artifactRole, exception.requestedPath(), toMaintenancePathFailure(exception.pathFailure()));
  }

  private static ProtectedBookMaintenancePathFailure toMaintenancePathFailure(
      SqliteCallerPathFailure pathFailure) {
    return switch (pathFailure) {
      case MISSING_PARENT_DIRECTORY -> ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY;
      case PARENT_PATH_COLLISION -> ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION;
      case PARENT_OWNER_ACCESS_REQUIRED ->
          ProtectedBookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED;
      case PARENT_OWNER_ONLY_REQUIRED ->
          ProtectedBookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED;
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE ->
          ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE;
      case UNSUPPORTED_SECURE_FILESYSTEM ->
          ProtectedBookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM;
      case ATOMIC_SECRET_PUBLICATION_UNSUPPORTED ->
          ProtectedBookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED;
    };
  }

  private static String bookFileMessage(SqliteCallerPathFailure pathFailure) {
    return switch (pathFailure) {
      case MISSING_PARENT_DIRECTORY ->
          "The FinGrind protected-book path requires a parent directory.";
      case PARENT_PATH_COLLISION ->
          "The FinGrind protected-book path cannot use a parent path that already exists as a non-directory entry or symlink.";
      case PARENT_OWNER_ACCESS_REQUIRED ->
          "The FinGrind protected-book path requires a parent directory that the owner can traverse and write.";
      case PARENT_OWNER_ONLY_REQUIRED ->
          "The FinGrind protected-book path requires an owner-only parent directory.";
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE ->
          "The FinGrind protected-book path must resolve to a regular non-symlink file.";
      case UNSUPPORTED_SECURE_FILESYSTEM ->
          "The FinGrind protected-book path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs.";
      case ATOMIC_SECRET_PUBLICATION_UNSUPPORTED ->
          "The FinGrind protected-book path must live on a filesystem that supports atomic no-replace secret publication.";
    };
  }

  private static String bookKeyFileMessage(SqliteCallerPathFailure pathFailure) {
    return switch (pathFailure) {
      case MISSING_PARENT_DIRECTORY ->
          "The FinGrind book key file path requires a parent directory.";
      case PARENT_PATH_COLLISION ->
          "The FinGrind book key file path cannot use a parent path that already exists as a non-directory entry or symlink.";
      case PARENT_OWNER_ACCESS_REQUIRED ->
          "The FinGrind book key file path requires a parent directory that the owner can traverse and write.";
      case PARENT_OWNER_ONLY_REQUIRED ->
          "The FinGrind book key file path requires an owner-only parent directory.";
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE ->
          "The FinGrind book key file path must resolve to a regular non-symlink file.";
      case UNSUPPORTED_SECURE_FILESYSTEM ->
          "The FinGrind book key file path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs.";
      case ATOMIC_SECRET_PUBLICATION_UNSUPPORTED ->
          "The FinGrind book key file path must live on a filesystem that supports atomic no-replace secret publication.";
    };
  }
}
