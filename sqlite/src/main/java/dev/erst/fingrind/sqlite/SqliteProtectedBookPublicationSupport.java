package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/** Owns atomic no-replace and selected-replacement publication of staged book artifacts. */
final class SqliteProtectedBookPublicationSupport {
  /** Creates one final hard link to a staged artifact without allowing replacement. */
  @FunctionalInterface
  interface NoReplaceLinkCreator {
    /** Creates one final link to the staged artifact. */
    void create(Path finalPath, Path stagedPath) throws IOException;
  }

  /** Atomically replaces one declared book target with its verified staged counterpart. */
  @FunctionalInterface
  interface AtomicBookMover {
    /** Moves the staged book onto its final target. */
    void move(Path stagedPath, Path finalPath) throws IOException;
  }

  /** Receives the exact boundary immediately before one final pair-member primitive is invoked. */
  @FunctionalInterface
  interface FinalMemberPublicationAttempt {
    /** Marks one final pair member as attempted. */
    void markAttempted();
  }

  /** Revalidates one fact at the closest Java boundary before a final filesystem primitive. */
  @FunctionalInterface
  interface FinalMemberPublicationGuard {
    /** Verifies the final-member precondition immediately before publication. */
    void requireCurrent() throws IOException;
  }

  /** Identifies the final member whose guard rejected before its primitive ran. */
  enum FinalMember {
    SECRET,
    BOOK
  }

  /** Proves a guard rejected before the associated final filesystem primitive was attempted. */
  static final class FinalMemberPublicationGuardRejectedException extends IOException {
    private static final long serialVersionUID = 1L;

    private final FinalMember member;

    private FinalMemberPublicationGuardRejectedException(FinalMember member, IOException cause) {
      super(Objects.requireNonNull(cause, "cause").getMessage(), cause);
      this.member = Objects.requireNonNull(member, "member");
    }

    FinalMember member() {
      return member;
    }
  }

  /** Names each pair-publication directory mutation that must be force-confirmed. */
  enum PairPublicationDurabilityStep {
    /** The staged member bytes and their owned-stage records were force-confirmed. */
    STAGED_MEMBER_DURABILITY,
    /** Every parent received the immutable shared pair-stage claim. */
    PAIR_STAGE_CLAIM,
    /** Every parent received the immutable recovery intent before authorization was published. */
    RECOVERY_INTENT,
    /** The operation-bound recovery record was created before any final member attempt. */
    RECOVERY_RECORD,
    /** A verified no-final-member prepublication outcome was durably retained for exact retry. */
    PREPUBLICATION_RETENTION,
    /** The generated-secret final name was created or re-confirmed. */
    GENERATED_SECRET_PUBLICATION,
    /** The final book name was created, replaced, or re-confirmed. */
    BOOK_PUBLICATION,
    /** A complete verified pair remains durably represented by immutable recovery evidence. */
    RECOVERY_TERMINAL_RETENTION
  }

  /** Forces one pair-publication parent directory through an injectable durability boundary. */
  @FunctionalInterface
  interface PairDirectoryForcer {
    /** Makes the selected directory mutation durable. */
    void force(PairPublicationDurabilityStep step, Path parentDirectory) throws IOException;
  }

  private SqliteProtectedBookPublicationSupport() {}

  static PairDirectoryForcer productionPairDirectoryForcer() {
    return (ignoredStep, parentDirectory) ->
        AttestationDirectoryDurability.force(
            Objects.requireNonNull(parentDirectory, "parentDirectory"));
  }

  static void moveReplacing(Path source, Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Creates one unique hard-link bridge for an atomic replacement without consuming its stage.
   *
   * <p>The bridge is intentionally never removed by a failure path. If the later atomic move does
   * not happen, it is inert opaque residue beside the retained stage; unlinking it would recreate
   * the same-owner replacement race that stage retention excludes. A successful move consumes only
   * the bridge, leaving the recorded stage available as durable publication evidence.
   */
  static Path createReplacementBridgeRetainingStage(
      Path stagedPath, Path finalPath, NoReplaceLinkCreator linkCreator) throws IOException {
    Path checkedStage =
        Objects.requireNonNull(stagedPath, "stagedPath").toAbsolutePath().normalize();
    Path checkedFinal = Objects.requireNonNull(finalPath, "finalPath").toAbsolutePath().normalize();
    Path parent = Objects.requireNonNull(checkedFinal.getParent(), "finalPath parent");
    NoReplaceLinkCreator checkedLinkCreator = Objects.requireNonNull(linkCreator, "linkCreator");
    for (int attempt = 0; attempt < 8; attempt++) {
      Path bridge = parent.resolve(".fingrind-replacement-" + UUID.randomUUID() + ".stage");
      if (createBridgeIfAbsent(checkedLinkCreator, bridge, checkedStage)) {
        return bridge;
      }
    }
    throw new IOException(
        "Unable to reserve a unique retained-stage replacement bridge beside "
            + checkedFinal
            + ".");
  }

  /** Creates one bridge only when its fresh opaque name remains absent. */
  private static boolean createBridgeIfAbsent(
      NoReplaceLinkCreator linkCreator, Path bridge, Path stagedPath) throws IOException {
    try {
      linkCreator.create(bridge, stagedPath);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      return false;
    }
  }

  /** Publishes a staged sibling without deleting it until its paired operation reaches commit. */
  static void publishRetainingStage(Path stagedPath, Path finalPath) throws IOException {
    publishRetainingStage(stagedPath, finalPath, Files::createLink);
  }

  /** Publishes one staged sibling through an explicit no-replace owner. */
  static void publishRetainingStage(
      Path stagedPath, Path finalPath, NoReplaceLinkCreator linkCreator) throws IOException {
    Objects.requireNonNull(stagedPath, "stagedPath");
    Objects.requireNonNull(finalPath, "finalPath");
    Objects.requireNonNull(linkCreator, "linkCreator").create(finalPath, stagedPath);
  }

  /** Wraps one no-replace link so the attempt fact is set at the final filesystem boundary. */
  static NoReplaceLinkCreator observingAttempt(
      NoReplaceLinkCreator linkCreator, FinalMemberPublicationAttempt attempt) {
    return (finalPath, stagedPath) -> {
      Objects.requireNonNull(attempt, "attempt").markAttempted();
      Objects.requireNonNull(linkCreator, "linkCreator").create(finalPath, stagedPath);
    };
  }

  /** Wraps one selected-target move so the attempt fact is set at its filesystem boundary. */
  static AtomicBookMover observingAttempt(
      AtomicBookMover mover, FinalMemberPublicationAttempt attempt) {
    return (stagedPath, finalPath) -> {
      Objects.requireNonNull(attempt, "attempt").markAttempted();
      Objects.requireNonNull(mover, "mover").move(stagedPath, finalPath);
    };
  }

  /** Wraps a no-replace primitive so its full guard runs immediately before the syscall. */
  static NoReplaceLinkCreator guardedLinkCreator(
      FinalMember member,
      FinalMemberPublicationGuard publicationGuard,
      FinalMemberPublicationAttempt attempt,
      NoReplaceLinkCreator linkCreator) {
    return (finalPath, stagedPath) -> {
      requireGuard(member, publicationGuard);
      Objects.requireNonNull(attempt, "attempt").markAttempted();
      Objects.requireNonNull(linkCreator, "linkCreator").create(finalPath, stagedPath);
    };
  }

  /**
   * Runs one final-member guard and preserves the fact that no primitive was attempted on refusal.
   */
  static void requireGuard(FinalMember member, FinalMemberPublicationGuard guard)
      throws FinalMemberPublicationGuardRejectedException {
    try {
      Objects.requireNonNull(guard, "guard").requireCurrent();
    } catch (IOException failure) {
      throw new FinalMemberPublicationGuardRejectedException(member, failure);
    }
  }
}
