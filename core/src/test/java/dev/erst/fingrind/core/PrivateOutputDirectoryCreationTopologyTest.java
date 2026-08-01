package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PrivateOutputDirectoryTestFilesystem.FakeFilesystemAccess;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies topology and access facts before private output-directory creation proceeds. */
class PrivateOutputDirectoryCreationTopologyTest {
  private static final Path ROOT = Path.of("/protected-root");
  private static final Path ANCESTOR = ROOT.resolve("reports");
  private static final Path OUTPUT = ANCESTOR.resolve("private");
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal PROFILE_OWNER = () -> "profile-owner";
  private static final UserPrincipal CURRENT_TOKEN_USER = () -> "current-token-user";
  private static final Set<PosixFilePermission> READABLE_SEARCHABLE_ANCESTOR =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_EXECUTE);

  @Test
  void topologyRejectsMissingAndNonDirectoryPathsBeforeAPrivateDirectoryIsCreated() {
    FakeFilesystemAccess missingRoot =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    FakeFilesystemAccess collision = privatePosixFilesystem();
    collision.markOther(OUTPUT);
    FakeFilesystemAccess missingComponent = privatePosixFilesystem();
    missingComponent.removeDirectory(ANCESTOR);
    FakeFilesystemAccess nonDirectoryComponent = privatePosixFilesystem();
    nonDirectoryComponent.markOther(ANCESTOR);
    Path rootlessPath = rootlessPath();

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryPathTopology.nearestExistingDirectory(Path.of("/"), missingRoot));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () -> PrivateOutputDirectoryPathTopology.missingDirectoryChain(Path.of("/"), missingRoot));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () -> PrivateOutputDirectoryPathTopology.missingDirectoryChain(OUTPUT, collision));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryPathTopology.requireLexicalRealDirectoryPath(
                rootlessPath, collision));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryPathTopology.requireLexicalRealDirectoryPath(
                OUTPUT, missingComponent));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryPathTopology.requireLexicalRealDirectoryPath(
                OUTPUT, nonDirectoryComponent));
  }

  @Test
  void creationSecurityRequiresDirectoryAndAccessFactsAtEveryExistingAncestor() {
    FakeFilesystemAccess nonDirectory = privatePosixFilesystem();
    nonDirectory.markOther(ANCESTOR);
    FakeFilesystemAccess noAccessModel = lexicalFilesystem();
    FakeFilesystemAccess aclOnly = lexicalFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      aclOnly.putAcl(path, ownerOnlyAcl);
    }

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireExistingCreationAncestry(ANCESTOR, nonDirectory));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireExistingCreationAncestry(
                ANCESTOR, noAccessModel));
    assertDoesNotThrow(
        () -> PrivateOutputDirectorySecurity.requireExistingCreationAncestry(OUTPUT, aclOnly));
  }

  @Test
  void protectedAncestryRejectsNonDirectoriesAndMixedAccessModels() {
    FakeFilesystemAccess nonDirectory = privatePosixFilesystem();
    nonDirectory.markOther(ANCESTOR);
    FakeFilesystemAccess posixWithoutOutputIdentity = privatePosixFilesystem();
    FakeFilesystemAccess aclWithoutOutputIdentity = lexicalFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      aclWithoutOutputIdentity.putAcl(path, ownerOnlyAcl);
    }
    PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity posixIdentity =
        new PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity(
            new PrivateOutputDirectory.PosixDirectoryIdentity(OWNER, 1_000L, false), null);
    PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity noIdentity =
        new PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity(null, null);

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireProtectedAncestry(
                OUTPUT, posixIdentity, nonDirectory));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireProtectedAncestry(
                OUTPUT, noIdentity, posixWithoutOutputIdentity));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireProtectedAncestry(
                OUTPUT, noIdentity, aclWithoutOutputIdentity));
  }

  @Test
  void creationAncestryRejectsAnOtherWritableNonStickyAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.removeDirectory(OUTPUT);
    filesystem.putPosix(
        ANCESTOR,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE));

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () -> PrivateOutputDirectory.requireCreationAncestry(OUTPUT, filesystem));
  }

  @Test
  void creationAncestryWrapsFilesystemFactFailuresWithoutDiscardingTheirCause() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    IOException failure = new IOException("simulated ancestry canonicalization failure");
    filesystem.failRealPath(failure);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireCreationAncestry(OUTPUT, filesystem));

    assertSame(failure, exception.getCause());
  }

  @Test
  void creationAncestryAdmitsOnlyTheCurrentTokenUserWhenProfileOwnershipDiffers() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putAcl(path, ownerOnlyAcl);
    }
    filesystem.putAcl(
        ANCESTOR,
        new PrivateOutputDirectory.AclState(
            PROFILE_OWNER,
            List.of(
                AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(CURRENT_TOKEN_USER)
                    .setPermissions(AclEntryPermission.values())
                    .build())));
    filesystem.permitCreationAclMutationPrincipal(CURRENT_TOKEN_USER);

    assertDoesNotThrow(
        () -> PrivateOutputDirectorySecurity.requireExistingCreationAncestry(ANCESTOR, filesystem));
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectorySecurity.requireProtectedAncestry(
                OUTPUT,
                new PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity(null, OWNER),
                filesystem));
  }

  private static Path rootlessPath() {
    return (Path)
        Proxy.newProxyInstance(
            Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader(), "context class loader"),
            new Class<?>[] {Path.class},
            (proxy, method, ignored) ->
                switch (method.getName()) {
                  case "toAbsolutePath", "normalize" -> proxy;
                  case "getRoot" -> null;
                  case "toString" -> "rootless";
                  default ->
                      throw new AssertionError(
                          "Unexpected rootless Path invocation: " + method.getName());
                });
  }

  private static FakeFilesystemAccess privatePosixFilesystem() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putPosix(path, READABLE_SEARCHABLE_ANCESTOR);
    }
    return filesystem;
  }

  private static FakeFilesystemAccess lexicalFilesystem() {
    FakeFilesystemAccess filesystem =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    for (Path directory : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.markDirectory(directory);
    }
    return filesystem;
  }

  private static AclEntry ownerAllowsAll() {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(OWNER)
        .setPermissions(AclEntryPermission.values())
        .build();
  }
}
