package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.core.PrivateOutputDirectoryTestFilesystem.FakeFilesystemAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises platform-neutral creation dispatch and fresh-child allocation failure boundaries. */
class PrivateOutputDirectoryCreationTest {
  private static final Path ROOT = Path.of("/private-output-root");
  private static final Path PARENT = ROOT.resolve(Path.of("staging"));
  private static final Path OUTPUT = PARENT.resolve(Path.of("allocated"));
  private static final UserPrincipal OWNER = () -> "owner";
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  @TempDir Path temporaryDirectory;

  @Test
  void creationDispatchesTheWindowsModeThroughTheProtectedAclContract()
      throws PrivateOutputDirectory.Violation {
    FakeFilesystemAccess filesystem = privateAclCreationFilesystem();
    AtomicInteger windowsCreationCount = new AtomicInteger();
    PrivateOutputDirectoryCreation.ProductionOperations operations =
        new PrivateOutputDirectoryCreation.ProductionOperations(
            filesystem,
            true,
            directory -> {
              windowsCreationCount.incrementAndGet();
              filesystem.putAcl(
                  directory, new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll())));
            });

    assertFalse(operations.supportsPosix(OUTPUT));
    assertTrue(operations.supportsAcl(OUTPUT));
    assertTrue(operations.isWindows());
    assertSame(filesystem, operations.filesystemAccess());
    PrivateOutputDirectoryCreation.createNewOwnerOnlyDirectories(OUTPUT, operations);

    assertEquals(1, windowsCreationCount.get());
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void creationDispatchesThePosixModeThroughTheAtomicOwnerOnlyCreationBoundary()
      throws PrivateOutputDirectory.Violation {
    FakeFilesystemAccess filesystem = privatePosixCreationFilesystem();
    AtomicInteger posixCreationCount = new AtomicInteger();
    PrivateOutputDirectoryCreation.Operations operations =
        new PrivateOutputDirectoryCreation.Operations() {
          @Override
          public boolean supportsPosix(Path path) {
            return true;
          }

          @Override
          public boolean supportsAcl(Path path) {
            return false;
          }

          @Override
          public boolean isWindows() {
            return false;
          }

          @Override
          public FakeFilesystemAccess filesystemAccess() {
            return filesystem;
          }

          @Override
          public void createPosixDirectory(
              Path directory, java.nio.file.attribute.FileAttribute<?>... attributes) {
            posixCreationCount.incrementAndGet();
            filesystem.putPosix(directory, OWNER_ONLY_DIRECTORY);
          }

          @Override
          public void createWindowsDirectory(Path directory) {
            throw new AssertionError("POSIX creation must not call the Windows creation boundary");
          }
        };

    PrivateOutputDirectoryCreation.createNewOwnerOnlyDirectories(OUTPUT, operations);

    assertEquals(1, posixCreationCount.get());
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void freshChildAllocationRejectsAParentThatIsNotAnExistingDirectory() {
    FakeFilesystemAccess filesystem = privatePosixCreationFilesystem();
    filesystem.markOther(PARENT);

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                PARENT,
                ".stage-",
                () -> "candidate",
                ignored -> {
                  throw new AssertionError("a non-directory parent must not allocate a child");
                },
                filesystem));
  }

  @Test
  void freshChildAllocationReportsExhaustionOnlyAfterEveryObservedCollision()
      throws PrivateOutputDirectory.Violation {
    FakeFilesystemAccess filesystem = privatePosixCreationFilesystem();
    AtomicInteger creationAttempts = new AtomicInteger();

    PrivateOutputDirectory.Violation failure =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                    PARENT,
                    ".stage-",
                    () -> "reused-token",
                    candidate -> {
                      creationAttempts.incrementAndGet();
                      filesystem.markDirectory(candidate);
                      throw new PrivateOutputDirectory.Violation(
                          PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
                          "simulated observed collision");
                    },
                    filesystem));

    assertEquals(64, creationAttempts.get());
    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, failure.kind());
  }

  @Test
  void freshChildAllocationRejectsBlankPrefixesAndDoesNotDiscardCandidateObservationFailures()
      throws PrivateOutputDirectory.Violation {
    FakeFilesystemAccess filesystem = privatePosixCreationFilesystem();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                PARENT, "", () -> "candidate", ignored -> {}, filesystem));

    Path candidate = PARENT.resolve(".stage-candidate");
    IOException observationFailure = new IOException("simulated candidate observation failure");
    filesystem.failNoFollowEntryKind(candidate, observationFailure);
    PrivateOutputDirectory.Violation observationViolation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                    PARENT,
                    ".stage-",
                    () -> "candidate",
                    ignored -> {
                      throw new PrivateOutputDirectory.Violation(
                          PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
                          "simulated creation collision");
                    },
                    filesystem));

    assertSame(observationFailure, observationViolation.getCause());
  }

  @Test
  void freshChildAllocationPreservesUnobservedCollisionsAndParentFactFailures()
      throws PrivateOutputDirectory.Violation {
    FakeFilesystemAccess collisionFilesystem = privatePosixCreationFilesystem();
    PrivateOutputDirectory.Violation collision =
        new PrivateOutputDirectory.Violation(
            PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, "simulated unobserved collision");

    assertSame(
        collision,
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                    PARENT,
                    ".stage-",
                    () -> "candidate",
                    ignored -> {
                      throw collision;
                    },
                    collisionFilesystem)));

    FakeFilesystemAccess parentFilesystem = privatePosixCreationFilesystem();
    IOException parentFailure = new IOException("simulated parent canonicalization failure");
    parentFilesystem.failNoFollowEntryKind(PARENT, parentFailure);
    PrivateOutputDirectory.Violation parentViolation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                    PARENT, ".stage-", () -> "candidate", ignored -> {}, parentFilesystem));

    assertSame(parentFailure, parentViolation.getCause());
  }

  @Test
  void defaultFreshChildAllocationUsesTheProductionRandomTokenAndOwnerOnlyCreation()
      throws IOException {
    assumePosix();
    Path parent = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(parent, OWNER_ONLY_DIRECTORY);

    Path child = PrivateOutputDirectory.createNewOwnerOnlyChild(parent, ".private-stage-");

    assertEquals(parent, child.getParent());
    assertTrue(child.getFileName().toString().startsWith(".private-stage-"));
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(child));
  }

  @Test
  void productionOperationsDelegatePosixCreationToTheExactNioPrimitive() throws IOException {
    assumePosix();
    FakeFilesystemAccess filesystem = privateAclCreationFilesystem();
    PrivateOutputDirectoryCreation.ProductionOperations operations =
        new PrivateOutputDirectoryCreation.ProductionOperations(
            filesystem,
            false,
            ignored -> {
              throw new AssertionError(
                  "the POSIX creation proof must not call the Windows creator");
            });
    Path directory = temporaryDirectory.resolve("created-with-posix-attributes");

    operations.createPosixDirectory(
        directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY));

    assertTrue(Files.isDirectory(directory));
    assertEquals(OWNER_ONLY_DIRECTORY, Files.getPosixFilePermissions(directory));
  }

  private static FakeFilesystemAccess privateAclCreationFilesystem() {
    FakeFilesystemAccess filesystem =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path directory : List.of(Path.of("/"), ROOT, PARENT)) {
      filesystem.putAcl(directory, ownerOnlyAcl);
    }
    filesystem.markAclSupported(OUTPUT);
    return filesystem;
  }

  private static FakeFilesystemAccess privatePosixCreationFilesystem() {
    FakeFilesystemAccess filesystem =
        PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER);
    for (Path directory : List.of(Path.of("/"), ROOT, PARENT)) {
      filesystem.putPosix(directory, OWNER_ONLY_DIRECTORY);
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

  private void assumePosix() {
    assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
  }
}
