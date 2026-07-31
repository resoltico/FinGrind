package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.erst.fingrind.core.PrivateOutputDirectoryTestFilesystem.FakeFilesystemAccess;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the ACL boundary accepts different principal objects with one native identity. */
class PrivateOutputDirectoryAclIdentityTest {
  private static final Path ROOT = Path.of("/protected-root");
  private static final Path ANCESTOR = ROOT.resolve("reports");
  private static final Path OUTPUT = ANCESTOR.resolve("private");
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal OWNER_ALIAS = () -> "owner-alias";
  private static final UserPrincipal COLLABORATOR = () -> "collaborator";

  @Test
  void admission_acceptsNativeEquivalentAclOwnersAndEntriesWithDifferentPrincipalObjects() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    filesystem.equateAclPrincipals(OWNER, OWNER_ALIAS);
    filesystem.putAcl(
        OUTPUT,
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                inheritOnlyAllowed(COLLABORATOR),
                allowed(COLLABORATOR),
                allowed(OWNER_ALIAS, AclEntryPermission.values()))));
    PrivateOutputDirectory.AclState nativeEquivalentOwnerAcl =
        new PrivateOutputDirectory.AclState(
            OWNER_ALIAS, List.of(allowed(OWNER_ALIAS, AclEntryPermission.values())));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR)) {
      filesystem.putAcl(path, nativeEquivalentOwnerAcl);
    }

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_acceptsAnAncestorOwnedByTheCurrentWindowsToken() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    filesystem.recognizeCurrentTokenAclPrincipal(COLLABORATOR);
    PrivateOutputDirectory.AclState outputOwnerAcl =
        new PrivateOutputDirectory.AclState(
            OWNER, List.of(allowed(OWNER, AclEntryPermission.values())));
    for (Path path : List.of(Path.of("/"), ROOT, OUTPUT)) {
      filesystem.putAcl(path, outputOwnerAcl);
    }
    filesystem.putAcl(
        ANCESTOR,
        new PrivateOutputDirectory.AclState(
            COLLABORATOR,
            List.of(
                allowed(OWNER, AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE))));

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  private static FakeFilesystemAccess lexicalFilesystem() {
    FakeFilesystemAccess filesystem =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    for (Path directory : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.markDirectory(directory);
    }
    return filesystem;
  }

  private static AclEntry allowed(UserPrincipal principal, AclEntryPermission... permissions) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(permissions)
        .build();
  }

  private static AclEntry inheritOnlyAllowed(
      UserPrincipal principal, AclEntryPermission... permissions) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(permissions)
        .setFlags(AclEntryFlag.INHERIT_ONLY)
        .build();
  }
}
