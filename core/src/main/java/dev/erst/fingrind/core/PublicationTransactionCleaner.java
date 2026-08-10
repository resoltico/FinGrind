package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Removes only authenticated transaction-owned stage residue after all final members committed. */
final class PublicationTransactionCleaner {
  private PublicationTransactionCleaner() {}

  static PublicationTransactionJournal cleanAll(
      PublicationTransactionJournal journal, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    for (int index = 0; index < current.members().size(); index++) {
      PublicationTransactionMember member = current.members().get(index);
      if (member.progress() == PublicationTransactionMemberProgress.CLEANED) {
        continue;
      }
      if (member.progress() != PublicationTransactionMemberProgress.COMMITTED) {
        throw new IOException(
            "Publication transaction cannot clean a member that is not committed.");
      }
      PublicationTransactionPlan.requireCurrentPrivateDirectories(current);
      cleanMember(member, runtime);
      current =
          runtime.updateMembers(
              current,
              PublicationTransactionMemberUpdates.cleaned(current, index),
              PublicationTransactionFaultPoint.MEMBER_CLEANED);
    }
    return current;
  }

  private static void cleanMember(
      PublicationTransactionMember member, PublicationTransactionRuntime runtime)
      throws IOException {
    Path parent = Objects.requireNonNull(member.stagePath().getParent(), "stage artifact parent");
    if (member.publicationMode() == PublicationMode.NO_REPLACE_LINK) {
      cleanNoReplaceLink(member, parent, runtime);
    } else {
      cleanReplacement(member, parent, runtime);
    }
  }

  private static void cleanNoReplaceLink(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      PublicationTransactionArtifactFiles.deleteStageAfterFreshValidation(member);
      runtime.faultInjector().after(PublicationTransactionFaultPoint.STAGE_UNLINKED);
    } else {
      PublicationTransactionArtifactFiles.requireCurrentFinalEvidence(member);
    }
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED);
  }

  private static void cleanReplacement(
      PublicationTransactionMember member, Path parent, PublicationTransactionRuntime runtime)
      throws IOException {
    if (PublicationTransactionArtifactFiles.evidenceIfPresent(member.stagePath()).isPresent()) {
      throw new IOException(
          "A replacement publication transaction still has a materialized stage.");
    }
    PublicationTransactionArtifactFiles.requireCurrentFinalEvidence(member);
    runtime.forceDirectory(parent, PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED);
  }
}
