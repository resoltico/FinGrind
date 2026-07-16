package dev.erst.fingrind.executor.maintenance;

/** Local path-failure vocabulary for protected-book maintenance refusals. */
public enum ProtectedBookMaintenancePathFailure {
  MISSING_PARENT_DIRECTORY,
  PARENT_PATH_COLLISION,
  PARENT_OWNER_ACCESS_REQUIRED,
  PARENT_OWNER_ONLY_REQUIRED,
  TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
  UNSUPPORTED_SECURE_FILESYSTEM,
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED
}
