package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Commits every fully staged journal member and records only verified final artifact evidence. */
final class PublicationTransactionCommitter {
  private PublicationTransactionCommitter() {}

  static PublicationTransactionJournal commitAll(
      PublicationTransactionJournal journal, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    for (int index : commitOrder(current.members())) {
      PublicationTransactionMember member = current.members().get(index);
      if (member.progress() == PublicationTransactionMemberProgress.COMMITTED
          || member.progress() == PublicationTransactionMemberProgress.CLEANED) {
        continue;
      }
      if (member.progress() != PublicationTransactionMemberProgress.STAGED) {
        throw new IOException(
            "Publication transaction cannot commit a member that lacks staged evidence.");
      }
      PublicationTransactionPlan.requireCurrentPrivateDirectories(current);
      commitMember(member, runtime);
      PublicationTransactionFinalizedArtifact finalized =
          PublicationTransactionArtifactFiles.finalEvidence(member.finalPath());
      requireSameArtifact(member.stagedArtifact().orElseThrow(), finalized);
      current =
          runtime.updateMembers(
              current,
              PublicationTransactionMemberUpdates.committed(current, index, finalized),
              PublicationTransactionFaultPoint.MEMBER_COMMITTED);
    }
    return current;
  }

  /**
   * Publishes every no-replace member before any replacement member.
   *
   * <p>A transaction still cannot provide filesystem-wide atomicity across multiple final paths.
   * This order therefore does not weaken the recovery model or claim pair atomicity. It does make a
   * late no-replace collision fail before the transaction can replace an already-existing member in
   * the same pair. In particular, a restored-book key collision must preserve the selected live
   * book rather than turning an ordinary destination race into a partial replacement.
   */
  private static List<Integer> commitOrder(List<PublicationTransactionMember> members) {
    List<PublicationTransactionMember> checkedMembers =
        List.copyOf(Objects.requireNonNull(members, "members"));
    return IntStream.range(0, checkedMembers.size())
        .boxed()
        .sorted(
            Comparator.comparingInt(
                index ->
                    checkedMembers.get(index).publicationMode() == PublicationMode.NO_REPLACE_LINK
                        ? 0
                        : 1))
        .toList();
  }

  /** Verifies every replacement precondition before a transaction may enter its commit phase. */
  static void requirePreCommitSafety(PublicationTransactionJournal journal) throws IOException {
    for (PublicationTransactionMember member :
        Objects.requireNonNull(journal, "journal").members()) {
      if (member.progress() != PublicationTransactionMemberProgress.STAGED) {
        throw new IOException(
            "Publication transaction cannot enter commit without every member's staged evidence.");
      }
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
      if (member.publicationMode() == PublicationMode.REPLACE) {
        requireCurrentReplacementPrecondition(member);
      }
    }
  }

  private static void commitMember(
      PublicationTransactionMember member, PublicationTransactionRuntime runtime)
      throws IOException {
    Path parent = Objects.requireNonNull(member.finalPath().getParent(), "final artifact parent");
    if (member.publicationMode() == PublicationMode.NO_REPLACE_LINK) {
      commitNoReplaceLink(member, parent, runtime);
    } else {
      commitReplacement(member, parent, runtime);
    }
  }

  private static void commitNoReplaceLink(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    try {
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
      PublicationTransactionArtifactFiles.createNoReplaceHardLink(
          member.finalPath(), member.stagePath());
    } catch (FileAlreadyExistsException existingFinal) {
      reconcileExistingFinalAfterCollision(member, existingFinal);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.FINAL_DIRECTORY_FORCED);
  }

  private static void commitReplacement(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    if (member.replacementTarget().isEmpty()) {
      commitNoReplaceLink(member, parent, runtime);
      return;
    }
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      requireCurrentReplacementPrecondition(member);
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
      PublicationTransactionArtifactFiles.replaceFinalWithStage(
          member.finalPath(), member.stagePath());
    } else {
      reconcileExistingFinal(member);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.FINAL_DIRECTORY_FORCED);
  }

  private static void requireCurrentReplacementPrecondition(PublicationTransactionMember member)
      throws IOException {
    if (member.replacementTarget().isEmpty()) {
      if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.finalPath()).isPresent()) {
        throw new PublicationTransactionFinalTargetOccupiedException(
            member.finalPath(),
            new IOException(
                "Publication transaction replacement target became occupied after planning."));
      }
      return;
    }
    PublicationTransactionFinalizedArtifact expected = member.replacementTarget().orElseThrow();
    PublicationTransactionFinalizedArtifact current =
        PublicationTransactionArtifactFiles.finalEvidence(member.finalPath());
    if (!expected.equals(current)) {
      throw new IOException(
          "Publication transaction replacement target changed after its authenticated plan was created.");
    }
  }

  private static void reconcileExistingFinalAfterCollision(
      PublicationTransactionMember member, FileAlreadyExistsException existingFinal)
      throws IOException {
    try {
      reconcileExistingFinal(member);
    } catch (IOException mismatch) {
      PublicationTransactionFinalTargetOccupiedException occupied =
          new PublicationTransactionFinalTargetOccupiedException(member.finalPath(), mismatch);
      occupied.addSuppressed(existingFinal);
      throw occupied;
    }
  }

  private static void reconcileExistingFinal(PublicationTransactionMember member)
      throws IOException {
    PublicationTransactionArtifactFiles.requireCurrentFinalEvidence(
        new PublicationTransactionMember(
            member.memberId(),
            member.role(),
            member.finalPath(),
            member.stagePath(),
            member.physicalDirectoryIdentity(),
            member.publicationMode(),
            member.replacementTarget(),
            PublicationTransactionMemberProgress.COMMITTED,
            member.stagedArtifact(),
            java.util.Optional.of(
                PublicationTransactionArtifactFiles.finalEvidence(member.finalPath()))));
  }

  static void requireSameArtifact(
      PublicationTransactionStagedArtifact staged,
      PublicationTransactionFinalizedArtifact finalized)
      throws IOException {
    if (!staged.physicalIdentity().equals(finalized.physicalIdentity())
        || !staged.sha256Hex().equals(finalized.sha256Hex())) {
      throw new IOException(
          "Publication transaction final does not match its authenticated stage.");
    }
  }
}
