package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** Retains and revalidates the durable control records that prove one publication primitive. */
final class SqlitePublicationCapabilityWitnessRecord implements AutoCloseable {
  private static final ReentrantLock LOCAL_ACQUISITION_LOCK = new ReentrantLock();

  private final SqlitePublicationCapabilityWitnessKey key;
  private final Path targetPath;
  private final SqliteCoordinationControlFiles.LockedControlFile control;
  private final SqliteHeldLease parentLease;
  private final SqlitePublicationCapabilityWitness.SecureRecordCreator recordCreator;
  private final SqlitePublicationCapabilityWitness.ParentDirectoryForcer parentDirectoryForcer;

  private SqlitePublicationCapabilityWitnessRecord(
      SqlitePublicationCapabilityWitnessKey key,
      Path targetPath,
      SqliteCoordinationControlFiles.LockedControlFile control,
      SqliteHeldLease parentLease,
      SqlitePublicationCapabilityWitness.SecureRecordCreator recordCreator,
      SqlitePublicationCapabilityWitness.ParentDirectoryForcer parentDirectoryForcer) {
    this.key = Objects.requireNonNull(key, "key");
    this.targetPath = Objects.requireNonNull(targetPath, "targetPath");
    this.control = Objects.requireNonNull(control, "control");
    this.parentLease = Objects.requireNonNull(parentLease, "parentLease");
    this.recordCreator = Objects.requireNonNull(recordCreator, "recordCreator");
    this.parentDirectoryForcer =
        Objects.requireNonNull(parentDirectoryForcer, "parentDirectoryForcer");
  }

  static SqlitePublicationCapabilityWitnessRecord acquire(
      SqlitePublicationCapabilityWitnessKey key,
      SqlitePublicationCapabilityWitness.Requirement representative,
      List<Path> admittedTargetPaths,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover,
      SqlitePublicationCapabilityWitness.SecureRecordCreator recordCreator,
      SqlitePublicationCapabilityWitness.ParentDirectoryForcer parentDirectoryForcer)
      throws IOException {
    LOCAL_ACQUISITION_LOCK.lock();
    try {
      Path controlPath = key.controlPath();
      byte[] controlMagic = key.magic("control");
      SqliteProtectedBookLeaseAcquisition parentAcquisition =
          SqliteBookMaintenanceLease.acquireWithAdmittedScope(
              representative.targetPath(),
              SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
              admittedTargetPaths);
      if (parentAcquisition instanceof SqliteLeaseBusy) {
        throw new IOException(
            "One FinGrind publication capability witness parent directory is busy: "
                + key.parentDirectory()
                + ".");
      }
      SqliteOwnedHeldLease parentLease = SqliteOwnedHeldLease.acquire(parentAcquisition);
      @Nullable SqliteOwnedLockedControlFile control =
          SqliteOwnedLockedControlFile.acquire(
              SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                  controlPath,
                  controlMagic,
                  SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                  SqliteCoordinationControlProtocol.maintenanceLockLength()));
      if (control == null) {
        closeParentLease(parentLease);
        throw new IOException(
            "One FinGrind publication capability witness is busy: " + controlPath + ".");
      }
      SqlitePublicationCapabilityWitnessRecord witness =
          new SqlitePublicationCapabilityWitnessRecord(
              key,
              representative.targetPath(),
              control.transfer(),
              parentLease.transfer(),
              recordCreator,
              parentDirectoryForcer);
      try {
        witness.establishOrValidate(linkCreator, mover);
        return witness;
      } catch (IOException | RuntimeException failure) {
        SqliteRuntimeCloseSequence.closeAllPreservingFailure(List.of(witness::close), failure);
        throw failure;
      }
    } finally {
      LOCAL_ACQUISITION_LOCK.unlock();
    }
  }

  void requireCurrent() throws IOException {
    SqlitePublicationCapabilityWitnessKey.forTarget(targetPath, key.primitiveKind());
    requireNoLegacyProbeResidue();
    if (key.primitiveKind() == SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK) {
      byte[] sourceMagic = key.magic("no-replace-source");
      Path source = key.statePath("source");
      Path completion = key.statePath("complete");
      requireExactRecord(source, sourceMagic);
      requireExactRecord(completion, sourceMagic);
      if (!Files.isSameFile(source, completion)) {
        throw invalidWitness("No-replace witness files no longer share one identity.");
      }
    } else {
      byte[] priorMagic = key.magic("atomic-replace-prior");
      byte[] replacementMagic = key.magic("atomic-replace-replacement");
      requireExactRecord(key.statePath("prior"), priorMagic);
      if (Files.exists(key.statePath("replacement"), LinkOption.NOFOLLOW_LINKS)
          || recordState(key.statePath("complete"), priorMagic, replacementMagic)
              != RecordState.SECOND) {
        throw invalidWitness("Atomic-replace witness is no longer complete.");
      }
    }
  }

  @Override
  public void close() {
    SqliteRuntimeCloseSequence.closeAll(
        List.of(
            SqliteRuntimeCloseSequence.coordinationControlCloseAction(control),
            parentLease::close));
  }

  private void establishOrValidate(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws IOException {
    requireNoLegacyProbeResidue();
    if (key.primitiveKind() == SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK) {
      establishOrValidateNoReplace(linkCreator);
    } else {
      establishOrValidateAtomicReplace(mover);
    }
    requireCurrent();
  }

  private void establishOrValidateNoReplace(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator) throws IOException {
    Path source = key.statePath("source");
    Path completion = key.statePath("complete");
    byte[] sourceMagic = key.magic("no-replace-source");
    if (Files.exists(completion, LinkOption.NOFOLLOW_LINKS)) {
      requireExactRecord(source, sourceMagic);
      requireExactRecord(completion, sourceMagic);
      if (!Files.isSameFile(source, completion)) {
        throw invalidWitness("No-replace completion does not retain the source file identity.");
      }
      return;
    }
    createOrRequireExactRecord(source, sourceMagic);
    try {
      Objects.requireNonNull(linkCreator, "linkCreator").create(completion, source);
      forceParent();
    } catch (FileAlreadyExistsException collision) {
      requireExactRecord(completion, sourceMagic);
    }
    requireExactRecord(source, sourceMagic);
    requireExactRecord(completion, sourceMagic);
    if (!Files.isSameFile(source, completion)) {
      throw invalidWitness("No-replace completion does not retain the source file identity.");
    }
  }

  private void establishOrValidateAtomicReplace(
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover) throws IOException {
    Path prior = key.statePath("prior");
    Path replacement = key.statePath("replacement");
    Path completion = key.statePath("complete");
    byte[] priorMagic = key.magic("atomic-replace-prior");
    byte[] replacementMagic = key.magic("atomic-replace-replacement");
    createOrRequireExactRecord(prior, priorMagic);
    if (Files.notExists(completion, LinkOption.NOFOLLOW_LINKS)) {
      createOrRequireExactRecord(completion, priorMagic);
    }
    RecordState completionState = recordState(completion, priorMagic, replacementMagic);
    if (completionState == RecordState.SECOND) {
      if (Files.exists(replacement, LinkOption.NOFOLLOW_LINKS)) {
        throw invalidWitness("Atomic-replace witness retained an impossible replacement source.");
      }
      return;
    }
    if (completionState != RecordState.FIRST) {
      throw invalidWitness("Atomic-replace witness has an impossible partial state.");
    }
    createOrRequireExactRecord(replacement, replacementMagic);
    Objects.requireNonNull(mover, "mover").move(replacement, completion);
    forceParent();
    if (Files.exists(replacement, LinkOption.NOFOLLOW_LINKS)
        || recordState(completion, priorMagic, replacementMagic) != RecordState.SECOND) {
      throw invalidWitness("Atomic-replace completion did not retain the replacement state.");
    }
  }

  private void createOrRequireExactRecord(Path path, byte[] expectedMagic) throws IOException {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      requireExactRecord(path, expectedMagic);
      return;
    }
    try {
      recordCreator.create(path, expectedMagic);
      forceParent();
    } catch (FileAlreadyExistsException collision) {
      requireExactRecord(path, expectedMagic);
    }
  }

  private void requireExactRecord(Path path, byte[] expectedMagic) throws IOException {
    try {
      SqliteCoordinationControlFiles.requireExistingExactRecord(path, expectedMagic);
    } catch (IOException | RuntimeException invalid) {
      throw invalidWitness(
          "Capability witness record is missing, malformed, or replaced: " + path, invalid);
    }
  }

  private RecordState recordState(Path path, byte[] firstMagic, byte[] secondMagic)
      throws IOException {
    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
      return RecordState.ABSENT;
    }
    try {
      requireExactRecord(path, firstMagic);
      return RecordState.FIRST;
    } catch (IOException firstMismatch) {
      try {
        requireExactRecord(path, secondMagic);
        return RecordState.SECOND;
      } catch (IOException secondMismatch) {
        firstMismatch.addSuppressed(secondMismatch);
        throw invalidWitness(
            "Capability witness record has an unexpected immutable state.", firstMismatch);
      }
    }
  }

  private void requireNoLegacyProbeResidue() throws IOException {
    try (var entries = Files.newDirectoryStream(key.parentDirectory())) {
      for (Path entry : entries) {
        String name = Objects.requireNonNull(entry.getFileName(), "entry fileName").toString();
        if (name.startsWith(".fingrind-book-no-replace-probe-")
            || name.startsWith(".fingrind-no-replace-probe-")
            || name.startsWith(".fingrind-book-replace-probe-")) {
          throw invalidWitness("Retired random publication-capability probe residue is present.");
        }
      }
    }
  }

  private void forceParent() throws IOException {
    parentDirectoryForcer.force(key.parentDirectory());
  }

  private IOException invalidWitness(String message) {
    return invalidWitness(message, null);
  }

  private IOException invalidWitness(String message, @Nullable Throwable cause) {
    String fullMessage =
        "The FinGrind publication capability witness is not valid for "
            + targetPath
            + ": "
            + message;
    return cause == null ? new IOException(fullMessage) : new IOException(fullMessage, cause);
  }

  private static void closeParentLease(SqliteOwnedHeldLease parentLease) {
    Objects.requireNonNull(parentLease, "parentLease").release();
  }

  /** Relative durable state of the two witness records that form one retained capability fact. */
  private enum RecordState {
    ABSENT,
    FIRST,
    SECOND
  }
}
