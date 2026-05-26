package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Minimal path implementation for the test ACL filesystem. */
public final class AclFixturePath implements Path {
  private final AclFixtureFileSystem fileSystem;
  private final String value;
  public boolean exists;
  public boolean regularFile;
  public @Nullable AclFixtureView aclView;
  public @Nullable AclFileAttributeView overrideAclView;
  public Set<PosixFilePermission> posixPermissions = Set.of();
  private @Nullable IOException deleteIfExistsFailure;
  private boolean preserveExistingEntryOnDeleteIfExists;
  private final Deque<PlannedIOException> newByteChannelFailures = new ArrayDeque<>();
  private final Deque<PlannedIOException> writeFailures = new ArrayDeque<>();
  private @Nullable IOException newDirectoryStreamFailure;
  private @Nullable IOException directoryStreamCloseFailure;
  private final Deque<IOException> moveFailures = new ArrayDeque<>();

  AclFixturePath(AclFixtureFileSystem fileSystem, String value) {
    this.fileSystem = fileSystem;
    this.value = value;
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

  @Override
  public AclFixtureFileSystem getFileSystem() {
    return fileSystem;
  }

  @Override
  public boolean isAbsolute() {
    return value.startsWith("\\");
  }

  @Override
  public Path getRoot() {
    return fileSystem.path("\\");
  }

  @Override
  public Path getFileName() {
    int index = value.lastIndexOf('\\');
    return fileSystem.path(index < 0 ? value : value.substring(index + 1));
  }

  @Override
  public @Nullable Path getParent() {
    int index = value.lastIndexOf('\\');
    if (index <= 0) {
      return null;
    }
    return fileSystem.path(value.substring(0, index));
  }

  @Override
  public int getNameCount() {
    return names().size();
  }

  @Override
  public Path getName(int index) {
    return fileSystem.path(names().get(index));
  }

  @Override
  public Path subpath(int beginIndex, int endIndex) {
    return fileSystem.path(String.join("\\", names().subList(beginIndex, endIndex)));
  }

  @Override
  public boolean startsWith(Path other) {
    return value.startsWith(other.toString());
  }

  @Override
  public boolean startsWith(String other) {
    return value.startsWith(other);
  }

  @Override
  public boolean endsWith(Path other) {
    return value.endsWith(other.toString());
  }

  @Override
  public boolean endsWith(String other) {
    return value.endsWith(other);
  }

  @Override
  public Path normalize() {
    return this;
  }

  @Override
  public Path resolve(Path other) {
    return fileSystem.path(value + "\\" + other);
  }

  @Override
  public Path resolve(String other) {
    return fileSystem.path(value + "\\" + other);
  }

  @Override
  public Path resolveSibling(Path other) {
    Path parent = getParent();
    return parent == null ? other : parent.resolve(other);
  }

  @Override
  public Path resolveSibling(String other) {
    return resolveSibling(fileSystem.path(other));
  }

  @Override
  public Path relativize(Path other) {
    throw new UnsupportedOperationException("relativize is not used by this test filesystem");
  }

  @Override
  public URI toUri() {
    return URI.create(fileSystem.provider().getScheme() + ":" + value.replace('\\', '/'));
  }

  @Override
  public Path toAbsolutePath() {
    return isAbsolute() ? this : fileSystem.path("\\" + value);
  }

  @Override
  public Path toRealPath(LinkOption... options) {
    return toAbsolutePath();
  }

  @Override
  public java.io.File toFile() {
    throw new UnsupportedOperationException("toFile is not used by this test filesystem");
  }

  @Override
  public WatchKey register(
      WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
    throw new UnsupportedOperationException(
        "watch registration is not used by this test filesystem");
  }

  @Override
  public Iterator<Path> iterator() {
    return names().stream().<Path>map(fileSystem::path).iterator();
  }

  @Override
  public int compareTo(Path other) {
    return value.compareTo(other.toString());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AclFixturePath path
        && fileSystem == path.fileSystem
        && value.equals(path.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(System.identityHashCode(fileSystem), value);
  }

  @Override
  public String toString() {
    return value;
  }

  private List<String> names() {
    return List.of(value.replaceFirst("^\\\\", "").split("\\\\"));
  }
}
