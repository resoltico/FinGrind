package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** In-memory nofollow filesystem facts for private-output-directory boundary tests. */
final class PrivateOutputDirectoryTestFilesystem {
  private PrivateOutputDirectoryTestFilesystem() {}

  /** Creates mutable in-memory filesystem facts for a single test owner. */
  static FakeFilesystemAccess fakeFilesystemAccess(UserPrincipal owner) {
    return new FakeFilesystemAccess(owner);
  }

  /** In-memory implementation exposing nofollow filesystem facts to boundary tests. */
  static final class FakeFilesystemAccess implements PrivateOutputDirectory.FilesystemAccess {
    private final UserPrincipal owner;
    private final Map<Path, PrivateOutputDirectory.NoFollowEntryKind> entryKinds =
        new ConcurrentHashMap<>();
    private final Map<Path, Set<PosixFilePermission>> posixPermissions = new ConcurrentHashMap<>();
    private final Map<Path, PrivateOutputDirectory.PosixDirectoryIdentity> posixIdentities =
        new ConcurrentHashMap<>();
    private final Map<Path, PrivateOutputDirectory.AclState> acls = new ConcurrentHashMap<>();
    private @Nullable IOException realPathIoFailure;
    private @Nullable RuntimeException realPathRuntimeFailure;

    FakeFilesystemAccess(UserPrincipal owner) {
      this.owner = Objects.requireNonNull(owner, "owner");
    }

    void putPosix(Path path, Set<PosixFilePermission> permissions) {
      markDirectory(path);
      posixPermissions.put(path, permissions);
      posixIdentities.putIfAbsent(
          path, new PrivateOutputDirectory.PosixDirectoryIdentity(owner, 1_000L, false));
    }

    void putPosixIdentity(Path path, UserPrincipal owner, long unixUserId, boolean sticky) {
      markDirectory(path);
      posixIdentities.put(
          path, new PrivateOutputDirectory.PosixDirectoryIdentity(owner, unixUserId, sticky));
    }

    void enableAcl(Path path) {
      markDirectory(path);
      acls.put(
          path,
          new PrivateOutputDirectory.AclState(
              owner,
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(owner)
                      .setPermissions(AclEntryPermission.values())
                      .build())));
    }

    void putAcl(Path path, PrivateOutputDirectory.AclState aclState) {
      markDirectory(path);
      acls.put(path, aclState);
    }

    void markDirectory(Path path) {
      entryKinds.put(path, PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY);
    }

    void markOther(Path path) {
      entryKinds.put(path, PrivateOutputDirectory.NoFollowEntryKind.OTHER);
      posixPermissions.remove(path);
      posixIdentities.remove(path);
      acls.remove(path);
    }

    void removeDirectory(Path path) {
      if (entryKinds.remove(path, PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY)) {
        posixPermissions.remove(path);
        posixIdentities.remove(path);
        acls.remove(path);
      }
    }

    void clearPosix(Path path) {
      posixPermissions.remove(path);
      posixIdentities.remove(path);
    }

    void failRealPath(IOException failure) {
      realPathIoFailure = Objects.requireNonNull(failure, "failure");
    }

    void failRealPath(RuntimeException failure) {
      realPathRuntimeFailure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public boolean isDirectoryNoFollow(Path path) {
      return noFollowEntryKind(path) == PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY;
    }

    @Override
    public PrivateOutputDirectory.NoFollowEntryKind noFollowEntryKind(Path path) {
      return entryKinds.getOrDefault(path, PrivateOutputDirectory.NoFollowEntryKind.MISSING);
    }

    @Override
    public boolean supportsPosix(Path path) {
      return posixPermissions.containsKey(path);
    }

    @Override
    public boolean supportsAcl(Path path) {
      return acls.containsKey(path);
    }

    @Override
    public Path toRealPath(Path path) throws IOException {
      if (realPathIoFailure != null) {
        throw realPathIoFailure;
      }
      if (realPathRuntimeFailure != null) {
        throw realPathRuntimeFailure;
      }
      return path;
    }

    @Override
    public @Nullable Path parent(Path path) {
      return path.getParent();
    }

    @Override
    public Set<PosixFilePermission> readPosixPermissions(Path path) throws IOException {
      Set<PosixFilePermission> permissions = posixPermissions.get(path);
      if (permissions == null) {
        throw new IOException("No POSIX permissions were configured for " + path + ".");
      }
      return permissions;
    }

    @Override
    public PrivateOutputDirectory.PosixDirectoryIdentity readPosixDirectoryIdentity(Path path)
        throws IOException {
      PrivateOutputDirectory.PosixDirectoryIdentity identity = posixIdentities.get(path);
      if (identity == null) {
        throw new IOException("No POSIX identity was configured for " + path + ".");
      }
      return identity;
    }

    @Override
    public PrivateOutputDirectory.AclState readAcl(Path path) throws IOException {
      PrivateOutputDirectory.AclState state = acls.get(path);
      if (state == null) {
        throw new IOException("No ACL was configured for " + path + ".");
      }
      return state;
    }
  }
}
