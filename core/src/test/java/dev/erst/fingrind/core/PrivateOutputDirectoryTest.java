package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.PrivateOutputDirectoryTestFilesystem.FakeFilesystemAccess;
import java.io.IOException;
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

/** Proves private publication admission protects both the final directory and its ancestry. */
class PrivateOutputDirectoryTest {
  private static final Path ROOT = Path.of("/protected-root");
  private static final Path ANCESTOR = ROOT.resolve("reports");
  private static final Path OUTPUT = ANCESTOR.resolve("private");
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal COLLABORATOR = () -> "collaborator";
  private static final UserPrincipal SUPERUSER = () -> "root";
  private static final UserPrincipal WINDOWS_LOCAL_SYSTEM = () -> "local-system";
  private static final UserPrincipal WINDOWS_ADMINISTRATORS = () -> "administrators";
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> READABLE_SEARCHABLE_ANCESTOR =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_EXECUTE);
  private static final Set<PosixFilePermission> SEARCHABLE_OWNER_WRITABLE_ANCESTOR =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_EXECUTE);
  private static final Set<PosixFilePermission> SHARED_WRITABLE_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_WRITE,
          PosixFilePermission.OTHERS_EXECUTE);

  @Test
  void admission_rejectsAPathThatIsNotAnExistingRealDirectory() {
    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectory.requireExistingOwnerOnly(
                    OUTPUT, PrivateOutputDirectoryTestFilesystem.fakeFilesystemAccess(OWNER)));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must be an existing real directory"));
  }

  @Test
  void admission_classifiesAFinalNonDirectoryAsAPathCollision() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.removeDirectory(OUTPUT);
    filesystem.markOther(OUTPUT);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
  }

  @Test
  void admission_acceptsCanonicalAncestryThatAllowsReadAndSearchButNoNonOwnerMutation() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_rejectsAnOrdinaryDifferentPosixAncestorEvenWhenNonOwnersCanOnlySearchIt() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(ANCESTOR, SEARCHABLE_OWNER_WRITABLE_ANCESTOR);
    filesystem.putPosixIdentity(ANCESTOR, COLLABORATOR, 1_001L, false);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("output-directory owner or POSIX superuser"));
  }

  @Test
  void admission_acceptsANonWritablePosixSuperuserAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosixIdentity(ROOT, SUPERUSER, 0L, false);

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void creationAncestry_rejectsNonStickyGroupWritableExistingAncestorBeforeCreation() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.removeDirectory(OUTPUT);
    filesystem.putPosix(ANCESTOR, SHARED_WRITABLE_DIRECTORY);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireCreationAncestry(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("output creation ancestry"));
  }

  @Test
  void creationAncestry_defersStickyAncestorOwnerProofUntilExactDirectoryAdmission() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.removeDirectory(OUTPUT);
    filesystem.putPosix(ROOT, SHARED_WRITABLE_DIRECTORY);
    filesystem.putPosixIdentity(ROOT, SUPERUSER, 0L, true);

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireCreationAncestry(OUTPUT, filesystem));
  }

  @Test
  void creationAncestry_rejectsAnIntermediateLexicalNonDirectoryBeforeCanonicalization() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    Path redirect = ANCESTOR.resolve("redirect");
    filesystem.markOther(redirect);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectory.requireCreationAncestry(
                    redirect.resolve("missing-private"), filesystem));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
  }

  @Test
  void admission_rejectsAnIntermediateLexicalNonDirectoryBeforeCanonicalization() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    Path redirect = ANCESTOR.resolve("redirect");
    Path redirectedOutput = redirect.resolve("private");
    filesystem.markOther(redirect);
    filesystem.putPosix(redirectedOutput, OWNER_ONLY_DIRECTORY);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(redirectedOutput, filesystem));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
  }

  @Test
  void admission_rejectsGroupWritableCanonicalAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(
        ANCESTOR,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("deny group and other mutation"));
  }

  @Test
  void admission_rejectsAGroupWritableAncestorWhenOtherIsNotWritable() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(
        ANCESTOR,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("deny group and other mutation"));
  }

  @Test
  void admission_rejectsAnOtherWritableAncestorWhenGroupIsNotWritable() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(
        ANCESTOR,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("deny group and other mutation"));
  }

  @Test
  void admission_acceptsStickyWritableAncestorWhenItsCanonicalChildBelongsToOutputOwner() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(ROOT, SHARED_WRITABLE_DIRECTORY);
    filesystem.putPosixIdentity(ROOT, OWNER, 1_000L, true);

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_rejectsStickyWritableAncestorOwnedByAnOrdinaryDifferentPrincipal() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(ROOT, SHARED_WRITABLE_DIRECTORY);
    filesystem.putPosixIdentity(ROOT, COLLABORATOR, 1_001L, true);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("POSIX superuser"));
  }

  @Test
  void admission_acceptsStickyWritablePosixSuperuserAncestorWithOutputOwnedChild() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(ROOT, SHARED_WRITABLE_DIRECTORY);
    filesystem.putPosixIdentity(ROOT, SUPERUSER, 0L, true);

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_rejectsStickyWritableAncestorWhenItsCanonicalChildBelongsToAnotherOwner() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(ROOT, SHARED_WRITABLE_DIRECTORY);
    filesystem.putPosixIdentity(ROOT, OWNER, 1_000L, true);
    filesystem.putPosixIdentity(ANCESTOR, SUPERUSER, 0L, false);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("child is owned"));
  }

  @Test
  void admission_rejectsNonOwnerAclMutationWhenHybridFilesystemAlsoReportsPrivatePosixMode() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putAcl(path, ownerOnlyAcl);
    }
    filesystem.putAcl(
        OUTPUT,
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                ownerAllowsAll(),
                allowed(COLLABORATOR, AclEntryPermission.ADD_FILE, AclEntryPermission.EXECUTE))));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("grant directory access only to its owner"));
  }

  @Test
  void admission_acceptsNonOwnerAclSiblingCreationOnAnAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    for (Path path : List.of(ROOT, ANCESTOR, OUTPUT)) {
      filesystem.enableAcl(path);
    }
    filesystem.putAcl(
        ANCESTOR,
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                ownerAllowsAll(),
                allowed(
                    COLLABORATOR,
                    AclEntryPermission.ADD_FILE,
                    AclEntryPermission.ADD_SUBDIRECTORY,
                    AclEntryPermission.EXECUTE))));

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_rejectsNonOwnerAclChildDeletionOnAnAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    for (Path path : List.of(ROOT, ANCESTOR, OUTPUT)) {
      filesystem.enableAcl(path);
    }
    filesystem.putAcl(
        ANCESTOR,
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                ownerAllowsAll(),
                allowed(
                    COLLABORATOR, AclEntryPermission.DELETE_CHILD, AclEntryPermission.EXECUTE))));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("deny non-owner mutation in the output ancestry"));
    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("FINGRIND_ACL_MUTATION_PERMISSIONS=DELETE_CHILD"));
    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("FINGRIND_ACL_MUTATION_PRINCIPAL=OTHER"));
  }

  @Test
  void admission_rejectsAclAncestorOwnedByAnotherPrincipalEvenWithoutAnExplicitMutationGrant() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    PrivateOutputDirectory.AclState outputOwnerAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(Path.of("/"), ROOT, OUTPUT)) {
      filesystem.putAcl(path, outputOwnerAcl);
    }
    filesystem.putAcl(
        ANCESTOR,
        new PrivateOutputDirectory.AclState(
            COLLABORATOR, List.of(allowed(OWNER, AclEntryPermission.LIST_DIRECTORY))));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("owned by the output-directory owner"));
  }

  @Test
  void admission_acceptsOnlyTrustedWindowsPrincipalsAlongsideTheOutputOwner() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    filesystem.trustAclMutationPrincipal(WINDOWS_LOCAL_SYSTEM);
    filesystem.trustAclMutationPrincipal(WINDOWS_ADMINISTRATORS);
    PrivateOutputDirectory.AclState protectedOutputAcl =
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                ownerAllowsAll(),
                allowed(WINDOWS_LOCAL_SYSTEM, AclEntryPermission.values()),
                allowed(WINDOWS_ADMINISTRATORS, AclEntryPermission.values()),
                allowed(COLLABORATOR)));
    for (Path path : List.of(OUTPUT)) {
      filesystem.putAcl(path, protectedOutputAcl);
    }
    PrivateOutputDirectory.AclState trustedAncestorAcl =
        new PrivateOutputDirectory.AclState(
            WINDOWS_LOCAL_SYSTEM,
            List.of(
                allowed(OWNER, AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE),
                allowed(WINDOWS_LOCAL_SYSTEM, AclEntryPermission.values()),
                allowed(WINDOWS_ADMINISTRATORS, AclEntryPermission.values()),
                allowed(
                    COLLABORATOR, AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR)) {
      filesystem.putAcl(path, trustedAncestorAcl);
    }

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_wrapsFilesystemFactFailuresWithoutDiscardingTheirCause() {
    FakeFilesystemAccess ioFailureFilesystem = privatePosixFilesystem();
    IOException ioFailure = new IOException("simulated metadata read failure");
    ioFailureFilesystem.failRealPath(ioFailure);

    PrivateOutputDirectory.Violation ioViolation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, ioFailureFilesystem));
    assertSame(ioFailure, ioViolation.getCause());
    assertTrue(
        Objects.requireNonNull(ioViolation.getMessage(), "I/O violation message")
            .contains("could not establish private owner-only access"));

    FakeFilesystemAccess unsupportedFilesystem = privatePosixFilesystem();
    UnsupportedOperationException unsupported =
        new UnsupportedOperationException("simulated unsupported metadata read");
    unsupportedFilesystem.failRealPath(unsupported);
    PrivateOutputDirectory.Violation unsupportedViolation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, unsupportedFilesystem));
    assertSame(unsupported, unsupportedViolation.getCause());

    FakeFilesystemAccess securityFilesystem = privatePosixFilesystem();
    SecurityException security = new SecurityException("simulated denied metadata read");
    securityFilesystem.failRealPath(security);
    PrivateOutputDirectory.Violation securityViolation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, securityFilesystem));
    assertSame(security, securityViolation.getCause());
  }

  @Test
  void admission_rejectsAnExistingOutputDirectoryWithoutAnAccessControlModel() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("supporting POSIX owner-only permissions or owner-only ACLs"));
  }

  @Test
  void admission_rejectsAnOutputPathWithAMissingLexicalAncestor() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.removeDirectory(ANCESTOR);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must remain an existing real directory"));
  }

  @Test
  void admission_rejectsAnOutputAncestryWithoutAnAccessControlModel() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.clearPosix(ANCESTOR);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("POSIX permissions or ACLs throughout the output ancestry"));
  }

  @Test
  void admission_rejectsAnOutputDirectoryThatItsOwnerCannotWriteAndSearch() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(OUTPUT, Set.of(PosixFilePermission.OWNER_READ));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("owner-writable and owner-searchable"));
  }

  @Test
  void admission_rejectsAnOutputDirectoryThatItsOwnerCannotSearch() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(
        OUTPUT, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("owner-writable and owner-searchable"));
  }

  @Test
  void admission_rejectsAnOutputDirectoryWithNonOwnerPosixReadAccess() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putPosix(
        OUTPUT,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("must grant no directory access to group or other principals"));
  }

  @Test
  void admission_rejectsAnAclOutputDirectoryWhoseOwnerCannotWriteAndTraverse() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    filesystem.putAcl(
        OUTPUT,
        new PrivateOutputDirectory.AclState(
            OWNER, List.of(allowed(OWNER, AclEntryPermission.READ_DATA))));

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("owner directory traversal and write access"));
  }

  @Test
  void admission_acceptsAclOnlyDirectoryFactsWhenNoNonOwnerCanMutate() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(OWNER, List.of(ownerAllowsAll()));
    for (Path path : List.of(Path.of("/"), ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putAcl(path, ownerOnlyAcl);
    }

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void admission_ignoresDenyAclEntriesBecauseTheyDoNotGrantDirectoryAccess() {
    FakeFilesystemAccess filesystem = privatePosixFilesystem();
    PrivateOutputDirectory.AclState ownerOnlyAcl =
        new PrivateOutputDirectory.AclState(
            OWNER,
            List.of(
                denied(
                    COLLABORATOR, AclEntryPermission.ADD_FILE, AclEntryPermission.ADD_SUBDIRECTORY),
                ownerAllowsAll()));
    for (Path path : List.of(ROOT, ANCESTOR, OUTPUT)) {
      filesystem.putAcl(path, ownerOnlyAcl);
    }

    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(OUTPUT, filesystem));
  }

  @Test
  void posixDirectoryIdentity_rejectsANegativeUnixUserId() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PrivateOutputDirectory.PosixDirectoryIdentity(OWNER, -1L, false));

    assertEquals("unixUserId must be non-negative.", exception.getMessage());
  }

  private static FakeFilesystemAccess privatePosixFilesystem() {
    FakeFilesystemAccess filesystem = lexicalFilesystem();
    filesystem.putPosix(Path.of("/"), READABLE_SEARCHABLE_ANCESTOR);
    filesystem.putPosix(ROOT, READABLE_SEARCHABLE_ANCESTOR);
    filesystem.putPosix(ANCESTOR, READABLE_SEARCHABLE_ANCESTOR);
    filesystem.putPosix(OUTPUT, OWNER_ONLY_DIRECTORY);
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
    return allowed(OWNER, AclEntryPermission.values());
  }

  private static AclEntry allowed(UserPrincipal principal, AclEntryPermission... permissions) {
    return aclEntry(AclEntryType.ALLOW, principal, permissions);
  }

  private static AclEntry denied(UserPrincipal principal, AclEntryPermission... permissions) {
    return aclEntry(AclEntryType.DENY, principal, permissions);
  }

  private static AclEntry aclEntry(
      AclEntryType type, UserPrincipal principal, AclEntryPermission... permissions) {
    return AclEntry.newBuilder()
        .setType(type)
        .setPrincipal(principal)
        .setPermissions(permissions)
        .build();
  }
}
