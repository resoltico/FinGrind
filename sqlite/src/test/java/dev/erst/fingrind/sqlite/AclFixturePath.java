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
  private @Nullable IOException readAttributesFailure;
  private @Nullable IOException sameFileFailure;
  private @Nullable UnsupportedOperationException newFileChannelUnsupported;
  private @Nullable IOException tryLockFailure;
  private @Nullable IOException closeFailure;
  private @Nullable Long reportedSize;
  private boolean preserveExistingEntryOnDeleteIfExists;
  private final Deque<PlannedIOException> newByteChannelFailures = new ArrayDeque<>();
  private final Deque<IOException> createDirectoryFailures = new ArrayDeque<>();
  private final Deque<PlannedIOException> writeFailures = new ArrayDeque<>();
  private int zeroProgressReadCalls;
  private int zeroProgressWriteCalls;
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

  AclFixturePath failReadAttributesWith(IOException exception) {
    readAttributesFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException readAttributesFailure() {
    return readAttributesFailure;
  }

  AclFixturePath failSameFileWith(IOException exception) {
    sameFileFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException sameFileFailure() {
    return sameFileFailure;
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

  AclFixturePath failNewFileChannelWithUnsupportedOperation(
      UnsupportedOperationException exception) {
    newFileChannelUnsupported = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable UnsupportedOperationException newFileChannelUnsupported() {
    return newFileChannelUnsupported;
  }

  AclFixturePath failTryLockWith(IOException exception) {
    tryLockFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException tryLockFailure() {
    return tryLockFailure;
  }

  AclFixturePath failCloseWith(IOException exception) {
    closeFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException closeFailure() {
    return closeFailure;
  }

  AclFixturePath reportSizeAs(long size) {
    if (size < 0L) {
      throw new IllegalArgumentException("reported size must be non-negative");
    }
    reportedSize = size;
    return this;
  }

  @Nullable Long reportedSize() {
    return reportedSize;
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

  AclFixturePath returnZeroProgressFromNextWrite() {
    zeroProgressWriteCalls = Math.addExact(zeroProgressWriteCalls, 1);
    return this;
  }

  AclFixturePath returnZeroProgressFromNextRead() {
    zeroProgressReadCalls = Math.addExact(zeroProgressReadCalls, 1);
    return this;
  }

  boolean consumeZeroProgressRead() {
    if (zeroProgressReadCalls == 0) {
      return false;
    }
    zeroProgressReadCalls--;
    return true;
  }

  boolean consumeZeroProgressWrite() {
    if (zeroProgressWriteCalls == 0) {
      return false;
    }
    zeroProgressWriteCalls--;
    return true;
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
