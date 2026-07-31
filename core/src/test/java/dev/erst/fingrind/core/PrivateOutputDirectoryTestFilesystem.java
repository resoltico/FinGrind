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
    private final Set<Path> aclSupportedPaths = ConcurrentHashMap.newKeySet();
    private final Set<UserPrincipal> trustedAclMutationPrincipals = ConcurrentHashMap.newKeySet();
    private final Set<UserPrincipal> currentTokenAclPrincipals = ConcurrentHashMap.newKeySet();
    private final Set<UserPrincipal> creationAclMutationPrincipals = ConcurrentHashMap.newKeySet();
    private final Set<AclPrincipalIdentityPair> equivalentAclPrincipalPairs =
        ConcurrentHashMap.newKeySet();
    private final Map<Path, IOException> noFollowEntryKindFailures = new ConcurrentHashMap<>();
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
      aclSupportedPaths.add(path);
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
      aclSupportedPaths.add(path);
      acls.put(path, aclState);
    }

    void markAclSupported(Path path) {
      aclSupportedPaths.add(path);
    }

    void trustAclMutationPrincipal(UserPrincipal principal) {
      trustedAclMutationPrincipals.add(Objects.requireNonNull(principal, "principal"));
    }

    void recognizeCurrentTokenAclPrincipal(UserPrincipal principal) {
      currentTokenAclPrincipals.add(Objects.requireNonNull(principal, "principal"));
    }

    void permitCreationAclMutationPrincipal(UserPrincipal principal) {
      creationAclMutationPrincipals.add(Objects.requireNonNull(principal, "principal"));
    }

    void equateAclPrincipals(UserPrincipal firstPrincipal, UserPrincipal secondPrincipal) {
      UserPrincipal checkedFirstPrincipal =
          Objects.requireNonNull(firstPrincipal, "firstPrincipal");
      UserPrincipal checkedSecondPrincipal =
          Objects.requireNonNull(secondPrincipal, "secondPrincipal");
      equivalentAclPrincipalPairs.add(
          new AclPrincipalIdentityPair(checkedFirstPrincipal, checkedSecondPrincipal));
      equivalentAclPrincipalPairs.add(
          new AclPrincipalIdentityPair(checkedSecondPrincipal, checkedFirstPrincipal));
    }

    void markDirectory(Path path) {
      entryKinds.put(path, PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY);
    }

    void markOther(Path path) {
      entryKinds.put(path, PrivateOutputDirectory.NoFollowEntryKind.OTHER);
      posixPermissions.remove(path);
      posixIdentities.remove(path);
      acls.remove(path);
      aclSupportedPaths.remove(path);
    }

    void removeDirectory(Path path) {
      if (entryKinds.remove(path, PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY)) {
        posixPermissions.remove(path);
        posixIdentities.remove(path);
        acls.remove(path);
        aclSupportedPaths.remove(path);
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

    void failNoFollowEntryKind(Path path, IOException failure) {
      noFollowEntryKindFailures.put(
          Objects.requireNonNull(path, "path"), Objects.requireNonNull(failure, "failure"));
    }

    @Override
    public boolean isDirectoryNoFollow(Path path) {
      return entryKinds.getOrDefault(path, PrivateOutputDirectory.NoFollowEntryKind.MISSING)
          == PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY;
    }

    @Override
    public PrivateOutputDirectory.NoFollowEntryKind noFollowEntryKind(Path path)
        throws IOException {
      IOException failure = noFollowEntryKindFailures.get(path);
      if (failure != null) {
        throw failure;
      }
      return entryKinds.getOrDefault(path, PrivateOutputDirectory.NoFollowEntryKind.MISSING);
    }

    @Override
    public boolean supportsPosix(Path path) {
      return posixPermissions.containsKey(path);
    }

    @Override
    public boolean supportsAcl(Path path) {
      return aclSupportedPaths.contains(path);
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

    @Override
    public boolean isTrustedAclMutationPrincipal(Path path, UserPrincipal principal) {
      return trustedAclMutationPrincipals.contains(principal);
    }

    @Override
    public boolean isCurrentTokenAclPrincipal(Path path, UserPrincipal principal)
        throws IOException {
      return currentTokenAclPrincipals.contains(principal)
          || PrivateOutputDirectory.FilesystemAccess.super.isCurrentTokenAclPrincipal(
              path, principal);
    }

    @Override
    public boolean matchesAclPrincipalIdentity(
        Path path, UserPrincipal firstPrincipal, UserPrincipal secondPrincipal) throws IOException {
      UserPrincipal checkedFirstPrincipal =
          Objects.requireNonNull(firstPrincipal, "firstPrincipal");
      UserPrincipal checkedSecondPrincipal =
          Objects.requireNonNull(secondPrincipal, "secondPrincipal");
      return equivalentAclPrincipalPairs.contains(
              new AclPrincipalIdentityPair(checkedFirstPrincipal, checkedSecondPrincipal))
          || PrivateOutputDirectory.FilesystemAccess.super.matchesAclPrincipalIdentity(
              path, checkedFirstPrincipal, checkedSecondPrincipal);
    }

    @Override
    public List<UserPrincipal> permittedAclMutationPrincipalsForCreation(
        Path path, PrivateOutputDirectory.AclState aclState) throws IOException {
      if (creationAclMutationPrincipals.isEmpty()) {
        return PrivateOutputDirectory.FilesystemAccess.super
            .permittedAclMutationPrincipalsForCreation(path, aclState);
      }
      Set<UserPrincipal> permitted = ConcurrentHashMap.newKeySet();
      permitted.add(Objects.requireNonNull(aclState, "aclState").owner());
      permitted.addAll(creationAclMutationPrincipals);
      return List.copyOf(permitted);
    }

    private record AclPrincipalIdentityPair(
        UserPrincipal firstPrincipal, UserPrincipal secondPrincipal) {}
  }
}
