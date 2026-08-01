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
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves inherited-only ACL templates do not grant access on the directory being checked. */
class PrivateOutputDirectoryAclInheritanceTest {
  private static final Path ROOT = Path.of("/protected-root");
  private static final Path ANCESTOR = ROOT.resolve("reports");
  private static final Path OUTPUT = ANCESTOR.resolve("private");
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal COLLABORATOR = () -> "collaborator";

  @Test
  void admission_ignoresInheritOnlyAclEntriesThatGrantNothingOnTheCheckedDirectory() {
    FakeFilesystemAccess filesystem =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    PrivateOutputDirectory.AclState privateAcl =
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                allowed(OWNER, AclEntryPermission.values()),
                allowedWithFlags(
                    COLLABORATOR,
                    Set.of(AclEntryFlag.INHERIT_ONLY),
                    AclEntryPermission.ADD_FILE,
                    AclEntryPermission.LIST_DIRECTORY)));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putAcl(path, privateAcl);
    }

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  private static AclEntry allowed(UserPrincipal principal, AclEntryPermission... permissions) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setPermissions(permissions)
        .build();
  }

  private static AclEntry allowedWithFlags(
      UserPrincipal principal, Set<AclEntryFlag> flags, AclEntryPermission... permissions) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(principal)
        .setFlags(flags)
        .setPermissions(permissions)
        .build();
  }
}
