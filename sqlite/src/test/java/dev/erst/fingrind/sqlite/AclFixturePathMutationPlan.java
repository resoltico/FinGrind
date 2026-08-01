package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Injected directory, metadata, and entry-mutation outcomes for the ACL fixture filesystem. */
final class AclFixturePathMutationPlan {
  private @Nullable IOException deleteIfExistsFailure;
  private @Nullable IOException readAttributesFailure;
  private @Nullable IOException posixReadAttributesFailure;
  private boolean preserveExistingEntryOnDeleteIfExists;
  private final Deque<IOException> createDirectoryFailures = new ArrayDeque<>();
  private @Nullable UnsupportedOperationException createDirectoryUnsupported;
  private final Deque<AclFixturePlannedIOException> newDirectoryStreamFailures = new ArrayDeque<>();
  private final Deque<AclFixturePlannedIOException> directoryStreamCloseFailures =
      new ArrayDeque<>();
  private final Deque<IOException> moveFailures = new ArrayDeque<>();

  void failDeleteIfExistsWith(IOException exception) {
    deleteIfExistsFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException deleteIfExistsFailure() {
    return deleteIfExistsFailure;
  }

  void failReadAttributesWith(IOException exception) {
    readAttributesFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException readAttributesFailure() {
    return readAttributesFailure;
  }

  void failPosixReadAttributesWith(IOException exception) {
    posixReadAttributesFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException posixReadAttributesFailure() {
    return posixReadAttributesFailure;
  }

  void preserveExistingEntryOnDeleteIfExists() {
    preserveExistingEntryOnDeleteIfExists = true;
  }

  boolean preservesExistingEntryOnDeleteIfExists() {
    return preserveExistingEntryOnDeleteIfExists;
  }

  void failCreateDirectoryWith(IOException exception) {
    createDirectoryFailures.addLast(Objects.requireNonNull(exception, "exception"));
  }

  void failCreateDirectoryWithUnsupportedOperation(UnsupportedOperationException exception) {
    createDirectoryUnsupported = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException createDirectoryFailure() {
    return createDirectoryFailures.pollFirst();
  }

  @Nullable UnsupportedOperationException createDirectoryUnsupported() {
    return createDirectoryUnsupported;
  }

  void failNewDirectoryStreamWith(IOException exception) {
    failNewDirectoryStreamAfterSuccessfulCallsWith(0, exception);
  }

  void failNewDirectoryStreamAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    newDirectoryStreamFailures.addLast(
        new AclFixturePlannedIOException(successfulCallsBeforeFailure, exception));
  }

  @Nullable IOException newDirectoryStreamFailure() {
    return AclFixturePlannedIOException.nextFailure(newDirectoryStreamFailures);
  }

  void failDirectoryStreamCloseWith(IOException exception) {
    failDirectoryStreamCloseAfterSuccessfulCallsWith(0, exception);
  }

  void failDirectoryStreamCloseAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    directoryStreamCloseFailures.addLast(
        new AclFixturePlannedIOException(successfulCallsBeforeFailure, exception));
  }

  @Nullable IOException directoryStreamCloseFailure() {
    return AclFixturePlannedIOException.nextFailure(directoryStreamCloseFailures);
  }

  void failMoveWith(IOException exception) {
    moveFailures.addLast(Objects.requireNonNull(exception, "exception"));
  }

  @Nullable IOException moveFailure() {
    return moveFailures.pollFirst();
  }
}
