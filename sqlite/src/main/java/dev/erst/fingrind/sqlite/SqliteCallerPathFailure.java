package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedPublicationPathFailure;

/** Stable local vocabulary for caller-controlled protected-book path-contract violations. */
enum SqliteCallerPathFailure {
  MISSING_PARENT_DIRECTORY(ProtectedPublicationPathFailure.MISSING_PARENT_DIRECTORY),
  PARENT_PATH_COLLISION(ProtectedPublicationPathFailure.PARENT_PATH_COLLISION),
  PARENT_OWNER_ACCESS_REQUIRED(ProtectedPublicationPathFailure.PARENT_OWNER_ACCESS_REQUIRED),
  PARENT_OWNER_ONLY_REQUIRED(ProtectedPublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED),
  ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE(
      ProtectedPublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE),
  TARGET_OWNER_ONLY_REQUIRED(ProtectedPublicationPathFailure.TARGET_OWNER_ONLY_REQUIRED),
  TARGET_IDENTITY_UNESTABLISHED(ProtectedPublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED),
  SOURCE_ARTIFACT_IDENTITY_DUPLICATED(
      ProtectedPublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED),
  SOURCE_ARTIFACT_IDENTITY_CHANGED(
      ProtectedPublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED),
  UNSUPPORTED_SECURE_FILESYSTEM(ProtectedPublicationPathFailure.UNSUPPORTED_SECURE_FILESYSTEM),
  ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED(
      ProtectedPublicationPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED),
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED(
      ProtectedPublicationPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED),
  ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED(
      ProtectedPublicationPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED),
  ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED(
      ProtectedPublicationPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED);

  private final ProtectedPublicationPathFailure maintenanceFailure;

  SqliteCallerPathFailure(ProtectedPublicationPathFailure maintenanceFailure) {
    this.maintenanceFailure = maintenanceFailure;
  }

  ProtectedPublicationPathFailure maintenanceFailure() {
    return maintenanceFailure;
  }
}
