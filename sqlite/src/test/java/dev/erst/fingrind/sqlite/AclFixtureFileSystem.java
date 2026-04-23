package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;

/** Minimal ACL-capable filesystem for exercising platform-specific security code. */
@NullUnmarked
final class AclFixtureFileSystem extends FileSystem {
  private final FileSystemProvider provider;
  private final Set<String> views;
  final UserPrincipal owner = new AclFixturePrincipal("owner");
  final GroupPrincipal group = new AclFixtureGroup("group");
  private boolean open = true;

  private AclFixtureFileSystem(Set<String> views) {
    this.views = Set.copyOf(views);
    this.provider = new Provider(this);
  }

  static AclFixtureFileSystem withViews(Set<String> views) {
    return new AclFixtureFileSystem(views);
  }

  AclFixturePath path(String value) {
    return new AclFixturePath(this, value);
  }

  @Override
  public FileSystemProvider provider() {
    return provider;
  }

  @Override
  public void close() {
    open = false;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getSeparator() {
    return "\\";
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return List.of(path("\\"));
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return List.of();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return views;
  }

  @Override
  public Path getPath(String first, String... more) {
    StringBuilder joined = new StringBuilder(first);
    for (String part : more) {
      joined.append(getSeparator()).append(part);
    }
    return path(joined.toString());
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    throw new UnsupportedOperationException("path matching is not used by this test filesystem");
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException("principal lookup is not used by this test filesystem");
  }

  @Override
  public WatchService newWatchService() {
    throw new UnsupportedOperationException("watch service is not used by this test filesystem");
  }

  /** Minimal provider backing the fake ACL filesystem. */
  private static final class Provider extends FileSystemProvider {
    private final AclFixtureFileSystem fileSystem;

    private Provider(AclFixtureFileSystem fileSystem) {
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
        Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
        throws IOException {
      AclFixturePath testPath = testPath(path);
      if (options.contains(StandardOpenOption.CREATE_NEW) && testPath.exists) {
        throw new FileAlreadyExistsException(testPath.toString());
      }
      testPath.exists = true;
      testPath.regularFile = true;
      testPath.posixPermissions = findPosixPermissions(attrs);
      return new AclFixtureSeekableByteChannel();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path dir, DirectoryStream.Filter<? super Path> filter) {
      return new DirectoryStream<>() {
        @Override
        public Iterator<Path> iterator() {
          return Collections.emptyIterator();
        }

        @Override
        public void close() {}
      };
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) {
      AclFixturePath testPath = testPath(dir);
      testPath.exists = true;
      testPath.regularFile = false;
      testPath.posixPermissions = findPosixPermissions(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
      AclFixturePath testPath = testPath(path);
      if (!testPath.exists) {
        throw new NoSuchFileException(testPath.toString());
      }
      testPath.exists = false;
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {
      throw new UnsupportedOperationException("copy is not used by this test filesystem");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) {
      throw new UnsupportedOperationException("move is not used by this test filesystem");
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
      throw new UnsupportedOperationException("file stores are not used by this test filesystem");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
      if (!testPath(path).exists) {
        throw new NoSuchFileException(path.toString());
      }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(
        Path path, Class<V> type, LinkOption... options) {
      AclFixturePath fixturePath = testPath(path);
      if (type == AclFileAttributeView.class) {
        return type.cast(fixturePath.aclView);
      }
      if (type == FileOwnerAttributeView.class) {
        return type.cast(fixturePath.aclView);
      }
      if (type == PosixFileAttributeView.class) {
        return type.cast(new AclFixturePosixView(fixturePath));
      }
      return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path, Class<A> type, LinkOption... options) {
      if (type == BasicFileAttributes.class) {
        return type.cast(new AclFixtureBasicFileAttributes(testPath(path)));
      }
      if (type == PosixFileAttributes.class) {
        return type.cast(new AclFixturePosixFileAttributes(testPath(path)));
      }
      throw new UnsupportedOperationException(
          "only basic and POSIX attributes are used by these tests");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
      AclFixturePath fixturePath = testPath(path);
      return Map.of(
          "isRegularFile", fixturePath.regularFile, "isDirectory", !fixturePath.regularFile);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
      throw new UnsupportedOperationException("setAttribute is not used by this test filesystem");
    }

    private static Set<PosixFilePermission> findPosixPermissions(FileAttribute<?>... attrs) {
      for (FileAttribute<?> attribute : attrs) {
        if ("posix:permissions".equals(attribute.name())) {
          Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
          for (Object permission : (Set<?>) attribute.value()) {
            permissions.add((PosixFilePermission) permission);
          }
          return Set.copyOf(permissions);
        }
      }
      return Set.of();
    }

    private static AclFixturePath testPath(Path path) {
      return (AclFixturePath) path;
    }
  }
}
