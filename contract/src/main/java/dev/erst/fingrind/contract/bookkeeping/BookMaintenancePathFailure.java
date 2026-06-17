package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable public path-failure vocabulary for protected-book maintenance refusals. */
public enum BookMaintenancePathFailure implements WireValue {
  MISSING_PARENT_DIRECTORY,
  PARENT_PATH_COLLISION,
  PARENT_OWNER_ACCESS_REQUIRED,
  PARENT_OWNER_ONLY_REQUIRED,
  TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
  UNSUPPORTED_SECURE_FILESYSTEM;

  @Override
  public String wireValue() {
    return switch (this) {
      case MISSING_PARENT_DIRECTORY -> "missing-parent-directory";
      case PARENT_PATH_COLLISION -> "parent-path-collision";
      case PARENT_OWNER_ACCESS_REQUIRED -> "parent-owner-access-required";
      case PARENT_OWNER_ONLY_REQUIRED -> "parent-owner-only-required";
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE -> "target-must-be-regular-non-symlink-file";
      case UNSUPPORTED_SECURE_FILESYSTEM -> "unsupported-secure-filesystem";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMaintenancePathFailure.class);
  }
}
