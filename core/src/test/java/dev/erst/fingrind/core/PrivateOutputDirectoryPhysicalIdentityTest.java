package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves stable physical identities used to order cooperating publication-directory leases. */
class PrivateOutputDirectoryPhysicalIdentityTest {
  @Test
  void choosesThePosixDeviceAndInodeIdentityWhenThatProofIsAvailable() throws Exception {
    assertEquals(
        "posix-v1:dev=1:ino=2",
        PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
            Path.of("private"), operations(true, false, false, "posix-v1:dev=1:ino=2", "windows")));
  }

  @Test
  void choosesTheExactWindowsDirectoryIdentityWhenOnlyWindowsAclProofIsAvailable()
      throws Exception {
    assertEquals(
        "windows-v1:volume=3:file=abc",
        PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
            Path.of("private"),
            operations(false, true, true, "posix", "windows-v1:volume=3:file=abc")));
  }

  @Test
  void failsClosedWhenTheFilesystemCannotProveAPhysicalDirectoryIdentity() {
    PrivateOutputDirectory.Violation violation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
                    Path.of("private"), operations(false, false, false, "posix", "windows")));

    assertTrue(violation.getCause() instanceof UnsupportedOperationException);
    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
                Path.of("private"), operations(false, true, false, "posix", "windows")));
  }

  @Test
  void preservesAdmissionRefusalsAndWrapsIdentityReadFailures() {
    PrivateOutputDirectory.Violation admissionFailure =
        new PrivateOutputDirectory.Violation(
            PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, "admission refused");
    assertSame(
        admissionFailure,
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
                    Path.of("private"),
                    operations(false, false, false, "posix", "windows", admissionFailure, null))));

    IOException identityFailure = new IOException("identity read failed");
    PrivateOutputDirectory.Violation wrapped =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryPhysicalIdentity.physicalObjectIdentity(
                    Path.of("private"),
                    operations(true, false, false, "posix", "windows", null, identityFailure)));
    assertSame(identityFailure, wrapped.getCause());
  }

  @Test
  void readsTheProductionPosixIdentityWithoutFollowingAnAlias(@TempDir Path temporaryDirectory)
      throws Exception {
    Set<PosixFilePermission> ownerOnly =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    Files.setPosixFilePermissions(temporaryDirectory, ownerOnly);

    assertTrue(
        PrivateOutputDirectory.physicalObjectIdentity(temporaryDirectory)
            .matches("posix-v1:dev=[0-9]+:ino=[0-9]+"));
  }

  @Test
  void productionOperationsReadWindowsFactsThroughTheInjectedNativeIdentityReader()
      throws Exception {
    PrivateOutputDirectoryTestFilesystem.FakeFilesystemAccess filesystemAccess =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(() -> "owner");
    Path directory = Path.of("private");
    filesystemAccess.enableAcl(directory);
    PrivateOutputDirectoryPhysicalIdentity.ProductionOperations operations =
        new PrivateOutputDirectoryPhysicalIdentity.ProductionOperations(
            filesystemAccess, "Windows 11", ignored -> "windows-v1:volume=3:file=abc");

    assertTrue(operations.supportsAcl(directory));
    assertTrue(operations.isWindows());
    assertEquals("windows-v1:volume=3:file=abc", operations.windowsPhysicalIdentity(directory));
  }

  private static PrivateOutputDirectoryPhysicalIdentity.Operations operations(
      boolean posix, boolean acl, boolean windows, String posixIdentity, String windowsIdentity) {
    return operations(posix, acl, windows, posixIdentity, windowsIdentity, null, null);
  }

  private static PrivateOutputDirectoryPhysicalIdentity.Operations operations(
      boolean posix,
      boolean acl,
      boolean windows,
      String posixIdentity,
      String windowsIdentity,
      PrivateOutputDirectory.@Nullable Violation admissionFailure,
      @Nullable IOException identityFailure) {
    return new PrivateOutputDirectoryPhysicalIdentity.Operations() {
      @Override
      public void requireExistingOwnerOnly(Path directory) throws PrivateOutputDirectory.Violation {
        if (admissionFailure != null) {
          throw admissionFailure;
        }
      }

      @Override
      public boolean supportsPosix(Path directory) {
        return posix;
      }

      @Override
      public boolean supportsAcl(Path directory) {
        return acl;
      }

      @Override
      public boolean isWindows() {
        return windows;
      }

      @Override
      public String posixPhysicalIdentity(Path directory) throws IOException {
        return identity(posixIdentity, identityFailure);
      }

      @Override
      public String windowsPhysicalIdentity(Path directory) throws IOException {
        return identity(windowsIdentity, identityFailure);
      }
    };
  }

  private static String identity(String identity, @Nullable IOException failure)
      throws IOException {
    if (failure != null) {
      throw failure;
    }
    return identity;
  }
}
