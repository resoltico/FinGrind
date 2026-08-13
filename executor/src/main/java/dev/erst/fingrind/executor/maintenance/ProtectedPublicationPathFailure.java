package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;

/** Local maintenance failure facts with their published vocabulary and caller-path language. */
public enum ProtectedPublicationPathFailure {
  MISSING_PARENT_DIRECTORY(
      PublicationPathFailure.MISSING_PARENT_DIRECTORY,
      "The FinGrind protected-book path requires a parent directory.",
      "The FinGrind book key file path requires a parent directory."),
  PARENT_PATH_COLLISION(
      PublicationPathFailure.PARENT_PATH_COLLISION,
      "The FinGrind protected-book path cannot use a parent path that already exists as a non-directory entry or symlink.",
      "The FinGrind book key file path cannot use a parent path that already exists as a non-directory entry or symlink."),
  PARENT_OWNER_ACCESS_REQUIRED(
      PublicationPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
      "The FinGrind protected-book path requires a parent directory that the owner can traverse and write.",
      "The FinGrind book key file path requires a parent directory that the owner can traverse and write."),
  PARENT_OWNER_ONLY_REQUIRED(
      PublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED,
      "The FinGrind protected-book path requires an owner-only parent directory.",
      "The FinGrind book key file path requires an owner-only parent directory."),
  ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE(
      PublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
      "The FinGrind protected-book path must resolve to a regular non-symlink file.",
      "The FinGrind book key file path must resolve to a regular non-symlink file."),
  TARGET_OWNER_ONLY_REQUIRED(
      PublicationPathFailure.TARGET_OWNER_ONLY_REQUIRED,
      "The FinGrind protected-book path must already use owner-only permissions.",
      "The FinGrind book key file path must already use owner-only permissions."),
  TARGET_IDENTITY_UNESTABLISHED(
      PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED,
      "The FinGrind protected-book path could not establish a distinct final-target identity.",
      "The FinGrind book key file path could not establish a distinct final-target identity."),
  SOURCE_ARTIFACT_IDENTITY_DUPLICATED(
      PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
      "The FinGrind protected-book path duplicates another selected source artifact's physical identity.",
      "The FinGrind book key file path duplicates another selected source artifact's physical identity."),
  SOURCE_ARTIFACT_IDENTITY_CHANGED(
      PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
      "The FinGrind protected-book source changed after its physical identity was admitted for this maintenance workflow.",
      "The FinGrind book key file source changed after its physical identity was admitted for this maintenance workflow."),
  UNSUPPORTED_SECURE_FILESYSTEM(
      PublicationPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
      "The FinGrind protected-book path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs.",
      "The FinGrind book key file path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs."),
  ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED(
      PublicationPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomically creating owner-only FinGrind protocol files.",
      "The FinGrind book key file path must live on a filesystem that supports atomically creating owner-only FinGrind protocol files."),
  ATOMIC_SECRET_PUBLICATION_UNSUPPORTED(
      PublicationPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic no-replace secret publication.",
      "The FinGrind book key file path must live on a filesystem that supports atomic no-replace secret publication."),
  ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED(
      PublicationPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic no-replace protected-book publication.",
      "The FinGrind book key file path must live on a filesystem that supports atomic no-replace protected-book publication."),
  ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED(
      PublicationPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED,
      "The FinGrind protected-book path must live on a filesystem that supports atomic protected-book replacement.",
      "The FinGrind book key file path must live on a filesystem that supports atomic protected-book replacement.");

  private final PublicationPathFailure publishedFailure;
  private final String bookFileMessage;
  private final String bookKeyFileMessage;

  ProtectedPublicationPathFailure(
      PublicationPathFailure publishedFailure, String bookFileMessage, String bookKeyFileMessage) {
    this.publishedFailure = publishedFailure;
    this.bookFileMessage = bookFileMessage;
    this.bookKeyFileMessage = bookKeyFileMessage;
  }

  /** Returns the exact stable public failure vocabulary for this local fact. */
  public PublicationPathFailure publishedFailure() {
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
