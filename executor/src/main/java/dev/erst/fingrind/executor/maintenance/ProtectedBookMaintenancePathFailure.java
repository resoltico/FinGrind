package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;

/** Local maintenance failure facts with their published vocabulary and caller-path language. */
public enum ProtectedBookMaintenancePathFailure {
  MISSING_PARENT_DIRECTORY(
      BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY,
      "The FinGrind protected-book path requires a parent directory.",
      "The FinGrind book key file path requires a parent directory."),
  PARENT_PATH_COLLISION(
      BookMaintenancePathFailure.PARENT_PATH_COLLISION,
      "The FinGrind protected-book path cannot use a parent path that already exists as a non-directory entry or symlink.",
      "The FinGrind book key file path cannot use a parent path that already exists as a non-directory entry or symlink."),
  PARENT_OWNER_ACCESS_REQUIRED(
      BookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED,
      "The FinGrind protected-book path requires a parent directory that the owner can traverse and write.",
      "The FinGrind book key file path requires a parent directory that the owner can traverse and write."),
  PARENT_OWNER_ONLY_REQUIRED(
      BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED,
      "The FinGrind protected-book path requires an owner-only parent directory.",
      "The FinGrind book key file path requires an owner-only parent directory."),
  ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE(
      BookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
      "The FinGrind protected-book path must resolve to a regular non-symlink file.",
      "The FinGrind book key file path must resolve to a regular non-symlink file."),
  TARGET_OWNER_ONLY_REQUIRED(
      BookMaintenancePathFailure.TARGET_OWNER_ONLY_REQUIRED,
      "The FinGrind protected-book path must already use owner-only permissions.",
      "The FinGrind book key file path must already use owner-only permissions."),
  TARGET_IDENTITY_UNESTABLISHED(
      BookMaintenancePathFailure.TARGET_IDENTITY_UNESTABLISHED,
      "The FinGrind protected-book path could not establish a distinct final-target identity.",
      "The FinGrind book key file path could not establish a distinct final-target identity."),
  SOURCE_ARTIFACT_IDENTITY_DUPLICATED(
      BookMaintenancePathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
      "The FinGrind protected-book path duplicates another selected source artifact's physical identity.",
      "The FinGrind book key file path duplicates another selected source artifact's physical identity."),
  SOURCE_ARTIFACT_IDENTITY_CHANGED(
      BookMaintenancePathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
      "The FinGrind protected-book source changed after its physical identity was admitted for this maintenance workflow.",
      "The FinGrind book key file source changed after its physical identity was admitted for this maintenance workflow."),
  UNSUPPORTED_SECURE_FILESYSTEM(
      BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
      "The FinGrind protected-book path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs.",
      "The FinGrind book key file path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs."),
  ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED(
      BookMaintenancePathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomically creating owner-only FinGrind protocol files.",
      "The FinGrind book key file path must live on a filesystem that supports atomically creating owner-only FinGrind protocol files."),
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED(
      BookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic no-replace secret publication.",
      "The FinGrind book key file path must live on a filesystem that supports atomic no-replace secret publication."),
  ATOMIC_BOOK_PUBLICATION_UNSUPPORTED(
      BookMaintenancePathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic no-replace protected-book publication.",
      "The FinGrind book key file path must live on a filesystem that supports atomic no-replace protected-book publication."),
  ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED(
      BookMaintenancePathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic protected-book replacement.",
      "The FinGrind book key file path must live on a filesystem that supports atomic protected-book replacement.");

  private final BookMaintenancePathFailure publishedFailure;
  private final String bookFileMessage;
  private final String bookKeyFileMessage;

  ProtectedBookMaintenancePathFailure(
      BookMaintenancePathFailure publishedFailure,
      String bookFileMessage,
      String bookKeyFileMessage) {
    this.publishedFailure = publishedFailure;
    this.bookFileMessage = bookFileMessage;
    this.bookKeyFileMessage = bookKeyFileMessage;
  }

  /** Returns the exact stable public failure vocabulary for this local fact. */
  public BookMaintenancePathFailure publishedFailure() {
    return publishedFailure;
  }

  /** Returns the protected-book specific public diagnostic. */
  public String bookFileMessage() {
    return bookFileMessage;
  }

  /** Returns the generated book-key specific public diagnostic. */
  public String bookKeyFileMessage() {
    return bookKeyFileMessage;
  }
}
