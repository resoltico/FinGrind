package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable public path-failure vocabulary for protected-book maintenance refusals. */
public enum BookMaintenancePathFailure implements WireValue {
  MISSING_PARENT_DIRECTORY("missing-parent-directory"),
  PARENT_PATH_COLLISION("parent-path-collision"),
  PARENT_OWNER_ACCESS_REQUIRED("parent-owner-access-required"),
  PARENT_OWNER_ONLY_REQUIRED("parent-owner-only-required"),
  ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE("artifact-must-be-regular-non-symlink-file"),
  TARGET_OWNER_ONLY_REQUIRED("target-owner-only-required"),
  TARGET_IDENTITY_UNESTABLISHED("target-identity-unestablished"),
  SOURCE_ARTIFACT_IDENTITY_DUPLICATED("source-artifact-identity-duplicated"),
  SOURCE_ARTIFACT_IDENTITY_CHANGED("source-artifact-identity-changed"),
  UNSUPPORTED_SECURE_FILESYSTEM("unsupported-secure-filesystem"),
  ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED(
      "atomic-owner-only-protocol-file-creation-unsupported"),
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED("atomic-secret-publication-unsupported"),
  ATOMIC_BOOK_PUBLICATION_UNSUPPORTED("atomic-book-publication-unsupported"),
  ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED("atomic-book-replacement-unsupported");

  private final String wireValue;

  BookMaintenancePathFailure(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMaintenancePathFailure.class);
  }
}
