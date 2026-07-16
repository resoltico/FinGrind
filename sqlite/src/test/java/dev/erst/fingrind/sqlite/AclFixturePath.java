package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Minimal path implementation for the test ACL filesystem. */
public final class AclFixturePath extends AclFixtureAbstractPath {
  public boolean exists;
  public boolean regularFile;
  public @Nullable AclFixtureView aclView;
  public @Nullable AclFileAttributeView overrideAclView;
  public Set<PosixFilePermission> posixPermissions = Set.of();
  private byte[] content = new byte[0];
  private @Nullable IOException deleteIfExistsFailure;
  private boolean preserveExistingEntryOnDeleteIfExists;
  private final Deque<PlannedIOException> newByteChannelFailures = new ArrayDeque<>();
  private final Deque<IOException> createDirectoryFailures = new ArrayDeque<>();
  private final Deque<PlannedIOException> writeFailures = new ArrayDeque<>();
  private @Nullable IOException newDirectoryStreamFailure;
  private @Nullable IOException directoryStreamCloseFailure;
  private final Deque<IOException> moveFailures = new ArrayDeque<>();

  AclFixturePath(AclFixtureFileSystem fileSystem, String value) {
    super(fileSystem, value);
    this.aclView = new AclFixtureView(fileSystem.owner);
  }

  public boolean existsValue() {
    return exists;
  }

  public boolean regularFileValue() {
    return regularFile;
  }

  public @Nullable AclFixtureView aclViewValue() {
    return aclView;
  }

  byte[] content() {
    return content.clone();
  }

  void replaceContent(byte[] replacement) {
    content = Objects.requireNonNull(replacement, "replacement").clone();
  }

  AclFixturePath failDeleteIfExistsWith(IOException exception) {
    deleteIfExistsFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException deleteIfExistsFailure() {
    return deleteIfExistsFailure;
  }

  AclFixturePath preserveExistingEntryOnDeleteIfExists() {
    preserveExistingEntryOnDeleteIfExists = true;
    return this;
  }

  boolean preserveExistingEntryOnDeleteIfExistsValue() {
    return preserveExistingEntryOnDeleteIfExists;
  }

  AclFixturePath failNewByteChannelWith(IOException exception) {
    return failNewByteChannelAfter(0, exception);
  }

  AclFixturePath failCreateDirectoryWith(IOException exception) {
    createDirectoryFailures.addLast(Objects.requireNonNull(exception, "exception"));
    return this;
  }

  @Nullable IOException createDirectoryFailure() {
    return createDirectoryFailures.pollFirst();
  }

  AclFixturePath failNewByteChannelAfter(int successfulCalls, IOException exception) {
    if (successfulCalls < 0) {
      throw new IllegalArgumentException("successfulCalls must be greater than or equal to zero.");
    }
    newByteChannelFailures.addLast(
        new PlannedIOException(successfulCalls, Objects.requireNonNull(exception, "exception")));
    return this;
  }

  @Nullable IOException newByteChannelFailure() {
    PlannedIOException plannedFailure = newByteChannelFailures.peekFirst();
    if (plannedFailure == null) {
      return null;
    }
    if (plannedFailure.successfulCallsBeforeFailure() > 0) {
      newByteChannelFailures.removeFirst();
      newByteChannelFailures.addFirst(plannedFailure.afterSuccessfulCall());
      return null;
    }
    newByteChannelFailures.removeFirst();
    return plannedFailure.exception();
  }

  AclFixturePath failWriteWith(IOException exception) {
    writeFailures.addLast(
        new PlannedIOException(0, Objects.requireNonNull(exception, "exception")));
    return this;
  }

  @Nullable IOException writeFailure() {
    PlannedIOException plannedFailure = writeFailures.peekFirst();
    if (plannedFailure == null) {
      return null;
    }
    if (plannedFailure.successfulCallsBeforeFailure() > 0) {
      writeFailures.removeFirst();
      writeFailures.addFirst(plannedFailure.afterSuccessfulCall());
      return null;
    }
    writeFailures.removeFirst();
    return plannedFailure.exception();
  }

  AclFixturePath failNewDirectoryStreamWith(IOException exception) {
    newDirectoryStreamFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException newDirectoryStreamFailure() {
    return newDirectoryStreamFailure;
  }

  AclFixturePath failDirectoryStreamCloseWith(IOException exception) {
    directoryStreamCloseFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException directoryStreamCloseFailure() {
    return directoryStreamCloseFailure;
  }

  AclFixturePath failMoveWith(IOException exception) {
    moveFailures.addLast(Objects.requireNonNull(exception, "exception"));
    return this;
  }

  @Nullable IOException moveFailure() {
    return moveFailures.pollFirst();
  }

  private record PlannedIOException(int successfulCallsBeforeFailure, IOException exception) {
    private PlannedIOException {
      if (successfulCallsBeforeFailure < 0) {
        throw new IllegalArgumentException(
            "successfulCallsBeforeFailure must be greater than or equal to zero.");
      }
      Objects.requireNonNull(exception, "exception");
    }

    private PlannedIOException afterSuccessfulCall() {
      return new PlannedIOException(successfulCallsBeforeFailure - 1, exception);
    }
  }
}
