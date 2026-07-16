package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Coverage for administrative preflight branches that wrap tighten-parent I/O failures. */
class CliAdministrativeCommandExecutorCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void administrativeExecutors_wrapTightenParentIoFailuresWithoutLeakingPaths() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliWorkflowDoubleSupport.ExplodingWorkflow workflow =
        new CliWorkflowDoubleSupport.ExplodingWorkflow(
            new IllegalStateException("workflow should not run"));
    CliAdministrativeCommandExecutor executor =
        new CliAdministrativeCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            failureWriter(outputStream),
            workflow,
            workflow);
    try (ThrowingPosixFileSystem fileSystem = new ThrowingPosixFileSystem()) {
      Path bookKeyFilePath = fileSystem.getPath("/secure/entity.book-key");
      IllegalStateException keyFailure =
          assertThrows(
              IllegalStateException.class,
              () -> executor.runGenerateBookKeyFileCommand(bookKeyFilePath, true, OutputMode.TEXT));
      String keyMessage = Objects.requireNonNull(keyFailure.getMessage());
      assertTrue(
          keyMessage.contains("Failed to tighten the existing book-key parent directory"),
          keyMessage);
      assertFalse(keyMessage.contains("/secure/entity.book-key"), keyMessage);
      assertEquals("boom", assertInstanceOf(IOException.class, keyFailure.getCause()).getMessage());

      Path bookFilePath = fileSystem.getPath("/books/entity.sqlite");
      BookAccess bookAccess =
          new BookAccess(
              bookFilePath,
              new BookAccess.PassphraseSource.KeyFile(Path.of("keys/entity.book-key")));
      IllegalStateException bookFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  executor.runOpenBookCommand(
                      bookAccess, openBookCommand(), true, OutputMode.TEXT));
      String bookMessage = Objects.requireNonNull(bookFailure.getMessage());
      assertTrue(
          bookMessage.contains("Failed to tighten the existing book parent directory"),
          bookMessage);
      assertFalse(bookMessage.contains("/books/entity.sqlite"), bookMessage);
      assertEquals(
          "boom", assertInstanceOf(IOException.class, bookFailure.getCause()).getMessage());
    }
  }

  /** Minimal owner-controlled POSIX filesystem double for deterministic tighten-parent failures. */
  private static final class ThrowingPosixFileSystem extends FileSystem {
    private final ThrowingPosixFileSystemProvider provider =
        new ThrowingPosixFileSystemProvider(this);

    @Override
    public FileSystemProvider provider() {
      return provider;
    }

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public String getSeparator() {
      return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
      return List.of(getPath("/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
      return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
      return Set.of("posix");
    }

    @Override
    public Path getPath(String first, String... more) {
      StringBuilder joined = new StringBuilder(first);
      for (String part : more) {
        if (!joined.isEmpty() && joined.charAt(joined.length() - 1) != '/') {
          joined.append('/');
        }
        joined.append(part);
      }
      return new ThrowingPosixPath(this, joined.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
      throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
      throw new UnsupportedOperationException();
    }
  }

  /** FileSystemProvider double that makes POSIX attribute reads fail with a checked I/O error. */
  private static final class ThrowingPosixFileSystemProvider extends FileSystemProvider {
    private final ThrowingPosixFileSystem fileSystem;

    private ThrowingPosixFileSystemProvider(ThrowingPosixFileSystem fileSystem) {
      this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    @Override
    public String getScheme() {
      return "throw-posix";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
      if ("throw-posix".equals(uri.getScheme())) {
        return fileSystem;
      }
      throw new FileSystemNotFoundException(uri.toString());
    }

    @Override
    public Path getPath(URI uri) {
      return fileSystem.getPath(uri.getPath());
    }

    @Override
    public SeekableByteChannel newByteChannel(
        Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path dir, DirectoryStream.Filter<? super Path> filter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Path path) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
      return Objects.equals(path, path2);
    }

    @Override
    public boolean isHidden(Path path) {
      return false;
    }

    @Override
    public FileStore getFileStore(Path path) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) {}

    @Override
    public <V extends FileAttributeView> @org.jspecify.annotations.Nullable V getFileAttributeView(
        Path path, Class<V> type, LinkOption... options) {
      if (type == PosixFileAttributeView.class) {
        return type.cast(new ThrowingPosixFileAttributeView());
      }
      return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path, Class<A> type, LinkOption... options) throws IOException {
      if (type == BasicFileAttributes.class) {
        return type.cast(new DirectoryAttributes());
      }
      if (type == PosixFileAttributes.class) {
        throw new IOException("boom");
      }
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
      throw new UnsupportedOperationException();
    }
  }

  /** POSIX attribute view double that always fails attribute reads. */
  private static final class ThrowingPosixFileAttributeView implements PosixFileAttributeView {
    @Override
    public String name() {
      return "posix";
    }

    @Override
    public PosixFileAttributes readAttributes() throws IOException {
      throw new IOException("boom");
    }

    @Override
    public void setPermissions(Set<PosixFilePermission> perms) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipal getOwner() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setOwner(UserPrincipal owner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGroup(GroupPrincipal group) {
      throw new UnsupportedOperationException();
    }
  }

  /** Basic directory attributes for parent-path existence and directory checks. */
  private static final class DirectoryAttributes implements BasicFileAttributes {
    @Override
    public java.nio.file.attribute.FileTime lastModifiedTime() {
      return java.nio.file.attribute.FileTime.fromMillis(0);
    }

    @Override
    public java.nio.file.attribute.FileTime lastAccessTime() {
      return java.nio.file.attribute.FileTime.fromMillis(0);
    }

    @Override
    public java.nio.file.attribute.FileTime creationTime() {
      return java.nio.file.attribute.FileTime.fromMillis(0);
    }

    @Override
    public boolean isRegularFile() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return true;
    }

    @Override
    public boolean isSymbolicLink() {
      return false;
    }

    @Override
    public boolean isOther() {
      return false;
    }

    @Override
    public long size() {
      return 0;
    }

    @Override
    public @org.jspecify.annotations.Nullable Object fileKey() {
      return null;
    }
  }

  /** Minimal absolute-path implementation paired with the deterministic POSIX filesystem double. */
  private static final class ThrowingPosixPath implements Path {
    private final ThrowingPosixFileSystem fileSystem;
    private final String value;

    private ThrowingPosixPath(ThrowingPosixFileSystem fileSystem, String value) {
      this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
      this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public ThrowingPosixFileSystem getFileSystem() {
      return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
      return value.startsWith("/");
    }

    @Override
    public @org.jspecify.annotations.Nullable Path getRoot() {
      return isAbsolute() ? fileSystem.getPath("/") : null;
    }

    @Override
    public Path getFileName() {
      int index = value.lastIndexOf('/');
      return fileSystem.getPath(index < 0 ? value : value.substring(index + 1));
    }

    @Override
    public @org.jspecify.annotations.Nullable Path getParent() {
      int index = value.lastIndexOf('/');
      if (index <= 0) {
        return null;
      }
      return fileSystem.getPath(value.substring(0, index));
    }

    @Override
    public int getNameCount() {
      return names().size();
    }

    @Override
    public Path getName(int index) {
      return fileSystem.getPath(names().get(index));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
      return fileSystem.getPath(String.join("/", names().subList(beginIndex, endIndex)));
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
      return resolve(other.toString());
    }

    @Override
    public Path resolve(String other) {
      if (other.startsWith("/")) {
        return fileSystem.getPath(other);
      }
      return fileSystem.getPath(value + "/" + other);
    }

    @Override
    public Path resolveSibling(Path other) {
      Path parent = getParent();
      return parent == null ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
      return resolveSibling(fileSystem.getPath(other));
    }

    @Override
    public Path relativize(Path other) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URI toUri() {
      return URI.create(fileSystem.provider().getScheme() + ":" + toAbsolutePath());
    }

    @Override
    public Path toAbsolutePath() {
      return isAbsolute() ? this : fileSystem.getPath("/" + value);
    }

    @Override
    public Path toRealPath(LinkOption... options) {
      return toAbsolutePath();
    }

    @Override
    public java.io.File toFile() {
      throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(
        WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
      return names().stream().<Path>map(fileSystem::getPath).iterator();
    }

    @Override
    public int compareTo(Path other) {
      return value.compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ThrowingPosixPath path
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
      String normalized = value.replaceFirst("^/+", "");
      if (normalized.isEmpty()) {
        return List.of();
      }
      return List.of(normalized.split("/"));
    }
  }
}
