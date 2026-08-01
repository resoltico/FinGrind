package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

  /** Creates one exact immutable witness record without adopting a pre-existing entry. */
  @FunctionalInterface
  interface SecureRecordCreator {
    /** Creates the selected absent record with the exact immutable witness header. */
    void create(Path path, byte[] expectedMagic) throws IOException;
  }

  /** Makes one witness-parent directory mutation durable before validation continues. */
  @FunctionalInterface
  interface ParentDirectoryForcer {
    /** Forces the selected witness parent directory after a durable name mutation. */
    void force(Path parentDirectory) throws IOException;
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

    AcquisitionFailure(Requirement requirement, Throwable cause) {
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
    private final List<AdmittedWitness> witnesses;
    private boolean closed;

    Set(List<AdmittedWitness> witnesses) {
      this.witnesses = List.copyOf(witnesses);
    }

    /** Revalidates one exact admitted target's primitive witness at the closest final boundary. */
    void requireCurrent(Path targetPath, PrimitiveKind primitiveKind) throws IOException {
      requireOpen();
      Path checkedTargetPath =
          Objects.requireNonNull(targetPath, "targetPath").toAbsolutePath().normalize();
      SqlitePublicationCapabilityWitnessKey key =
          SqlitePublicationCapabilityWitnessKey.forTarget(checkedTargetPath, primitiveKind);
      AdmittedWitness witness = witnessFor(key);
      if (witness == null
          || !SqliteProtectedBookPathIdentity.containsNormalizedSpelling(
              witness.admittedTargets(), checkedTargetPath)) {
        throw new IOException(
            "The FinGrind publication capability witness was not admitted for the exact target "
                + checkedTargetPath
                + " and "
                + primitiveKind
                + ".");
      }
      witness.record().requireCurrent();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      SqliteRuntimeCloseSequence.closeAllReverse(
          witnesses.stream()
              .map(witness -> (SqliteRuntimeCloseSequence.CloseAction) witness.record()::close)
              .toList());
    }

    private void requireOpen() {
      if (closed) {
        throw new IllegalStateException(
            "The FinGrind publication capability witness set is closed.");
      }
    }

    private @Nullable AdmittedWitness witnessFor(SqlitePublicationCapabilityWitnessKey key) {
      return witnesses.stream()
          .filter(witness -> witness.key().equals(key))
          .findFirst()
          .orElse(null);
    }

    /** One retained witness record and the exact targets admitted to use it. */
    record AdmittedWitness(
        SqlitePublicationCapabilityWitnessKey key,
        SqlitePublicationCapabilityWitnessRecord record,
        List<Path> admittedTargets) {
      AdmittedWitness {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(record, "record");
        admittedTargets = List.copyOf(admittedTargets);
      }
    }
  }

  /** Acquires every distinct parent-plus-primitive witness in deterministic order. */
  static Set acquire(
      List<Requirement> requirements,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover)
      throws AcquisitionFailure {
    return acquire(
        requirements,
        linkCreator,
        mover,
        SqliteCoordinationControlFiles::createAtomicallySecureRecord,
        AttestationDirectoryDurability::force);
  }

  static Set acquire(
      List<Requirement> requirements,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover mover,
      SecureRecordCreator recordCreator,
      ParentDirectoryForcer parentDirectoryForcer)
      throws AcquisitionFailure {
    return SqlitePublicationCapabilityWitnessAcquirer.acquire(
        requirements, linkCreator, mover, recordCreator, parentDirectoryForcer);
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
}
