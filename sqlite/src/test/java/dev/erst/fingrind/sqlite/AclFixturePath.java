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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Minimal path implementation for the test ACL filesystem. */
final class AclFixturePath implements Path {
  private final AclFixtureFileSystem fileSystem;
  private final String value;
  boolean exists;
  boolean regularFile;
  @Nullable AclFixtureView aclView;
  @Nullable AclFileAttributeView overrideAclView;
  Set<PosixFilePermission> posixPermissions = Set.of();
  private @Nullable IOException deleteIfExistsFailure;
  private @Nullable IOException newByteChannelFailure;
  private @Nullable IOException newDirectoryStreamFailure;

  AclFixturePath(AclFixtureFileSystem fileSystem, String value) {
    this.fileSystem = fileSystem;
    this.value = value;
    this.aclView = new AclFixtureView(fileSystem.owner);
  }

  AclFixturePath failDeleteIfExistsWith(IOException exception) {
    deleteIfExistsFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException deleteIfExistsFailure() {
    return deleteIfExistsFailure;
  }

  AclFixturePath failNewByteChannelWith(IOException exception) {
    newByteChannelFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException newByteChannelFailure() {
    return newByteChannelFailure;
  }

  AclFixturePath failNewDirectoryStreamWith(IOException exception) {
    newDirectoryStreamFailure = Objects.requireNonNull(exception, "exception");
    return this;
  }

  @Nullable IOException newDirectoryStreamFailure() {
    return newDirectoryStreamFailure;
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
