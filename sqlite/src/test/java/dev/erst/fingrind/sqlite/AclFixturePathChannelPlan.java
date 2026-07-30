package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Injected channel and in-memory-content outcomes for the ACL fixture filesystem. */
final class AclFixturePathChannelPlan {
  private byte[] content = new byte[0];
  private @Nullable UnsupportedOperationException newFileChannelUnsupported;
  private @Nullable UnsupportedOperationException newByteChannelUnsupported;
  private @Nullable IOException tryLockFailure;
  private @Nullable IOException closeFailure;
  private @Nullable Long reportedSize;
  private final Deque<AclFixturePlannedIOException> newByteChannelFailures = new ArrayDeque<>();
  private final Deque<AclFixturePlannedIOException> writeFailures = new ArrayDeque<>();
  private int zeroProgressReadCalls;
  private int zeroProgressWriteCalls;

  byte[] content() {
    return content.clone();
  }

  void replaceContent(byte[] replacement) {
    content = Objects.requireNonNull(replacement, "replacement").clone();
  }

  void failNewByteChannelWith(IOException exception) {
    failNewByteChannelAfter(0, exception);
  }

  void failNewFileChannelWithUnsupportedOperation(UnsupportedOperationException exception) {
    newFileChannelUnsupported = Objects.requireNonNull(exception, "exception");
  }

  @Nullable UnsupportedOperationException newFileChannelUnsupported() {
    return newFileChannelUnsupported;
  }

  void failNewByteChannelWithUnsupportedOperation(UnsupportedOperationException exception) {
    newByteChannelUnsupported = Objects.requireNonNull(exception, "exception");
  }

  @Nullable UnsupportedOperationException newByteChannelUnsupported() {
    return newByteChannelUnsupported;
  }

  void failTryLockWith(IOException exception) {
    tryLockFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException tryLockFailure() {
    return tryLockFailure;
  }

  void failCloseWith(IOException exception) {
    closeFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException closeFailure() {
    return closeFailure;
  }

  void reportSizeAs(long size) {
    if (size < 0L) {
      throw new IllegalArgumentException("reported size must be non-negative");
    }
    reportedSize = size;
  }

  @Nullable Long reportedSize() {
    return reportedSize;
  }

  void failNewByteChannelAfter(int successfulCalls, IOException exception) {
    if (successfulCalls < 0) {
      throw new IllegalArgumentException("successfulCalls must be greater than or equal to zero.");
    }
    newByteChannelFailures.addLast(new AclFixturePlannedIOException(successfulCalls, exception));
  }

  @Nullable IOException newByteChannelFailure() {
    return AclFixturePlannedIOException.nextFailure(newByteChannelFailures);
  }

  void failWriteWith(IOException exception) {
    writeFailures.addLast(new AclFixturePlannedIOException(0, exception));
  }

  @Nullable IOException writeFailure() {
    return AclFixturePlannedIOException.nextFailure(writeFailures);
  }

  void returnZeroProgressFromNextWrite() {
    zeroProgressWriteCalls = Math.addExact(zeroProgressWriteCalls, 1);
  }

  void returnZeroProgressFromNextRead() {
    zeroProgressReadCalls = Math.addExact(zeroProgressReadCalls, 1);
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
}
