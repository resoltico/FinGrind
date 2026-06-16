package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Provider backing the fake ACL filesystem used by platform-specific security tests. */
final class AclFixtureFileSystemProvider extends FileSystemProvider {
  private final AclFixtureFileSystem fileSystem;

  AclFixtureFileSystemProvider(AclFixtureFileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  @Override
  public String getScheme() {
    return "fingrind-test-acl";
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
    throw new FileSystemAlreadyExistsException(uri.toString());
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    if (!fileSystem.isOpen()) {
      throw new FileSystemNotFoundException(uri.toString());
    }
    return fileSystem;
  }

  @Override
  public Path getPath(URI uri) {
    return fileSystem.path(uri.getPath());
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return AclFixtureChannelOperations.newByteChannel(path, options, attrs);
  }

  @Override
  public FileChannel newFileChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return AclFixtureChannelOperations.newFileChannel(path, options, attrs);
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(
      Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
    return AclFixtureMutationOperations.newDirectoryStream(fileSystem, dir);
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    AclFixtureMutationOperations.createDirectory(dir, attrs);
  }

  @Override
  public void delete(Path path) throws IOException {
    if (!deleteIfExists(path)) {
      throw new NoSuchFileException(path.toString());
    }
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    return AclFixtureMutationOperations.deleteIfExists(path);
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    AclFixtureMutationOperations.copy(source, target, options);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    AclFixtureMutationOperations.move(source, target, options);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) {
    return java.util.Objects.equals(path, path2);
  }

  @Override
  public boolean isHidden(Path path) {
    return false;
  }

  @Override
  public FileStore getFileStore(Path path) {
    return new AclFixtureFileStore(fileSystem.supportedViews());
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    AclFixtureAttributeOperations.checkAccess(path, modes);
  }

  @Override
  public <V extends FileAttributeView> @Nullable V getFileAttributeView(
      Path path, Class<V> type, LinkOption... options) {
    return AclFixtureAttributeOperations.getFileAttributeView(path, type);
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(
      Path path, Class<A> type, LinkOption... options) throws IOException {
    return AclFixtureAttributeOperations.readAttributes(path, type);
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
      throws IOException {
    return AclFixtureAttributeOperations.readAttributes(path, attributes);
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
    throw new UnsupportedOperationException("setAttribute is not used by this test filesystem");
  }
}
