package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Exercises platform selection without forging host operating-system properties. */
class PrivateOutputFileTest {
  private static final Path ARTIFACT = Path.of("private-output.fg").toAbsolutePath();

  @Test
  void selectsPosixCreationBeforeAnyAclFallback() throws IOException {
    FakeOperations operations = new FakeOperations(true, true, false);

    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFileAdmission.createNew(ARTIFACT, operations)) {
      assertTrue(opened.created());
    }

    assertEquals(1, operations.posixCreates);
    assertEquals(0, operations.windowsCreates);
    assertEquals(1, operations.secureParentChecks);
  }

  @Test
  void selectsTheWindowsNativeTransportOnlyForAnAclWindowsFilesystem() throws IOException {
    FakeOperations operations = new FakeOperations(false, true, true);

    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFileAdmission.createNew(ARTIFACT, operations)) {
      assertTrue(opened.created());
    }

    assertEquals(0, operations.posixCreates);
    assertEquals(1, operations.windowsCreates);
  }

  @Test
  void rejectsAclOnlyCreationOutsideWindowsRatherThanCreatingAndRepairing() {
    FakeOperations operations = new FakeOperations(false, true, false);

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> PrivateOutputFileAdmission.createNew(ARTIFACT, operations));

    assertEquals(PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, failure.kind());
    assertEquals(0, operations.posixCreates);
    assertEquals(0, operations.windowsCreates);
  }

  @Test
  void creationRejectsAFileSystemWithoutAnExactOwnerOnlyTransport() {
    FakeOperations operations = new FakeOperations(false, false, true);

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> PrivateOutputFileAdmission.createNew(ARTIFACT, operations));

    assertEquals(PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, failure.kind());
    assertEquals(0, operations.posixCreates);
    assertEquals(0, operations.windowsCreates);
  }

  @Test
  void existingAdmissionUsesTheRequestedAccessThroughTheSelectedTransport() throws IOException {
    FakeOperations operations = new FakeOperations(false, true, true);

    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFileAdmission.openExisting(
            ARTIFACT, PrivateOutputFile.Access.READ_ONLY, operations)) {
      assertFalse(opened.created());
    }

    assertEquals(1, operations.windowsOpens);
    assertEquals(PrivateOutputFile.Access.READ_ONLY, operations.windowsAccess);
  }

  @Test
  void existingAdmissionUsesTheRequestedAccessThroughThePosixTransport() throws IOException {
    FakeOperations operations = new FakeOperations(true, true, true);

    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFileAdmission.openExisting(
            ARTIFACT, PrivateOutputFile.Access.READ_WRITE, operations)) {
      assertFalse(opened.created());
    }

    assertEquals(1, operations.posixOpens);
    assertEquals(PrivateOutputFile.Access.READ_WRITE, operations.posixAccess);
    assertEquals(0, operations.windowsOpens);
  }

  @Test
  void existingAdmissionRejectsFilesystemsWithoutAnExactOwnerOnlyTransport() {
    FakeOperations operations = new FakeOperations(false, false, true);

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () ->
                PrivateOutputFileAdmission.openExisting(
                    ARTIFACT, PrivateOutputFile.Access.READ_ONLY, operations));

    assertEquals(PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, failure.kind());
    assertEquals(0, operations.posixOpens);
    assertEquals(0, operations.windowsOpens);
  }

  @Test
  void existingAdmissionRejectsAclOnlyFilesystemsOutsideWindows() {
    FakeOperations operations = new FakeOperations(false, true, false);

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () ->
                PrivateOutputFileAdmission.openExisting(
                    ARTIFACT, PrivateOutputFile.Access.READ_ONLY, operations));

    assertEquals(PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, failure.kind());
    assertEquals(0, operations.posixOpens);
    assertEquals(0, operations.windowsOpens);
  }

  @Test
  void existingOwnerOnlyAdmissionClosesExactlyOnceAndPreservesCloseFailures() throws IOException {
    FakeOperations acceptedOperations = new FakeOperations(true, false, false);

    PrivateOutputFileAdmission.requireExistingOwnerOnly(
        ARTIFACT, PrivateOutputFile.Access.READ_ONLY, acceptedOperations);

    assertEquals(1, acceptedOperations.posixOpens);
    assertEquals(
        1, Objects.requireNonNull(acceptedOperations.openedFile, "openedFile").closeCount());

    FakeOperations failingOperations = new FakeOperations(true, false, false);
    IOException closeFailure = new IOException("simulated close failure");
    failingOperations.openedCloseFailure = closeFailure;

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputFileAdmission.requireExistingOwnerOnly(
                    ARTIFACT, PrivateOutputFile.Access.READ_ONLY, failingOperations));

    assertSame(closeFailure, failure);
    assertEquals(1, failingOperations.posixOpens);
    assertEquals(
        1, Objects.requireNonNull(failingOperations.openedFile, "openedFile").closeCount());
  }

  @Test
  void existingOwnerOnlyAdmissionRejectsANullPlatformOpenResult() {
    FakeOperations operations = new FakeOperations(true, false, false);
    operations.returnNullOpenedFile = true;

    assertThrows(
        NullPointerException.class,
        () ->
            PrivateOutputFileAdmission.requireExistingOwnerOnly(
                ARTIFACT, PrivateOutputFile.Access.READ_ONLY, operations));

    assertEquals(1, operations.posixOpens);
  }

  @Test
  void operatingSystemRecognitionAcceptsWindowsNamesAndRejectsOtherNames() {
    assertTrue(PrivateOutputFile.isWindows("Windows Server 2025"));
    assertTrue(PrivateOutputFile.isWindows("WINDOWS"));
    assertFalse(PrivateOutputFile.isWindows("Mac OS X"));
    assertFalse(PrivateOutputFile.isWindows("Linux"));
  }

  @Test
  void creationRejectsAFilesystemRootBecauseItCannotHaveAPrivateParent() {
    Path root = Objects.requireNonNull(ARTIFACT.getRoot(), "artifact root");
    FakeOperations operations = new FakeOperations(true, false, false);

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> PrivateOutputFileAdmission.createNew(root, operations));

    assertEquals(PrivateOutputFile.ViolationKind.MISSING_PARENT, failure.kind());
    assertEquals(root, failure.file());
    assertEquals(0, operations.secureParentChecks);
  }

  @Test
  void ownerOnlyFileViolationsExposeTheAffectedArtifactPath() {
    PrivateOutputFile.OwnerOnlyFileViolation failure =
        PrivateOutputFile.ownerOnlyRequired(ARTIFACT);

    assertEquals(ARTIFACT, failure.file());
    assertEquals(PrivateOutputFile.ViolationKind.OWNER_ONLY_REQUIRED, failure.kind());
  }

  @Test
  void lockRangesRejectNegativeZeroAndOverflowingIntervals() {
    assertThrows(IllegalArgumentException.class, () -> PrivateOutputFile.requireLockRange(-1L, 1L));
    assertThrows(IllegalArgumentException.class, () -> PrivateOutputFile.requireLockRange(0L, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () -> PrivateOutputFile.requireLockRange(Long.MAX_VALUE, 1L));

    PrivateOutputFile.requireLockRange(Long.MAX_VALUE - 1L, 1L);
  }

  @Test
  void normalizesPrivateParentAdmissionFailuresIntoTheFileCapabilityVocabulary() {
    FakeOperations operations = new FakeOperations(true, false, false);
    operations.parentViolation =
        new PrivateOutputDirectory.Violation(
            PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED, "simulated parent refusal");

    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> PrivateOutputFileAdmission.createNew(ARTIFACT, operations));

    assertEquals(PrivateOutputFile.ViolationKind.PARENT_OWNER_ONLY_REQUIRED, failure.kind());
    assertSame(operations.parentViolation, failure.getCause());
  }

  /** Test platform boundary that records the selected owner-only file mechanism. */
  private static final class FakeOperations implements PrivateOutputFile.Operations {
    private final boolean posix;
    private final boolean acl;
    private final boolean windows;
    private int secureParentChecks;
    private int posixCreates;
    private int windowsCreates;
    private int posixOpens;
    private int windowsOpens;
    private PrivateOutputFile.@Nullable Access posixAccess;
    private PrivateOutputFile.@Nullable Access windowsAccess;
    private PrivateOutputDirectory.@Nullable Violation parentViolation;
    private @Nullable IOException openedCloseFailure;
    private @Nullable FakeOpenedFile openedFile;
    private boolean returnNullOpenedFile;

    private FakeOperations(boolean posix, boolean acl, boolean windows) {
      this.posix = posix;
      this.acl = acl;
      this.windows = windows;
    }

    @Override
    public boolean supportsPosix(Path file) {
      return posix;
    }

    @Override
    public boolean supportsAcl(Path file) {
      return acl;
    }

    @Override
    public boolean isWindows() {
      return windows;
    }

    @Override
    public void requireSecureParent(Path file) throws IOException {
      secureParentChecks++;
      if (parentViolation != null) {
        throw parentViolation;
      }
    }

    @Override
    public PrivateOutputFile.OpenedFile createNewPosix(Path file) {
      posixCreates++;
      return openedFile(true);
    }

    @Override
    public PrivateOutputFile.OpenedFile createNewWindows(Path file) {
      windowsCreates++;
      return openedFile(true);
    }

    @Override
    public PrivateOutputFile.OpenedFile openExistingPosix(
        Path file, PrivateOutputFile.Access access) {
      posixOpens++;
      posixAccess = access;
      return openedFile(false);
    }

    @Override
    public PrivateOutputFile.OpenedFile openExistingWindows(
        Path file, PrivateOutputFile.Access access) {
      windowsOpens++;
      windowsAccess = access;
      return openedFile(false);
    }

    private FakeOpenedFile openedFile(boolean created) {
      if (returnNullOpenedFile) {
        return nullOf();
      }
      FakeOpenedFile opened = new FakeOpenedFile(created, openedCloseFailure);
      openedFile = opened;
      return opened;
    }
  }

  /** Minimal retained channel used to prove platform-selection behavior. */
  private static final class FakeOpenedFile implements PrivateOutputFile.OpenedFile {
    private final boolean created;
    private final @Nullable IOException closeFailure;
    private int closeCount;

    private FakeOpenedFile(boolean created, @Nullable IOException closeFailure) {
      this.created = created;
      this.closeFailure = closeFailure;
    }

    @Override
    public boolean created() {
      return created;
    }

    @Override
    public int read(ByteBuffer destination) {
      return -1;
    }

    @Override
    public int write(ByteBuffer source) {
      int written = source.remaining();
      source.position(source.limit());
      return written;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public long size() {
      return 0L;
    }

    @Override
    public void truncate(long size) {}

    @Override
    public void position(long position) {}

    @Override
    public void force() {}

    @Override
    public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size) {
      return () -> {};
    }

    @Override
    public String physicalObjectIdentity() {
      return "fake";
    }

    @Override
    public void close() throws IOException {
      closeCount++;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }

    private int closeCount() {
      return closeCount;
    }
  }
}
