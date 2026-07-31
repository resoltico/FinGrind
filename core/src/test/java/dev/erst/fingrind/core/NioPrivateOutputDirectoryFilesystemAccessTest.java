package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the NIO fact reader reports and validates filesystem metadata without interpretation.
 */
class NioPrivateOutputDirectoryFilesystemAccessTest {
  private static final int UNIX_STICKY_BIT = 0x200;
  private static final int OWNER_READ_WRITE_SEARCH_MODE = 0x1C0;
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal OWNER_ALIAS = () -> "owner-alias";
  private static final UserPrincipal CURRENT_TOKEN_USER = () -> "DOMAIN\\current-token-user";
  private static final GroupPrincipal GROUP = () -> "group";

  @TempDir Path temporaryDirectory;

  @Test
  void readsDefaultFilesystemFactsForAnExistingDirectory() throws IOException {
    NioPrivateOutputDirectoryFilesystemAccess access =
        new NioPrivateOutputDirectoryFilesystemAccess();

    assertTrue(access.isDirectoryNoFollow(temporaryDirectory));
    assertEquals(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        access.supportsPosix(temporaryDirectory));
    assertEquals(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("acl"),
        access.supportsAcl(temporaryDirectory));
    assertEquals(temporaryDirectory.toRealPath(), access.toRealPath(temporaryDirectory));
    assertEquals(temporaryDirectory.getParent(), access.parent(temporaryDirectory));

    if (access.supportsPosix(temporaryDirectory)) {
      assertEquals(
          Files.getPosixFilePermissions(temporaryDirectory, LinkOption.NOFOLLOW_LINKS),
          access.readPosixPermissions(temporaryDirectory));
      PrivateOutputDirectory.PosixDirectoryIdentity identity =
          access.readPosixDirectoryIdentity(temporaryDirectory);
      assertEquals(Files.getOwner(temporaryDirectory, LinkOption.NOFOLLOW_LINKS), identity.owner());
      assertTrue(identity.unixUserId() >= 0L);
    }

    if (access.supportsAcl(temporaryDirectory)) {
      assertEquals(
          Files.getOwner(temporaryDirectory, LinkOption.NOFOLLOW_LINKS),
          access.readAcl(temporaryDirectory).owner());
    } else {
      assertThrows(IOException.class, () -> access.readAcl(temporaryDirectory));
    }
    assertFalse(access.isTrustedAclMutationPrincipal(temporaryDirectory, OWNER));
    if (!WindowsTrustedAclPrincipalResolver.isWindows(System.getProperty("os.name", ""))) {
      assertFalse(access.isCurrentTokenAclPrincipal(temporaryDirectory, OWNER));
      assertEquals(
          List.of(OWNER),
          access.permittedAclMutationPrincipalsForCreation(
              temporaryDirectory, new PrivateOutputDirectory.AclState(OWNER, List.of())));
      assertEquals(
          PrivateOutputDirectory.AclMutationPrincipalKind.OTHER,
          access.classifyAclMutationPrincipal(temporaryDirectory, OWNER));
    }
  }

  @Test
  void readsInjectedUnixIdentityFactsIncludingBothStickyBitStates() throws IOException {
    PosixFileAttributes attributes = posixAttributes();

    PrivateOutputDirectory.PosixDirectoryIdentity sticky =
        new NioPrivateOutputDirectoryFilesystemAccess(
                ignored -> attributes,
                (ignored, attribute) ->
                    "unix:mode".equals(attribute)
                        ? UNIX_STICKY_BIT | OWNER_READ_WRITE_SEARCH_MODE
                        : 501L,
                ignored -> new FixedAclView(OWNER, List.of()))
            .readPosixDirectoryIdentity(temporaryDirectory);
    assertEquals(OWNER, sticky.owner());
    assertEquals(501L, sticky.unixUserId());
    assertTrue(sticky.sticky());

    PrivateOutputDirectory.PosixDirectoryIdentity nonSticky =
        new NioPrivateOutputDirectoryFilesystemAccess(
                ignored -> attributes,
                (ignored, attribute) ->
                    "unix:mode".equals(attribute) ? OWNER_READ_WRITE_SEARCH_MODE : 502L,
                ignored -> new FixedAclView(OWNER, List.of()))
            .readPosixDirectoryIdentity(temporaryDirectory);
    assertEquals(502L, nonSticky.unixUserId());
    assertFalse(nonSticky.sticky());
  }

  @Test
  void rejectsNonNumericUnixIdentityFacts() {
    PosixFileAttributes attributes = posixAttributes();

    IOException modeFailure =
        assertThrows(
            IOException.class,
            () ->
                new NioPrivateOutputDirectoryFilesystemAccess(
                        ignored -> attributes,
                        (ignored, attribute) -> "not-a-mode",
                        ignored -> new FixedAclView(OWNER, List.of()))
                    .readPosixDirectoryIdentity(temporaryDirectory));
    assertTrue(
        Objects.requireNonNull(modeFailure.getMessage(), "mode failure message")
            .contains("UNIX mode"));

    IOException userIdFailure =
        assertThrows(
            IOException.class,
            () ->
                new NioPrivateOutputDirectoryFilesystemAccess(
                        ignored -> attributes,
                        (ignored, attribute) ->
                            "unix:mode".equals(attribute)
                                ? OWNER_READ_WRITE_SEARCH_MODE
                                : "not-a-user-id",
                        ignored -> new FixedAclView(OWNER, List.of()))
                    .readPosixDirectoryIdentity(temporaryDirectory));
    assertTrue(
        Objects.requireNonNull(userIdFailure.getMessage(), "user identifier failure message")
            .contains("UNIX user identifier"));
  }

  @Test
  void returnsAclFactsOrRejectsAnUnavailableAclView() throws IOException {
    List<AclEntry> entries = List.of();
    NioPrivateOutputDirectoryFilesystemAccess available =
        new NioPrivateOutputDirectoryFilesystemAccess(
            ignored -> posixAttributes(),
            (ignored, attribute) -> 0L,
            ignored -> new FixedAclView(OWNER, entries));

    PrivateOutputDirectory.AclState state = available.readAcl(temporaryDirectory);
    assertEquals(OWNER, state.owner());
    assertEquals(entries, state.entries());

    NioPrivateOutputDirectoryFilesystemAccess unavailable =
        new NioPrivateOutputDirectoryFilesystemAccess(
            ignored -> posixAttributes(), (ignored, attribute) -> 0L, ignored -> null);
    IOException failure =
        assertThrows(IOException.class, () -> unavailable.readAcl(temporaryDirectory));
    assertTrue(
        Objects.requireNonNull(failure.getMessage(), "ACL failure message").contains("ACL view"));
  }

  @Test
  void creationAclMutationPermissionAdmitsOnlyTheObservedSidBackedCurrentUserOnWindows()
      throws IOException {
    PrivateOutputDirectory.AclState aclState =
        new PrivateOutputDirectory.AclState(OWNER, List.of(allowEntry(CURRENT_TOKEN_USER)));

    assertEquals(
        List.of(OWNER),
        NioPrivateOutputDirectoryFilesystemAccess.permittedAclMutationPrincipalsForCreation(
            "Linux",
            temporaryDirectory,
            aclState,
            () ->
                candidate -> {
                  throw new AssertionError("non-Windows creation must not resolve a token user");
                }));
    assertEquals(
        List.of(OWNER, CURRENT_TOKEN_USER),
        NioPrivateOutputDirectoryFilesystemAccess.permittedAclMutationPrincipalsForCreation(
            "Windows Server 2025",
            temporaryDirectory,
            aclState,
            () -> candidate -> candidate.getName().equalsIgnoreCase(CURRENT_TOKEN_USER.getName())));
    assertEquals(
        List.of(OWNER),
        NioPrivateOutputDirectoryFilesystemAccess.permittedAclMutationPrincipalsForCreation(
            "Windows Server 2025",
            temporaryDirectory,
            new PrivateOutputDirectory.AclState(OWNER, List.of()),
            () -> candidate -> candidate.getName().equalsIgnoreCase(CURRENT_TOKEN_USER.getName())));
    assertEquals(
        List.of(OWNER),
        NioPrivateOutputDirectoryFilesystemAccess.permittedAclMutationPrincipalsForCreation(
            "Windows Server 2025",
            temporaryDirectory,
            new PrivateOutputDirectory.AclState(OWNER, List.of()),
            () -> candidate -> candidate.getName().equalsIgnoreCase(OWNER.getName())));
  }

  @Test
  void productionTrustedAclMatcherSourceCreatesItsNativeMatcherLazily() throws IOException {
    assertNotNull(
        NioPrivateOutputDirectoryFilesystemAccess.productionTrustedAclPrincipalMatcherSource()
            .acquire());
  }

  @Test
  void nativeAclIdentityMatchingUsesTheWindowsSidMatcherOnlyOnWindows() throws IOException {
    int[] nativeMatcherCalls = {0};
    NioPrivateOutputDirectoryFilesystemAccess.AclPrincipalIdentityMatcher nativeMatcher =
        (firstPrincipal, secondPrincipal) -> {
          nativeMatcherCalls[0]++;
          assertEquals(OWNER, firstPrincipal);
          assertEquals(OWNER_ALIAS, secondPrincipal);
          return true;
        };

    assertTrue(
        NioPrivateOutputDirectoryFilesystemAccess.matchesAclPrincipalIdentity(
            "Windows Server 2025", temporaryDirectory, OWNER, OWNER_ALIAS, nativeMatcher));
    assertTrue(
        NioPrivateOutputDirectoryFilesystemAccess.matchesAclPrincipalIdentity(
            "Linux", temporaryDirectory, OWNER, OWNER, nativeMatcher));
    assertFalse(
        NioPrivateOutputDirectoryFilesystemAccess.matchesAclPrincipalIdentity(
            "Linux", temporaryDirectory, OWNER, OWNER_ALIAS, nativeMatcher));
    assertEquals(1, nativeMatcherCalls[0]);
  }

  @Test
  void productionAclIdentityMatchingUsesTheHostPlatformContract() throws IOException {
    NioPrivateOutputDirectoryFilesystemAccess access =
        new NioPrivateOutputDirectoryFilesystemAccess();

    assertTrue(access.matchesAclPrincipalIdentity(temporaryDirectory, OWNER, OWNER));
    assertFalse(access.matchesAclPrincipalIdentity(temporaryDirectory, OWNER, OWNER_ALIAS));
  }

  private static AclEntry allowEntry(UserPrincipal principal) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(AclEntryPermission.ADD_FILE)
        .build();
  }

  private static PosixFileAttributes posixAttributes() {
    return new PosixFileAttributes() {
      @Override
      public UserPrincipal owner() {
        return OWNER;
      }

      @Override
      public GroupPrincipal group() {
        return GROUP;
      }

      @Override
      public Set<PosixFilePermission> permissions() {
        return Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      }

      @Override
      public FileTime lastModifiedTime() {
        return FileTime.fromMillis(0L);
      }

      @Override
      public FileTime lastAccessTime() {
        return FileTime.fromMillis(0L);
      }

      @Override
      public FileTime creationTime() {
        return FileTime.fromMillis(0L);
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
        return 0L;
      }

      @Override
      public Object fileKey() {
        return "fixed-directory-key";
      }
    };
  }

  private record FixedAclView(UserPrincipal owner, List<AclEntry> entries)
      implements AclFileAttributeView {
    FixedAclView {
      Objects.requireNonNull(owner, "owner");
      entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    @Override
    public String name() {
      return "acl";
    }

    @Override
    public UserPrincipal getOwner() {
      return owner;
    }

    @Override
    public void setOwner(UserPrincipal owner) {
      throw new UnsupportedOperationException("The fixed test ACL view is read-only.");
    }

    @Override
    public List<AclEntry> getAcl() {
      return entries;
    }

    @Override
    public void setAcl(List<AclEntry> entries) {
      throw new UnsupportedOperationException("The fixed test ACL view is read-only.");
    }
  }
}
