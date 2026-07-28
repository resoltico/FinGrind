package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Retained, bounded evidence that one final-name primitive was observed on one physical parent
 * directory.
 *
 * <p>The witness proves only the observed provider behavior used to create it. It cannot promise
 * that a later syscall will succeed, and Java NIO cannot eliminate an adversarial same-owner rename
 * outside FinGrind's cooperative directory-lock protocol. Every final primitive therefore
 * revalidates the immutable witness immediately before it runs.
 */
final class SqlitePublicationCapabilityWitness {
  private static final String PROTOCOL = "FinGrind-publication-capability-v2";
  private static final String BASE_PREFIX = ".fingrind-publication-capability-v2-";
  private static final ReentrantLock LOCAL_ACQUISITION_LOCK = new ReentrantLock();

  private SqlitePublicationCapabilityWitness() {}

  /** Final-name filesystem primitive whose support is attested by one retained witness. */
  enum PrimitiveKind {
    NO_REPLACE_LINK("no-replace-link"),
    ATOMIC_REPLACE("atomic-replace");

    private final String token;

    PrimitiveKind(String token) {
      this.token = token;
    }

    String token() {
      return token;
    }
  }

  record Requirement(Path targetPath, PrimitiveKind primitiveKind) {
    Requirement {
      targetPath = Objects.requireNonNull(targetPath, "targetPath").toAbsolutePath().normalize();
      Objects.requireNonNull(primitiveKind, "primitiveKind");
    }

    static Requirement noReplace(Path targetPath) {
      return new Requirement(targetPath, PrimitiveKind.NO_REPLACE_LINK);
    }

    static Requirement atomicReplace(Path targetPath) {
      return new Requirement(targetPath, PrimitiveKind.ATOMIC_REPLACE);
    }
  }

  /**
   * Identifies the exact admitted target whose primitive could not establish its retained witness.
   *
   * <p>The caller translates this before it reaches a public maintenance result, preserving the
   * target role rather than reporting a generic staging failure.
   */
  static final class AcquisitionFailure extends IOException {
    private static final long serialVersionUID = 1L;

    private final transient Requirement requirement;

    private AcquisitionFailure(Requirement requirement, Throwable cause) {
      super(
          "Failed to establish the retained FinGrind publication capability witness for "
              + Objects.requireNonNull(requirement, "requirement").targetPath()
              + " and "
              + requirement.primitiveKind()
              + ".",
          Objects.requireNonNull(cause, "cause"));
      this.requirement = requirement;
    }

    Requirement requirement() {
      return requirement;
    }
  }

  /** Holds deterministic, de-duplicated witnesses until the enclosing publication closes. */
  static final class Set implements AutoCloseable {
    private final Map<WitnessKey, Witness> witnesses;
    private final Map<WitnessKey, List<Path>> admittedTargets;
    private boolean closed;

    private Set(
        Map<WitnessKey, Witness> witnesses,
        Map<WitnessKey, List<Requirement>> admittedRequirements) {
      this.witnesses = Collections.unmodifiableMap(new ConcurrentHashMap<>(witnesses));
      Map<WitnessKey, List<Path>> targets = new ConcurrentHashMap<>();
      for (Map.Entry<WitnessKey, List<Requirement>> entry : admittedRequirements.entrySet()) {
        targets.put(
            entry.getKey(), entry.getValue().stream().map(Requirement::targetPath).toList());
      }
      admittedTargets = Collections.unmodifiableMap(targets);
    }

    /** Revalidates one exact admitted target's primitive witness at the closest final boundary. */
    void requireCurrent(Path targetPath, PrimitiveKind primitiveKind) throws IOException {
      requireOpen();
      Path checkedTargetPath =
          Objects.requireNonNull(targetPath, "targetPath").toAbsolutePath().normalize();
      WitnessKey key = keyFor(checkedTargetPath, primitiveKind);
      List<Path> targets = admittedTargets.get(key);
      if (targets == null
          || !SqliteProtectedBookPathIdentity.containsNormalizedSpelling(
              targets, checkedTargetPath)) {
        throw new IOException(
            "The FinGrind publication capability witness was not admitted for the exact target "
                + checkedTargetPath
                + " and "
                + primitiveKind
                + ".");
      }
      Objects.requireNonNull(witnesses.get(key), "admitted witness").requireCurrent();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      SqliteRuntimeCloseSequence.closeAllReverse(
          witnesses.values().stream().map(witness -> (SqliteRuntimeCloseSequence.CloseAction) witness::close).toList());
    }

    private void requireOpen() {
      if (closed) {
        throw new IllegalStateException(
            "The FinGrind publication capability witness set is closed.");
      }
    }
  }

  /** Acquires every distinct parent-plus-primitive witness in deterministic order. */
  static Set acquire(
      List<Requirement> requirements,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws IOException {
    List<Requirement> checkedRequirements = List.copyOf(requirements);
    Objects.requireNonNull(linkCreator, "linkCreator");
    Objects.requireNonNull(mover, "mover");
    Map<WitnessKey, List<Requirement>> distinct = new ConcurrentHashMap<>();
    for (Requirement requirement : checkedRequirements) {
      Requirement checkedRequirement = Objects.requireNonNull(requirement, "requirement");
      try {
        requirementsFor(
                distinct,
                keyFor(checkedRequirement.targetPath(), checkedRequirement.primitiveKind()))
            .add(checkedRequirement);
      } catch (IOException | RuntimeException failure) {
        throw new AcquisitionFailure(checkedRequirement, failure);
      }
    }
    List<Map.Entry<WitnessKey, List<Requirement>>> ordered = new ArrayList<>(distinct.entrySet());
    ordered.sort(Map.Entry.comparingByKey());
    Map<WitnessKey, Witness> acquired = new ConcurrentHashMap<>();
    try {
      return acquireAll(ordered, acquired, distinct, linkCreator, mover);
    } catch (IOException | RuntimeException failure) {
      SqliteRuntimeCloseSequence.closeAllReversePreservingFailure(
          acquired.values().stream()
              .map(witness -> (SqliteRuntimeCloseSequence.CloseAction) witness::close)
              .toList(),
          failure);
      throw failure;
    }
  }

  private static Set acquireAll(
      List<Map.Entry<WitnessKey, List<Requirement>>> ordered,
      Map<WitnessKey, Witness> acquired,
      Map<WitnessKey, List<Requirement>> distinct,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws IOException {
    for (Map.Entry<WitnessKey, List<Requirement>> entry : ordered) {
      Requirement representative = entry.getValue().getFirst();
      try {
        acquired.put(
            entry.getKey(),
            acquireOne(
                entry.getKey(),
                entry.getValue(),
                AcquisitionScope.admittedTargetPaths(entry.getKey(), distinct),
                linkCreator,
                mover));
      } catch (IOException | RuntimeException failure) {
        throw new AcquisitionFailure(representative, failure);
      }
    }
    return new Set(acquired, distinct);
  }

  /** Acquires the exact book and generated-secret primitive facts for one staged pair. */
  static Set acquirePair(
      Path bookTargetPath,
      PrimitiveKind bookPrimitiveKind,
      Path secretTargetPath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws IOException {
    return acquire(
        List.of(
            new Requirement(bookTargetPath, bookPrimitiveKind),
            Requirement.noReplace(secretTargetPath)),
        linkCreator,
        mover);
  }

  /**
   * Converts a retained-witness acquisition failure into its precise caller path failure when the
   * provider rejected the required final-name primitive.
   */
  static @Nullable SqliteCallerPathContractException callerPathFailure(
      AcquisitionFailure failure, SqliteCallerPathFailure noReplaceFailure) {
    AcquisitionFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    Requirement requirement = checkedFailure.requirement();
    Throwable cause = Objects.requireNonNull(checkedFailure.getCause(), "failure cause");
    if (cause instanceof SqliteCallerPathContractException pathFailure) {
      return new SqliteCallerPathContractException(
          requirement.targetPath(),
          pathFailure.pathFailure(),
          Objects.requireNonNull(pathFailure.getMessage(), "pathFailure message"),
          pathFailure);
    }
    if (!signalsUnsupportedPrimitive(requirement.primitiveKind(), cause)) {
      return null;
    }
    SqliteCallerPathFailure pathFailure =
        switch (requirement.primitiveKind()) {
          case NO_REPLACE_LINK -> Objects.requireNonNull(noReplaceFailure, "noReplaceFailure");
          case ATOMIC_REPLACE -> SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED;
        };
    String requiredPrimitive =
        switch (pathFailure) {
          case ATOMIC_SECRET_PUBLICATION_UNSUPPORTED ->
              "atomic no-replace generated-secret publication";
          case ATOMIC_BOOK_PUBLICATION_UNSUPPORTED ->
              "atomic no-replace protected-book publication";
          case ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED -> "atomic protected-book replacement";
          default ->
              throw new IllegalArgumentException(
                  "Unsupported publication capability failure vocabulary: " + pathFailure + ".");
        };
    return new SqliteCallerPathContractException(
        requirement.targetPath(),
        pathFailure,
        "The FinGrind target requires " + requiredPrimitive + ": " + requirement.targetPath() + ".",
        cause);
  }

  private static boolean signalsUnsupportedPrimitive(PrimitiveKind primitiveKind, Throwable cause) {
    if (cause instanceof UnsupportedOperationException) {
      return true;
    }
    if (primitiveKind == PrimitiveKind.ATOMIC_REPLACE
        && cause instanceof AtomicMoveNotSupportedException) {
      return true;
    }
    if (cause instanceof FileSystemException filesystemException) {
      String reason = filesystemException.getReason();
      return reason != null && reason.toLowerCase(Locale.ROOT).contains("not supported");
    }
    return false;
  }

  private static Witness acquireOne(
      WitnessKey key,
      List<Requirement> requirements,
      List<Path> admittedTargetPaths,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws IOException {
    LOCAL_ACQUISITION_LOCK.lock();
    try {
      List<Requirement> checkedRequirements = List.copyOf(requirements);
      Requirement representative = checkedRequirements.getFirst();
      Path controlPath = controlPath(key);
      byte[] controlMagic = magic(key, "control");
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
                  SqliteCoordinationControlFiles.maintenanceLockPosition(),
                  SqliteCoordinationControlFiles.maintenanceLockLength()));
      if (control == null) {
        closeParentLease(parentLease);
        throw new IOException(
            "One FinGrind publication capability witness is busy: " + controlPath + ".");
      }
      Witness witness =
          new Witness(key, representative.targetPath(), control.transfer(), parentLease.transfer());
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

  private static void closeParentLease(SqliteOwnedHeldLease parentLease) {
    Objects.requireNonNull(parentLease, "parentLease").release();
  }

  private static List<Requirement> requirementsFor(
      Map<WitnessKey, List<Requirement>> requirementsByKey, WitnessKey key) {
    return requirementsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
  }

  private static WitnessKey keyFor(Path targetPath, PrimitiveKind primitiveKind)
      throws IOException {
    Path checkedTarget =
        Objects.requireNonNull(targetPath, "targetPath").toAbsolutePath().normalize();
    Path parent = Objects.requireNonNull(checkedTarget.getParent(), "targetPath parent");
    SqliteBookFileSecurity.requireExistingSecureParentDirectory(checkedTarget);
    Path canonicalParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
    return new WitnessKey(
        canonicalParent,
        SqliteCoordinationControlFiles.canonicalDirectoryBinding(canonicalParent),
        Objects.requireNonNull(primitiveKind, "primitiveKind"));
  }

  private static Path controlPath(WitnessKey key) {
    return key.parentDirectory().resolve(baseName(key) + ".control");
  }

  private static String baseName(WitnessKey key) {
    return BASE_PREFIX
        + SqliteCoordinationControlFiles.sha256Hex(
            key.parentFingerprint() + "\u0000" + key.primitiveKind().token());
  }

  private static byte[] magic(WitnessKey key, String state) {
    return SqliteCoordinationControlFiles.magic(
        PROTOCOL + "-" + key.primitiveKind().token() + "-" + state, key.parentFingerprint());
  }

  record WitnessKey(
      Path parentDirectory, String parentFingerprint, PrimitiveKind primitiveKind)
      implements Comparable<WitnessKey> {
    WitnessKey {
      Objects.requireNonNull(parentDirectory, "parentDirectory");
      Objects.requireNonNull(parentFingerprint, "parentFingerprint");
      Objects.requireNonNull(primitiveKind, "primitiveKind");
    }

    @Override
    public int compareTo(WitnessKey other) {
      WitnessKey checkedOther = Objects.requireNonNull(other, "other");
      int parentOrder = parentFingerprint.compareTo(checkedOther.parentFingerprint);
      return parentOrder != 0
          ? parentOrder
          : primitiveKind.token().compareTo(checkedOther.primitiveKind.token());
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof WitnessKey witnessKey
          && parentFingerprint.equals(witnessKey.parentFingerprint)
          && primitiveKind == witnessKey.primitiveKind;
    }

    @Override
    public int hashCode() {
      return Objects.hash(parentFingerprint, primitiveKind);
    }
  }

  /** Derives the immutable same-directory admission set before any witness retains a lease. */
  private static final class AcquisitionScope {
    private static List<Path> admittedTargetPaths(
        WitnessKey key, Map<WitnessKey, List<Requirement>> requirementsByKey) {
      List<Path> targets = new ArrayList<>();
      for (Map.Entry<WitnessKey, List<Requirement>> entry : requirementsByKey.entrySet()) {
        if (entry.getKey().parentFingerprint().equals(key.parentFingerprint())) {
          for (Requirement requirement : entry.getValue()) {
            targets.add(requirement.targetPath());
          }
        }
      }
      return List.copyOf(targets);
    }
  }

  /** Retains the exact control and optional parent lease for one verified publication primitive. */
  private static final class Witness implements AutoCloseable {
    private final WitnessKey key;
    private final Path targetPath;
    private final SqliteCoordinationControlFiles.LockedControlFile control;
    private final SqliteHeldLease parentLease;

    private Witness(
        WitnessKey key,
        Path targetPath,
        SqliteCoordinationControlFiles.LockedControlFile control,
        SqliteHeldLease parentLease) {
      this.key = Objects.requireNonNull(key, "key");
      this.targetPath = Objects.requireNonNull(targetPath, "targetPath");
      this.control = Objects.requireNonNull(control, "control");
      this.parentLease = Objects.requireNonNull(parentLease, "parentLease");
    }

    private void establishOrValidate(
        SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
        SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
        throws IOException {
      requireNoLegacyProbeResidue();
      if (key.primitiveKind() == PrimitiveKind.NO_REPLACE_LINK) {
        establishOrValidateNoReplace(linkCreator);
      } else {
        establishOrValidateAtomicReplace(mover);
      }
      requireCurrent();
    }

    private void establishOrValidateNoReplace(
        SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator) throws IOException {
      Path source = statePath("source");
      Path completion = statePath("complete");
      byte[] sourceMagic = magic(key, "no-replace-source");
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
      Path prior = statePath("prior");
      Path replacement = statePath("replacement");
      Path completion = statePath("complete");
      byte[] priorMagic = magic(key, "atomic-replace-prior");
      byte[] replacementMagic = magic(key, "atomic-replace-replacement");
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

    private void requireCurrent() throws IOException {
      keyFor(targetPath, key.primitiveKind());
      requireNoLegacyProbeResidue();
      if (key.primitiveKind() == PrimitiveKind.NO_REPLACE_LINK) {
        byte[] sourceMagic = magic(key, "no-replace-source");
        Path source = statePath("source");
        Path completion = statePath("complete");
        requireExactRecord(source, sourceMagic);
        requireExactRecord(completion, sourceMagic);
        if (!Files.isSameFile(source, completion)) {
          throw invalidWitness("No-replace witness files no longer share one identity.");
        }
      } else {
        byte[] priorMagic = magic(key, "atomic-replace-prior");
        byte[] replacementMagic = magic(key, "atomic-replace-replacement");
        requireExactRecord(statePath("prior"), priorMagic);
        if (Files.exists(statePath("replacement"), LinkOption.NOFOLLOW_LINKS)
            || recordState(statePath("complete"), priorMagic, replacementMagic)
                != RecordState.SECOND) {
          throw invalidWitness("Atomic-replace witness is no longer complete.");
        }
      }
    }

    private void createOrRequireExactRecord(Path path, byte[] expectedMagic) throws IOException {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        requireExactRecord(path, expectedMagic);
        return;
      }
      try {
        SqliteCoordinationControlFiles.createAtomicallySecureRecord(path, expectedMagic);
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

    private Path statePath(String state) {
      return key.parentDirectory().resolve(baseName(key) + "." + state);
    }

    private void forceParent() throws IOException {
      AttestationDirectoryDurability.force(key.parentDirectory());
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

    @Override
    public void close() {
      SqliteRuntimeCloseSequence.closeAll(
          List.of(
              SqliteRuntimeCloseSequence.coordinationControlCloseAction(control),
              parentLease::close));
    }
  }

  /** Relative durable state of the two witness records that form one retained capability fact. */
  private enum RecordState {
    ABSENT,
    FIRST,
    SECOND
  }
}
