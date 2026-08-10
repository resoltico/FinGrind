package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Commits every fully staged journal member and records only verified final artifact evidence. */
final class PublicationTransactionCommitter {
  private PublicationTransactionCommitter() {}

  static PublicationTransactionJournal commitAll(
      PublicationTransactionJournal journal, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    for (int index = 0; index < current.members().size(); index++) {
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
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.requireCurrentStageEvidence(member);
      PublicationTransactionArtifactFiles.replaceFinalWithStage(
          member.finalPath(), member.stagePath());
    } else {
      reconcileExistingFinal(member);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.FINAL_DIRECTORY_FORCED);
  }

  private static void reconcileExistingFinalAfterCollision(
      PublicationTransactionMember member, FileAlreadyExistsException existingFinal)
      throws IOException {
    try {
      reconcileExistingFinal(member);
    } catch (IOException mismatch) {
      mismatch.addSuppressed(existingFinal);
      throw mismatch;
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
