package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
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
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
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
import org.junit.jupiter.api.Test;

/** Tests for platform-specific key-file security branches. */
class SqliteBookKeyFileSecurityTest {
  @Test
  void aclFilesystemBranchesUseOwnerOnlyAclDescriptorsAndGeneration() throws Exception {
    try (FakeAclFileSystem fileSystem = FakeAclFileSystem.withViews(Set.of("acl"))) {
      FakeAclPath keyPath = fileSystem.path("\\keys\\acme.book-key");

      assertEquals(
          "owner-only-acl", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertEquals(1, keyPath.aclView.getAcl().size());
      assertEquals(fileSystem.owner, keyPath.aclView.getAcl().getFirst().principal());
      assertTrue(
          keyPath.aclView.getAcl().getFirst().permissions().contains(AclEntryPermission.READ_DATA));
      assertTrue(
          keyPath.aclView.getAcl().getFirst().permissions().contains(AclEntryPermission.DELETE));
    }
  }

  @Test
  void posixFilesystemBranchesUseOwnerOnlyDescriptorsAndGeneration() throws Exception {
    try (FakeAclFileSystem fileSystem = FakeAclFileSystem.withViews(Set.of("posix"))) {
      FakeAclPath keyPath = fileSystem.path("\\keys\\acme.book-key");

      assertEquals("0600", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          keyPath.posixPermissions);
    }
  }

  @Test
  void posixFilesystemRejectsOwnerUnreadableAndGroupReadableKeyFiles() {
    try (FakeAclFileSystem fileSystem = FakeAclFileSystem.withViews(Set.of("posix"))) {
      FakeAclPath ownerUnreadable = fileSystem.path("\\keys\\owner-unreadable.book-key");
      ownerUnreadable.exists = true;
      ownerUnreadable.regularFile = true;
      ownerUnreadable.posixPermissions = Set.of(PosixFilePermission.OWNER_WRITE);

      IllegalStateException ownerUnreadableException =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(ownerUnreadable));

      assertTrue(ownerUnreadableException.getMessage().contains("owner-readable"));

      FakeAclPath groupReadable = fileSystem.path("\\keys\\group-readable.book-key");
      groupReadable.exists = true;
      groupReadable.regularFile = true;
      groupReadable.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);

      IllegalStateException groupReadableException =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(groupReadable));

      assertTrue(groupReadableException.getMessage().contains("owner-only permissions"));
    }
  }

  @Test
  void unsupportedFilesystemBranchesRejectWithoutNativeSecurityViews() {
    try (FakeAclFileSystem fileSystem = FakeAclFileSystem.withViews(Set.of("basic"))) {
      FakeAclPath keyPath = fileSystem.path("\\keys\\unsupported.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;

      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath));
    }
  }

  @Test
  void aclFilesystemWithoutAclViewRejectsDuringInspection() {
    try (FakeAclFileSystem fileSystem = FakeAclFileSystem.withViews(Set.of("acl"))) {
      FakeAclPath keyPath = fileSystem.path("\\keys\\missing-view.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView = null;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath));

      assertTrue(exception.getMessage().contains("supports POSIX owner-only permissions"));
    }
  }

  /** Minimal ACL-capable filesystem for exercising platform-specific security code. */
  private static final class FakeAclFileSystem extends FileSystem {
    private final FakeAclFileSystemProvider provider;
    private final Set<String> views;
    private final UserPrincipal owner = new FakePrincipal("owner");
    private final GroupPrincipal group = new FakeGroup("group");
    private boolean open = true;

    private FakeAclFileSystem(Set<String> views) {
      this.views = Set.copyOf(views);
      this.provider = new FakeAclFileSystemProvider(this);
    }

    private static FakeAclFileSystem withViews(Set<String> views) {
      return new FakeAclFileSystem(views);
    }

    private FakeAclPath path(String value) {
      return new FakeAclPath(this, value);
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
      throw new UnsupportedOperationException(
          "principal lookup is not used by this test filesystem");
    }

    @Override
    public WatchService newWatchService() {
      throw new UnsupportedOperationException("watch service is not used by this test filesystem");
    }
  }

  /** Minimal provider backing the fake ACL filesystem. */
  private static final class FakeAclFileSystemProvider extends FileSystemProvider {
    private final FakeAclFileSystem fileSystem;

    private FakeAclFileSystemProvider(FakeAclFileSystem fileSystem) {
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
      FakeAclPath fakePath = fakePath(path);
      if (options.contains(StandardOpenOption.CREATE_NEW) && fakePath.exists) {
        throw new FileAlreadyExistsException(fakePath.toString());
      }
      fakePath.exists = true;
      fakePath.regularFile = true;
      fakePath.posixPermissions = findPosixPermissions(attrs);
      return new FakeSeekableByteChannel();
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
      FakeAclPath fakePath = fakePath(dir);
      fakePath.exists = true;
      fakePath.regularFile = false;
      fakePath.posixPermissions = findPosixPermissions(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
      FakeAclPath fakePath = fakePath(path);
      if (!fakePath.exists) {
        throw new NoSuchFileException(fakePath.toString());
      }
      fakePath.exists = false;
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
      if (!fakePath(path).exists) {
        throw new NoSuchFileException(path.toString());
      }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(
        Path path, Class<V> type, LinkOption... options) {
      FakeAclPath fakePath = fakePath(path);
      if (type == AclFileAttributeView.class) {
        return type.cast(fakePath.aclView);
      }
      if (type == FileOwnerAttributeView.class) {
        return type.cast(fakePath.aclView);
      }
      if (type == PosixFileAttributeView.class) {
        return type.cast(new FakePosixView(fakePath));
      }
      return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(
        Path path, Class<A> type, LinkOption... options) {
      if (type == BasicFileAttributes.class) {
        return type.cast(new FakeBasicFileAttributes(fakePath(path)));
      }
      if (type == PosixFileAttributes.class) {
        return type.cast(new FakePosixFileAttributes(fakePath(path)));
      }
      throw new UnsupportedOperationException(
          "only basic and POSIX attributes are used by these tests");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
      FakeAclPath fakePath = fakePath(path);
      return Map.of("isRegularFile", fakePath.regularFile, "isDirectory", !fakePath.regularFile);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
      throw new UnsupportedOperationException("setAttribute is not used by this test filesystem");
    }

    private FakeAclPath fakePath(Path path) {
      return (FakeAclPath) path;
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
  }

  /** Minimal path implementation for the fake ACL filesystem. */
  private static final class FakeAclPath implements Path {
    private final FakeAclFileSystem fileSystem;
    private final String value;
    private boolean exists;
    private boolean regularFile;
    private FakeAclView aclView;
    private Set<PosixFilePermission> posixPermissions = Set.of();

    private FakeAclPath(FakeAclFileSystem fileSystem, String value) {
      this.fileSystem = fileSystem;
      this.value = value;
      this.aclView = new FakeAclView(fileSystem.owner);
    }

    @Override
    public FakeAclFileSystem getFileSystem() {
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
    public Path getParent() {
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
      return other instanceof FakeAclPath path
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

  /** Minimal ACL view for exercising owner-only ACL writes and reads. */
  private static final class FakeAclView implements AclFileAttributeView {
    private UserPrincipal owner;
    private List<AclEntry> acl = List.of();

    private FakeAclView(UserPrincipal owner) {
      this.owner = owner;
    }

    @Override
    public String name() {
      return "acl";
    }

    @Override
    public List<AclEntry> getAcl() {
      return acl;
    }

    @Override
    public void setAcl(List<AclEntry> acl) {
      this.acl = List.copyOf(acl);
    }

    @Override
    public UserPrincipal getOwner() {
      return owner;
    }

    @Override
    public void setOwner(UserPrincipal owner) {
      this.owner = Objects.requireNonNull(owner, "owner");
    }
  }

  /** Minimal POSIX view for exercising owner-only mode writes and reads on every host OS. */
  private static final class FakePosixView implements PosixFileAttributeView {
    private final FakeAclPath path;

    private FakePosixView(FakeAclPath path) {
      this.path = path;
    }

    @Override
    public String name() {
      return "posix";
    }

    @Override
    public PosixFileAttributes readAttributes() {
      return new FakePosixFileAttributes(path);
    }

    @Override
    public void setPermissions(Set<PosixFilePermission> permissions) {
      path.posixPermissions = Set.copyOf(permissions);
    }

    @Override
    public void setGroup(GroupPrincipal group) {}

    @Override
    public UserPrincipal getOwner() {
      return path.fileSystem.owner;
    }

    @Override
    public void setOwner(UserPrincipal owner) {}

    @Override
    public void setTimes(
        java.nio.file.attribute.FileTime lastModifiedTime,
        java.nio.file.attribute.FileTime lastAccessTime,
        java.nio.file.attribute.FileTime createTime) {}
  }

  /** Minimal basic attributes for the fake ACL filesystem. */
  private record FakeBasicFileAttributes(FakeAclPath path) implements BasicFileAttributes {
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
      return path.regularFile;
    }

    @Override
    public boolean isDirectory() {
      return !path.regularFile;
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
    public Object fileKey() {
      return path;
    }
  }

  /** Minimal POSIX attributes for Files.getPosixFilePermissions on the fake filesystem. */
  private record FakePosixFileAttributes(FakeAclPath path) implements PosixFileAttributes {
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
      return path.regularFile;
    }

    @Override
    public boolean isDirectory() {
      return !path.regularFile;
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
    public Object fileKey() {
      return path;
    }

    @Override
    public UserPrincipal owner() {
      return path.fileSystem.owner;
    }

    @Override
    public GroupPrincipal group() {
      return path.fileSystem.group;
    }

    @Override
    public Set<PosixFilePermission> permissions() {
      return path.posixPermissions;
    }
  }

  /** Minimal writable channel for Files.createFile on the fake ACL filesystem. */
  private static final class FakeSeekableByteChannel implements SeekableByteChannel {
    private boolean open = true;

    @Override
    public int read(ByteBuffer dst) {
      return -1;
    }

    @Override
    public int write(ByteBuffer src) {
      int remaining = src.remaining();
      src.position(src.limit());
      return remaining;
    }

    @Override
    public long position() {
      return 0;
    }

    @Override
    public SeekableByteChannel position(long newPosition) {
      return this;
    }

    @Override
    public long size() {
      return 0;
    }

    @Override
    public SeekableByteChannel truncate(long size) {
      return this;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() {
      open = false;
    }
  }

  /** User principal identified solely by name. */
  private record FakePrincipal(String name) implements UserPrincipal {
    @Override
    public String getName() {
      return name;
    }
  }

  /** Group principal identified solely by name. */
  private record FakeGroup(String name) implements GroupPrincipal {
    @Override
    public String getName() {
      return name;
    }
  }
}
