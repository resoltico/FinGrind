package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;

/** Stable local vocabulary for caller-controlled protected-book path-contract violations. */
enum SqliteCallerPathFailure {
  MISSING_PARENT_DIRECTORY(ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY),
  PARENT_PATH_COLLISION(ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION),
  PARENT_OWNER_ACCESS_REQUIRED(ProtectedBookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED),
  PARENT_OWNER_ONLY_REQUIRED(ProtectedBookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED),
  ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE(
      ProtectedBookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE),
  TARGET_OWNER_ONLY_REQUIRED(ProtectedBookMaintenancePathFailure.TARGET_OWNER_ONLY_REQUIRED),
  PAIR_TARGET_LEAF_PORTABILITY_REQUIRED(
      ProtectedBookMaintenancePathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED),
  TARGET_IDENTITY_UNESTABLISHED(ProtectedBookMaintenancePathFailure.TARGET_IDENTITY_UNESTABLISHED),
  SOURCE_ARTIFACT_IDENTITY_DUPLICATED(
      ProtectedBookMaintenancePathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED),
  SOURCE_ARTIFACT_IDENTITY_CHANGED(
      ProtectedBookMaintenancePathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED),
  UNSUPPORTED_SECURE_FILESYSTEM(ProtectedBookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM),
  ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED(
      ProtectedBookMaintenancePathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED),
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED(
      ProtectedBookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED),
  ATOMIC_BOOK_PUBLICATION_UNSUPPORTED(
      ProtectedBookMaintenancePathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED),
  ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED(
      ProtectedBookMaintenancePathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED);

  private final ProtectedBookMaintenancePathFailure maintenanceFailure;

  SqliteCallerPathFailure(ProtectedBookMaintenancePathFailure maintenanceFailure) {
    this.maintenanceFailure = maintenanceFailure;
  }

  ProtectedBookMaintenancePathFailure maintenanceFailure() {
    return maintenanceFailure;
  }
}
